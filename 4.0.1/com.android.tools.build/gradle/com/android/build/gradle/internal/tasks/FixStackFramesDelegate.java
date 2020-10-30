/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry;
import com.android.builder.utils.ExceptionRunnable;
import com.android.builder.utils.FileCache;
import com.android.builder.utils.ZipEntryUtils;
import com.android.ide.common.resources.FileStatus;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * When running Desugar, we need to make sure stack frames information is valid in the class files.
 * This is due to fact that Desugar may load classes in the JVM, and if stack frame information is
 * invalid for bytecode 1.7 and above, {@link VerifyError} is thrown. Also, if stack frames are
 * broken, ASM might be unable to read those classes.
 *
 * <p>This delegate will load all class files from all external jars, and will use ASM to
 * recalculate the stack frames information. In order to obtain new stack frames, types need to be
 * resolved.
 *
 * <p>The parent task requires external libraries as inputs, and all other scope types are
 * referenced. Reason is that loading a class from an external jar, might depend on loading a class
 * that could be located in any of the referenced scopes. In case we are unable to resolve types,
 * content of the original class file will be copied to the the output as we do not know upfront if
 * Desugar will actually load that type.
 */
public class FixStackFramesDelegate {

    /** ASM class writer that uses specified class loader to resolve types. */
    private static class FixFramesVisitor extends ClassWriter {

        @NonNull private final URLClassLoader classLoader;

        public FixFramesVisitor(int flags, @NonNull URLClassLoader classLoader) {
            super(flags);
            this.classLoader = classLoader;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            Class<?> c;
            Class<?> d;
            ClassLoader classLoader = this.classLoader;
            try {
                c = Class.forName(type1.replace('/', '.'), false, classLoader);
                d = Class.forName(type2.replace('/', '.'), false, classLoader);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format(
                                "Unable to find common supper type for %s and %s.", type1, type2),
                        e);
            }
            if (c.isAssignableFrom(d)) {
                return type1;
            }
            if (d.isAssignableFrom(c)) {
                return type2;
            }
            if (c.isInterface() || d.isInterface()) {
                return "java/lang/Object";
            } else {
                do {
                    c = c.getSuperclass();
                } while (!c.isAssignableFrom(d));
                return c.getName().replace('.', '/');
            }
        }
    }

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(FixStackFramesDelegate.class);
    private static final FileTime ZERO = FileTime.fromMillis(0);
    /**
     * Please update this whenever you wish to invalidate all previous cache entries. E.g. if there
     * is a bug in processing, increasing the cache version will invalidate all invalid cache
     * entries, and fresh ones will be generated.
     */
    private static final long CACHE_VERSION = 1;

    // Shared state used in the worker actions.
    private static final WorkerActionServiceRegistry sharedState =
            new WorkerActionServiceRegistry();

    @NonNull private final Set<File> bootClasspath;
    @NonNull private final Set<File> classesToFix;
    @NonNull private final Set<File> referencedClasses;
    @NonNull private final File outFolder;
    @Nullable private final FileCache userCache;

    public FixStackFramesDelegate(
            @NonNull Set<File> bootClasspath,
            @NonNull Set<File> classesToFix,
            @NonNull Set<File> referencedClasses,
            @NonNull File outFolder,
            @Nullable FileCache userCache) {
        this.bootClasspath = bootClasspath;
        this.classesToFix = classesToFix;
        this.referencedClasses = referencedClasses;
        this.outFolder = outFolder;
        this.userCache = userCache;
    }

    @NonNull
    private URLClassLoader createClassLoader() throws MalformedURLException {
        ImmutableList.Builder<URL> urls = new ImmutableList.Builder<>();
        for (File file : bootClasspath) {
            if (file.exists()) {
                urls.add(file.toURI().toURL());
            }
        }
        for (File file : Iterables.concat(classesToFix, referencedClasses)) {
            if (file.isDirectory() || file.isFile()) {
                urls.add(file.toURI().toURL());
            }
        }

        ImmutableList<URL> allUrls = urls.build();
        URL[] classLoaderUrls = allUrls.toArray(new URL[0]);
        return new URLClassLoader(classLoaderUrls);
    }

    private String getUniqueName(@NonNull File input) {
        return Hashing.sha256().hashBytes(
                        input.getAbsolutePath().getBytes(StandardCharsets.UTF_8)).toString()
                + ".jar";
    }

    private void processFiles(
            @NonNull WorkerExecutorFacade workers, @NonNull Map<File, FileStatus> changedInput)
            throws IOException {

        try (Closer closer = Closer.create()) {
            closer.register(workers);
            URLClassLoader classLoader = createClassLoader();
            closer.register(classLoader);

            ClassLoaderKey classLoaderKey = new ClassLoaderKey("classLoader" + hashCode());
            closer.register(sharedState.registerServiceAsCloseable(classLoaderKey, classLoader));

            CacheKey cacheKey;
            if (userCache != null) {
                cacheKey = new CacheKey("userCache" + hashCode());
                closer.register(sharedState.registerServiceAsCloseable(cacheKey, userCache));
            } else {
                cacheKey = null;
            }
            for (Map.Entry<File, FileStatus> entry : changedInput.entrySet()) {
                File out = new File(outFolder, getUniqueName(entry.getKey()));

                Files.deleteIfExists(out.toPath());

                if (entry.getValue() == FileStatus.NEW || entry.getValue() == FileStatus.CHANGED) {
                    workers.submit(
                            FixStackFramesRunnable.class,
                            new Params(entry.getKey(), out, classLoaderKey, cacheKey));
                }
            }
            // We keep waiting for all the workers to finnish so that all the work is done before
            // we remove services in Manager.close()
            workers.await();
        }
    }

    public void doFullRun(@NonNull WorkerExecutorFacade workers) throws IOException {
        FileUtils.cleanOutputDir(outFolder);

        Map<File, FileStatus> inputToProcess =
                classesToFix.stream().collect(Collectors.toMap(f -> f, f -> FileStatus.NEW));

        processFiles(workers, inputToProcess);
    }

    public void doIncrementalRun(
            @NonNull WorkerExecutorFacade workers, @NonNull Map<File, FileStatus> changedInput)
            throws IOException {
        // We should only process (unzip and fix stack) existing jar input from classesToFix
        // If changedInput contains a folder or deleted jar we will still try to delete
        // corresponding output entry (if exists) but will do no processing
        Set<File> jarsToProcess =
                classesToFix.stream().filter(File::isFile).collect(Collectors.toSet());

        Map<File, FileStatus> inputToProcess =
                changedInput
                        .entrySet()
                        .stream()
                        .filter(
                                e ->
                                        e.getValue() == FileStatus.REMOVED
                                                || jarsToProcess.contains(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        processFiles(workers, inputToProcess);
    }

    private static class BaseKey implements Serializable {
        private final String name;

        public BaseKey(@NonNull String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof BaseKey) {
                return this.name.equals(((BaseKey) other).name);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    static class ClassLoaderKey extends BaseKey
            implements WorkerActionServiceRegistry.ServiceKey<URLClassLoader> {
        public ClassLoaderKey(@NonNull String name) {
            super(name);
        }

        @NonNull
        @Override
        public Class<URLClassLoader> getType() {
            return URLClassLoader.class;
        }
    }

    static class CacheKey extends BaseKey
            implements WorkerActionServiceRegistry.ServiceKey<FileCache> {
        public CacheKey(@NonNull String name) {
            super(name);
        }

        @NonNull
        @Override
        public Class<FileCache> getType() {
            return FileCache.class;
        }
    }

    // TODO: convert to Kotlin data class
    private static class Params implements Serializable {
        @NonNull private final File input;
        @NonNull private final File output;
        @NonNull private final ClassLoaderKey classLoaderKey;
        @Nullable private final CacheKey cacheKey;

        private Params(
                @NonNull File input,
                @NonNull File output,
                @NonNull ClassLoaderKey classLoaderKey,
                @Nullable CacheKey cacheKey) {
            this.input = input;
            this.output = output;
            this.classLoaderKey = classLoaderKey;
            this.cacheKey = cacheKey;
        }
    }

    private static class FixStackFramesRunnable implements Runnable {
        @NonNull private final Params params;

        @Inject
        public FixStackFramesRunnable(@NonNull Params params) {
            this.params = params;
        }

        @Override
        public void run() {
            try {
                URLClassLoader classLoader =
                        FixStackFramesDelegate.sharedState
                                .getService(params.classLoaderKey)
                                .getService();
                FileCache userCache =
                        params.cacheKey != null
                                ? FixStackFramesDelegate.sharedState
                                        .getService(params.cacheKey)
                                        .getService()
                                : null;

                ExceptionRunnable fileCreator =
                        createFile(params.input, params.output, classLoader);
                if (userCache != null) {
                    FileCache.Inputs key =
                            new FileCache.Inputs.Builder(FileCache.Command.FIX_STACK_FRAMES)
                                    .putFile(
                                            "file",
                                            params.input,
                                            FileCache.FileProperties.PATH_SIZE_TIMESTAMP)
                                    .putLong("version", CACHE_VERSION)
                                    .build();
                    userCache.createFile(params.output, key, fileCreator);
                } else {
                    fileCreator.run();
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @NonNull
        private static ExceptionRunnable createFile(
                @NonNull File input, @NonNull File output, @NonNull URLClassLoader classLoader) {
            return () -> {
                try (ZipFile inputZip = new ZipFile(input);
                     ZipOutputStream outputZip =
                             new ZipOutputStream(
                                     new BufferedOutputStream(
                                             Files.newOutputStream(output.toPath())))) {
                    Enumeration<? extends ZipEntry> inEntries = inputZip.entries();
                    while (inEntries.hasMoreElements()) {
                        ZipEntry entry = inEntries.nextElement();
                        if (!ZipEntryUtils.isValidZipEntryName(entry)) {
                            throw new InvalidPathException(
                                    entry.getName(), "Entry name contains invalid characters");
                        }
                        if (!entry.getName().endsWith(SdkConstants.DOT_CLASS)) {
                            continue;
                        }
                        InputStream originalFile =
                                new BufferedInputStream(inputZip.getInputStream(entry));
                        ZipEntry outEntry = new ZipEntry(entry.getName());

                        byte[] newEntryContent = getFixedClass(originalFile, classLoader);

                        CRC32 crc32 = new CRC32();
                        crc32.update(newEntryContent);
                        outEntry.setCrc(crc32.getValue());
                        outEntry.setMethod(ZipEntry.STORED);
                        outEntry.setSize(newEntryContent.length);
                        outEntry.setCompressedSize(newEntryContent.length);
                        outEntry.setLastAccessTime(ZERO);
                        outEntry.setLastModifiedTime(ZERO);
                        outEntry.setCreationTime(ZERO);

                        outputZip.putNextEntry(outEntry);
                        outputZip.write(newEntryContent);
                        outputZip.closeEntry();
                    }
                }
            };
        }

        @NonNull
        private static byte[] getFixedClass(
                @NonNull InputStream originalFile, @NonNull URLClassLoader classLoader)
                throws IOException {
            byte[] bytes = ByteStreams.toByteArray(originalFile);
            try {
                ClassReader classReader = new ClassReader(bytes);
                ClassWriter classWriter =
                        new FixFramesVisitor(ClassWriter.COMPUTE_FRAMES, classLoader);
                classReader.accept(classWriter, ClassReader.SKIP_FRAMES);
                return classWriter.toByteArray();
            } catch (Throwable t) {
                // we could not fix it, just copy the original and log the exception
                logger.verbose(t.getMessage());
                return bytes;
            }
        }
    }
}
