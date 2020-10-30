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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dexing.DexArchiveMerger;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.process.ProcessOutput;
import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;

/**
 * Helper class to invoke the {@link com.android.builder.dexing.DexArchiveMerger} used to merge dex
 * archives.
 */
public class DexMergerTransformCallable implements Callable<Void> {

    @NonNull private final MessageReceiver messageReceiver;
    @NonNull private final DexingType dexingType;
    @NonNull private final ProcessOutput processOutput;
    @NonNull private final File dexOutputDir;
    @NonNull private final Iterator<Path> dexArchives;
    @NonNull private final ForkJoinPool forkJoinPool;
    @Nullable private final Path mainDexList;
    @NonNull private final DexMergerTool dexMerger;
    private final int minSdkVersion;
    private final boolean isDebuggable;

    public DexMergerTransformCallable(
            @NonNull MessageReceiver messageReceiver,
            @NonNull DexingType dexingType,
            @NonNull ProcessOutput processOutput,
            @NonNull File dexOutputDir,
            @NonNull Iterator<Path> dexArchives,
            @Nullable Path mainDexList,
            @NonNull ForkJoinPool forkJoinPool,
            @NonNull DexMergerTool dexMerger,
            int minSdkVersion,
            boolean isDebuggable) {
        this.messageReceiver = messageReceiver;
        this.dexingType = dexingType;
        this.processOutput = processOutput;
        this.dexOutputDir = dexOutputDir;
        this.dexArchives = dexArchives;
        this.mainDexList = mainDexList;
        this.forkJoinPool = forkJoinPool;
        this.dexMerger = dexMerger;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
    }

    @Override
    public Void call() throws Exception {
        DexArchiveMerger merger;
        switch (dexMerger) {
            case DX:
                DxContext dxContext =
                        new DxContext(
                                processOutput.getStandardOutput(), processOutput.getErrorOutput());
                merger = DexArchiveMerger.createDxDexMerger(dxContext, forkJoinPool, isDebuggable);
                break;
            case D8:
                int d8MinSdkVersion = minSdkVersion;
                if (d8MinSdkVersion < 21 && dexingType == DexingType.NATIVE_MULTIDEX) {
                    // D8 has baked-in logic that does not allow multiple dex files without
                    // main dex list if min sdk < 21. When we deploy the app to a device with api
                    // level 21+, we will promote legacy multidex to native multidex, but the min
                    // sdk version will be less than 21, which will cause D8 failure as we do not
                    // supply the main dex list. In order to prevent that, it is safe to set min
                    // sdk version to 21.
                    d8MinSdkVersion = 21;
                }
                merger =
                        DexArchiveMerger.createD8DexMerger(
                                messageReceiver, d8MinSdkVersion, isDebuggable, forkJoinPool);
                break;
            default:
                throw new AssertionError("Unknown dex merger " + dexMerger.name());
        }

        merger.mergeDexArchives(dexArchives, dexOutputDir.toPath(), mainDexList, dexingType);
        return null;
    }

    public interface Factory {
        DexMergerTransformCallable create(
                @NonNull MessageReceiver messageReceiver,
                @NonNull DexingType dexingType,
                @NonNull ProcessOutput processOutput,
                @NonNull File dexOutputDir,
                @NonNull Iterator<Path> dexArchives,
                @Nullable Path mainDexList,
                @NonNull ForkJoinPool forkJoinPool,
                @NonNull DexMergerTool dexMerger,
                int minSdkVersion,
                boolean isDebuggable);
    }
}
