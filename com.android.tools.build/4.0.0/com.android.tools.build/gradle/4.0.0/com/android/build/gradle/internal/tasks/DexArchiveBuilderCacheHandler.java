/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.dexing.DexerTool;
import com.android.builder.utils.FileCache;
import com.android.dx.Version;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** helper class used to cache dex archives in the user or build caches. */
class DexArchiveBuilderCacheHandler {

    static final class CacheableItem {
        @NonNull final File input;
        @NonNull final Collection<File> cachable;
        @NonNull final List<Path> dependencies;

        CacheableItem(
                @NonNull File input,
                @NonNull Collection<File> cachable,
                @NonNull List<Path> dependencies) {
            this.input = input;
            this.cachable = cachable;
            this.dependencies = dependencies;
        }
    }

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DexArchiveBuilderCacheHandler.class);

    // Increase this if we might have generated broken cache entries to invalidate them.
    private static final int CACHE_KEY_VERSION = 5;

    @Nullable private final FileCache userLevelCache;
    private final boolean isDxNoOptimizeFlagPresent;
    private final int minSdkVersion;
    private final boolean isDebuggable;
    @NonNull private final DexerTool dexer;
    @Nullable private String coreLibDesugarConfig;
    /**
     * A cache session to share between all cache access. We can do that because each {@link
     * DexArchiveBuilderCacheHandler} is used only by one DexArchiveBuilderTransform and all files
     * we use as cache inputs are left unchanged during the DexArchiveBuilderTransform.
     */
    @NonNull private final FileCache.CacheSession cacheSession = FileCache.newSession();

    DexArchiveBuilderCacheHandler(
            @Nullable FileCache userLevelCache,
            boolean isDxNoOptimizeFlagPresent,
            int minSdkVersion,
            boolean isDebuggable,
            @NonNull DexerTool dexer,
            @Nullable String coreLibDesugarConfig) {
        this.userLevelCache = userLevelCache;
        this.isDxNoOptimizeFlagPresent = isDxNoOptimizeFlagPresent;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
        this.dexer = dexer;
        this.coreLibDesugarConfig = coreLibDesugarConfig;
    }

    @Nullable
    File getCachedDexIfPresent(
            @NonNull File input, @NonNull List<Path> dependencies, boolean shrinkDesugarLibrary)
            throws IOException {
        return getCachedVersionIfPresent(input, dependencies, OutputType.DEX, shrinkDesugarLibrary);
    }

    @Nullable
    File getCachedKeepRuleIfPresent(
            @NonNull File input, @NonNull List<Path> dependencies, boolean shrinkDesugarLibrary)
            throws IOException {
        return getCachedVersionIfPresent(
                input, dependencies, OutputType.KEEP_RULE, shrinkDesugarLibrary);
    }

    @Nullable
    private File getCachedVersionIfPresent(
            @NonNull File input,
            @NonNull List<Path> dependencies,
            @NonNull OutputType outputType,
            boolean shrinkDesugarLibrary)
            throws IOException {
        FileCache cache = getBuildCache(input, userLevelCache);

        if (cache == null) {
            return null;
        }

        FileCache.Inputs buildCacheInputs =
                DexArchiveBuilderCacheHandler.getBuildCacheInputs(
                        input,
                        isDxNoOptimizeFlagPresent,
                        dexer,
                        minSdkVersion,
                        isDebuggable,
                        dependencies,
                        shrinkDesugarLibrary,
                        outputType,
                        coreLibDesugarConfig,
                        cacheSession);
        return cache.cacheEntryExists(buildCacheInputs)
                ? cache.getFileInCache(buildCacheInputs)
                : null;
    }

    void populateDexCache(
            @NonNull Collection<CacheableItem> cacheableItems, boolean shrinkDesugarLibrary)
            throws IOException, ExecutionException {
        populateCache(cacheableItems, OutputType.DEX, shrinkDesugarLibrary);
    }

    void populateKeepRuleCache(
            @NonNull Collection<CacheableItem> cacheableItems, boolean shrinkDesugarLibrary)
            throws IOException, ExecutionException {
        populateCache(cacheableItems, OutputType.KEEP_RULE, shrinkDesugarLibrary);
    }

    private void populateCache(
            @NonNull Collection<CacheableItem> cacheableItems,
            @NonNull OutputType outputType,
            boolean shrinkDesugarLibrary)
            throws IOException, ExecutionException {

        for (CacheableItem cacheableItem : cacheableItems) {
            FileCache cache = getBuildCache(cacheableItem.input, userLevelCache);
            if (cache != null) {
                FileCache.Inputs buildCacheInputs =
                        DexArchiveBuilderCacheHandler.getBuildCacheInputs(
                                cacheableItem.input,
                                isDxNoOptimizeFlagPresent,
                                dexer,
                                minSdkVersion,
                                isDebuggable,
                                cacheableItem.dependencies,
                                shrinkDesugarLibrary,
                                outputType,
                                coreLibDesugarConfig,
                                cacheSession);
                FileCache.QueryResult result;
                Collection<File> cachableOutputs = cacheableItem.cachable;

                switch (outputType) {
                    case DEX:
                        result =
                                cache.createFileInCacheIfAbsent(
                                        buildCacheInputs,
                                        out -> {
                                            logger.verbose(
                                                    "Merging %1$s into %2$s",
                                                    Joiner.on(',').join(cachableOutputs),
                                                    out.getAbsolutePath());
                                            mergeJars(out, cachableOutputs);
                                        });
                        break;
                    case KEEP_RULE:
                        result =
                                cache.createFileInCacheIfAbsent(
                                        buildCacheInputs,
                                        out -> {
                                            FileUtils.cleanOutputDir(out);
                                            logger.verbose(
                                                    "Copying %1$s into %2$s",
                                                    Joiner.on(',').join(cachableOutputs),
                                                    out.getAbsolutePath());
                                            for (File file : cachableOutputs) {
                                                Files.copy(
                                                        file.toPath(),
                                                        out.toPath().resolve(file.getName()),
                                                        StandardCopyOption.REPLACE_EXISTING);
                                            }
                                        });
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown dex output type");
                }

                if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
                    Verify.verifyNotNull(result.getCauseOfCorruption());
                    logger.lifecycle(
                            "The build cache at '%1$s' contained an invalid cache entry.\n"
                                    + "Cause: %2$s\n"
                                    + "We have recreated the cache entry.\n"
                                    + "%3$s",
                            cache.getCacheDirectory().getAbsolutePath(),
                            Throwables.getStackTraceAsString(result.getCauseOfCorruption()),
                            BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE);
                }
            }
        }
    }

    private static void mergeJars(File out, Iterable<File> dexArchives) throws IOException {

        try (JarOutputStream jarOutputStream =
                new JarOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {

            Set<String> usedNames = new HashSet<>();
            for (File dexArchive : dexArchives) {
                if (dexArchive.exists()) {
                    try (JarFile jarFile = new JarFile(dexArchive)) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry jarEntry = entries.nextElement();
                            // Get unique name as jars might have multiple classes.dex, as for D8
                            // we do not want to output single dex per class for performance reasons
                            String entryName = jarEntry.getName();
                            while (!usedNames.add(entryName)) {
                                entryName = "_" + entryName;
                            }
                            jarOutputStream.putNextEntry(new JarEntry(entryName));
                            try (InputStream inputStream =
                                    new BufferedInputStream(jarFile.getInputStream(jarEntry))) {
                                ByteStreams.copy(inputStream, jarOutputStream);
                            }
                            jarOutputStream.closeEntry();
                        }
                    }
                }
            }
        }
    }

    /**
     * Input parameters to be provided by the client when using {@link FileCache}.
     *
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory. This enum class lists the input parameters that are
     * used in {@link DexArchiveBuilderCacheHandler}.
     */
    private enum FileCacheInputParams {

        /** The input file. */
        FILE,

        /** Dx version used to create the dex archive. */
        DX_VERSION,

        /** Whether jumbo mode is enabled. */
        JUMBO_MODE,

        /** Whether optimize is enabled. */
        OPTIMIZE,

        /** Tool used to produce the dex archive. */
        DEXER_TOOL,

        /** Version of the cache key. */
        CACHE_KEY_VERSION,

        /** Min sdk version used to generate dex. */
        MIN_SDK_VERSION,

        /** If generate dex is debuggable. */
        IS_DEBUGGABLE,

        /** Additional dependency files. */
        EXTRA_DEPENDENCIES,

        /** Whether we need to shrink desugar library. */
        SHRINK_DESUGAR_LIBRARY,

        /** Output type of dexing task. */
        DEX_OUTPUT_TYPE,

        /** Config for core library desugaring. */
        CORE_LIB_DESUGAR_CONFIG,
    }

    /**
     * Represents DexArchiveBuilderTask cacheable output types which can be either dex or keep_rule.
     */
    private enum OutputType {
        DEX,

        KEEP_RULE,
    }

    /**
     * Returns a {@link FileCache.Inputs} object computed from the given parameters for the
     * predex-library task to use the build cache.
     */
    @NonNull
    public static FileCache.Inputs getBuildCacheInputs(
            @NonNull File inputFile,
            @NonNull boolean isDxNoOptimizeFlagPresent,
            @NonNull DexerTool dexerTool,
            int minSdkVersion,
            boolean isDebuggable,
            @NonNull List<Path> extraDependencies,
            boolean shrinkDesugarLibrary,
            OutputType outputType,
            @Nullable String coreLibDesugarConfig,
            FileCache.CacheSession cacheSession)
            throws IOException {
        // To use the cache, we need to specify all the inputs that affect the outcome of a pre-dex
        // (see DxDexKey for an exhaustive list of these inputs)
        FileCache.Inputs.Builder buildCacheInputs =
                new FileCache.Inputs.Builder(
                        FileCache.Command.PREDEX_LIBRARY_TO_DEX_ARCHIVE, cacheSession);

        buildCacheInputs
                .putFile(
                        FileCacheInputParams.FILE.name(),
                        inputFile,
                        FileCache.FileProperties.PATH_HASH)
                .putString(FileCacheInputParams.DX_VERSION.name(), Version.VERSION)
                .putBoolean(FileCacheInputParams.JUMBO_MODE.name(), isJumboModeEnabledForDx())
                .putBoolean(FileCacheInputParams.OPTIMIZE.name(), !isDxNoOptimizeFlagPresent)
                .putString(FileCacheInputParams.DEXER_TOOL.name(), dexerTool.name())
                .putLong(FileCacheInputParams.CACHE_KEY_VERSION.name(), CACHE_KEY_VERSION)
                .putLong(FileCacheInputParams.MIN_SDK_VERSION.name(), minSdkVersion)
                .putBoolean(FileCacheInputParams.IS_DEBUGGABLE.name(), isDebuggable)
                .putBoolean(
                        FileCacheInputParams.SHRINK_DESUGAR_LIBRARY.name(), shrinkDesugarLibrary)
                .putString(FileCacheInputParams.DEX_OUTPUT_TYPE.name(), outputType.name());

        if (coreLibDesugarConfig != null) {
            buildCacheInputs.putString(
                    FileCacheInputParams.CORE_LIB_DESUGAR_CONFIG.name(), coreLibDesugarConfig);
        }

        for (int i = 0; i < extraDependencies.size(); i++) {
            Path path = extraDependencies.get(i);
            if (Files.isDirectory(path)) {
                buildCacheInputs.putDirectory(
                        FileCacheInputParams.EXTRA_DEPENDENCIES.name() + "[" + i + "]",
                        path.toFile(),
                        FileCache.DirectoryProperties.PATH_HASH);
            } else if (Files.isRegularFile(path)) {
                buildCacheInputs.putFile(
                        FileCacheInputParams.EXTRA_DEPENDENCIES.name() + "[" + i + "]",
                        path.toFile(),
                        FileCache.FileProperties.PATH_HASH);
            } else if (!Files.exists(path)) {
                throw new NoSuchFileException(path.toString());
            } else {
                throw new IOException("Unsupported file '" + path.toString() + "'");
            }
        }

        return buildCacheInputs.build();
    }

    /** Jumbo mode is always enabled for dex archives - see http://b.android.com/321744 */
    static boolean isJumboModeEnabledForDx() {
        return true;
    }

    /**
     * Returns the build cache if it should be used for the predex-library task, and {@code null}
     * otherwise.
     */
    @Nullable
    static FileCache getBuildCache(@NonNull File inputFile, @Nullable FileCache buildCache) {
        // We use the build cache only when it is enabled.
        if (buildCache == null) {
            return null;
        }
        // After the check above, here the build cache should be enabled. We now check whether it is
        // a snapshot version or not (to address http://b.android.com/228623).
        // Note that the current check is based on the file path; if later on there is a more
        // reliable way to verify whether an input file is a snapshot, we should replace this check
        // with that.
        if (inputFile.getPath().contains("-SNAPSHOT")) {
            return null;
        } else {
            return buildCache;
        }
    }
}
