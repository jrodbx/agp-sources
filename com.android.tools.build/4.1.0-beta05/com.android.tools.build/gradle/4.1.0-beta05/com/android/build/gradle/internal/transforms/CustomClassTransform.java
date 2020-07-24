/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms;

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES;
import static com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.utils.ZipEntryUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A transform that provides a simple API for transforming all the user classes. It only requires a
 * function to transform from an {@link InputStream} to an {@link OutputStream} which will be run
 * for every class. This transform takes care of all the incremental and jar vs class details.
 *
 * <p>This transform is created with a path to the .jar that provides its actual implementation. The
 * jar must follow the following convention:
 *
 * <p>1) Expose a service of type BiConsumer<InputStream, OutputStream>. This function will be used
 * invoked to transform a .class file. 2) To output additional classes as a result of the output,
 * they will be looked for as resources in the given jar. All jars found in the "dependencies"
 * resource directory will be output.
 */
public class CustomClassTransform extends Transform {

    @NonNull private final String name;

    @NonNull private final String path;
    private final boolean addDependencies;

    public static final Set<QualifiedContent.Scope> SCOPE_EXTERNAL =
            Sets.immutableEnumSet(QualifiedContent.Scope.EXTERNAL_LIBRARIES);

    /**
     * Creates the transform.
     *
     * @param path the path to the transform code jar.
     * @param addDependencies whether to add the dependencies of the transform to the streams for
     *     inclusion in the APK.
     */
    public CustomClassTransform(@NonNull String path, boolean addDependencies) {
        this.name = Files.getNameWithoutExtension(path);
        this.path = path;
        this.addDependencies = addDependencies;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return addDependencies ? ImmutableSet.of(CLASSES, NATIVE_LIBS) : ImmutableSet.of(CLASSES);
    }

    @NonNull
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return ImmutableSet.of(SecondaryFile.nonIncremental(new File(path)));
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws InterruptedException, IOException {
        final TransformOutputProvider outputProvider = invocation.getOutputProvider();
        assert outputProvider != null;

        // Output the resources, we only do this if this is not incremental,
        // as the secondary file is will trigger a full build if modified.
        if (!invocation.isIncremental()) {
            outputProvider.deleteAll();

            if (addDependencies) {
                // To avoid https://bugs.openjdk.java.net/browse/JDK-7183373
                // we extract the resources directly as a zip file.
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(path))) {
                    ZipEntry entry;
                    Pattern pattern = Pattern.compile("dependencies/(.*)\\.jar");
                    while ((entry = zis.getNextEntry()) != null
                            && ZipEntryUtils.isValidZipEntryName(entry)) {
                        Matcher matcher = pattern.matcher(entry.getName());
                        if (matcher.matches()) {
                            String name = matcher.group(1);
                            File outputJar =
                                    outputProvider.getContentLocation(
                                            name, getOutputTypes(), SCOPE_EXTERNAL, Format.JAR);
                            Files.createParentDirs(outputJar);
                            try (FileOutputStream fos = new FileOutputStream(outputJar)) {
                                ByteStreams.copy(zis, fos);
                            }
                        }
                        zis.closeEntry();
                    }
                }
            }
        }

        URL url = new File(path).toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {url})) {
            BiConsumer<InputStream, OutputStream> function = loadTransformFunction(loader);

            for (TransformInput ti : invocation.getInputs()) {
                for (JarInput jarInput : ti.getJarInputs()) {
                    File inputJar = jarInput.getFile();
                    File outputJar =
                            outputProvider.getContentLocation(
                                    jarInput.getName(),
                                    jarInput.getContentTypes(),
                                    jarInput.getScopes(),
                                    Format.JAR);

                    if (invocation.isIncremental()) {
                        switch (jarInput.getStatus()) {
                            case NOTCHANGED:
                                break;
                            case ADDED:
                            case CHANGED:
                                transformJar(function, inputJar, outputJar);
                                break;
                            case REMOVED:
                                FileUtils.delete(outputJar);
                                break;
                        }
                    } else {
                        transformJar(function, inputJar, outputJar);
                    }
                }
                for (DirectoryInput di : ti.getDirectoryInputs()) {
                    File inputDir = di.getFile();
                    File outputDir =
                            outputProvider.getContentLocation(
                                    di.getName(),
                                    di.getContentTypes(),
                                    di.getScopes(),
                                    Format.DIRECTORY);
                    if (invocation.isIncremental()) {
                        for (Map.Entry<File, Status> entry : di.getChangedFiles().entrySet()) {
                            File inputFile = entry.getKey();
                            switch (entry.getValue()) {
                                case NOTCHANGED:
                                    break;
                                case ADDED:
                                case CHANGED:
                                    if (!inputFile.isDirectory()
                                            && inputFile.getName()
                                            .endsWith(SdkConstants.DOT_CLASS)) {
                                        File out = toOutputFile(outputDir, inputDir, inputFile);
                                        transformFile(function, inputFile, out);
                                    }
                                    break;
                                case REMOVED:
                                    File outputFile = toOutputFile(outputDir, inputDir, inputFile);
                                    FileUtils.deleteIfExists(outputFile);
                                    break;
                            }
                        }
                    } else {
                        for (File in : FileUtils.getAllFiles(inputDir)) {
                            if (in.getName().endsWith(SdkConstants.DOT_CLASS)) {
                                File out = toOutputFile(outputDir, inputDir, in);
                                transformFile(function, in, out);
                            }
                        }
                    }
                }
            }
        }
    }

    private BiConsumer<InputStream, OutputStream> loadTransformFunction(URLClassLoader loader) {
        ServiceLoader<BiConsumer> serviceLoader = ServiceLoader.load(BiConsumer.class, loader);
        ArrayList<BiConsumer> functions = Lists.newArrayList(serviceLoader.iterator());

        if (functions.isEmpty()) {
            throw new IllegalStateException(
                    "Custom transform does not provide a BiConsumer to apply");
        } else if (functions.size() > 1) {
            throw new IllegalStateException(
                    "Custom transform provides more than one BiConsumer to apply");
        }
        BiConsumer uncheckedFunction = functions.get(0);
        // Validate the generic arguments are valid:
        Type[] types = uncheckedFunction.getClass().getGenericInterfaces();
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                ParameterizedType generic = (ParameterizedType) type;
                Type[] args = generic.getActualTypeArguments();
                if (generic.getRawType().equals(BiConsumer.class)
                        && args.length == 2
                        && args[0].equals(InputStream.class)
                        && args[1].equals(OutputStream.class)) {
                    return (BiConsumer<InputStream, OutputStream>) uncheckedFunction;
                }
            }
        }
        throw new IllegalStateException(
                "Custom transform must provide a BiConsumer<InputStream, OutputStream>");
    }

    private void transformJar(
            BiConsumer<InputStream, OutputStream> function, File inputJar, File outputJar)
            throws IOException {
        Files.createParentDirs(outputJar);
        try (FileInputStream fis = new FileInputStream(inputJar);
                ZipInputStream zis = new ZipInputStream(fis);
                FileOutputStream fos = new FileOutputStream(outputJar);
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null && ZipEntryUtils.isValidZipEntryName(entry)) {
                if (!entry.isDirectory() && entry.getName().endsWith(SdkConstants.DOT_CLASS)) {
                    ZipEntry nextEntry = new ZipEntry(entry.getName());
                    // Any negative time value sets ZipEntry's xdostime to DOSTIME_BEFORE_1980
                    // constant.
                    nextEntry.setTime(-1L);
                    zos.putNextEntry(nextEntry);
                    apply(function, zis, zos);
                } else {
                    // Do not copy resources
                }
                entry = zis.getNextEntry();
            }
        }
    }

    private void transformFile(
            BiConsumer<InputStream, OutputStream> function, File inputFile, File outputFile)
            throws IOException {
        Files.createParentDirs(outputFile);
        try (FileInputStream fis = new FileInputStream(inputFile);
                FileOutputStream fos = new FileOutputStream(outputFile)) {
            apply(function, fis, fos);
        }
    }

    @NonNull
    private static File toOutputFile(File outputDir, File inputDir, File inputFile) {
        return new File(outputDir, FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir));
    }

    private void apply(
            BiConsumer<InputStream, OutputStream> function, InputStream in, OutputStream out)
            throws IOException {
        try {
            function.accept(in, out);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
