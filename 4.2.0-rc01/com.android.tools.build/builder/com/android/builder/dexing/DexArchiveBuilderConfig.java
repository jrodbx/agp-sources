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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import java.io.PrintStream;

/**
 * Configuration object used to setup dx when creating a dex archive. It contains information about
 * the number of parallel .class to .dex file conversions. Also, it specifies if the generated dex
 * should be optimized.
 *
 * <p>Currently, there is a limited set of options to configure, and if necessary, other can be
 * added in the future.
 */
public class DexArchiveBuilderConfig {

    @NonNull private final DxContext dxContext;
    @NonNull private final DexerTool tool;

    @NonNull private final DexOptions dexOptions;
    @NonNull private final CfOptions cfOptions;

    private final int inBufferSize;
    private final int outBufferSize;

    /**
     * Creates a configuration object used to set up the dex archive conversion.
     *
     * @param dxContext used when invoking dx, mainly for getting the standard and error output
     * @param optimized if generated dex should be optimized
     * @param inBufferSize size of the buffer to read .class files, 0 to not reuse buffers.
     * @param minSdkVersion minimum sdk version used to enable dx features
     * @param tool tool that will be used to create dex archives
     * @param outBufferSize size of the buffer to store .dex files from translation.
     * @param jumboMode if jumbo mode is enabled for dx
     */
    public DexArchiveBuilderConfig(
            @NonNull DxContext dxContext,
            boolean optimized,
            int inBufferSize,
            int minSdkVersion,
            @NonNull DexerTool tool,
            int outBufferSize,
            boolean jumboMode) {
        this.dxContext = dxContext;
        this.inBufferSize = inBufferSize;
        this.tool = tool;
        this.outBufferSize = outBufferSize;

        this.dexOptions = new DexOptions();
        this.dexOptions.forceJumbo = jumboMode;
        this.dexOptions.minSdkVersion = minSdkVersion;

        this.cfOptions = new CfOptions();
        this.cfOptions.optimize = optimized;
        // default value used by dx
        this.cfOptions.localInfo = true;
    }

    @NonNull
    public DexOptions getDexOptions() {
        return dexOptions;
    }

    @NonNull
    public CfOptions getCfOptions() {
        return cfOptions;
    }

    @NonNull
    public DxContext getDxContext() {
        return dxContext;
    }

    @NonNull
    public DexerTool getTool() {
        return tool;
    }

    public int getMinSdkVersion() {
        return dexOptions.minSdkVersion;
    }

    @NonNull
    public PrintStream getErrorOut() {
        return dxContext.err;
    }

    public int getInBufferSize() {
        return inBufferSize;
    }

    public int getOutBufferSize() {
        return outBufferSize;
    }
}
