/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.internal.compiler;

import static com.android.SdkConstants.PLATFORM_DARWIN;
import static com.android.SdkConstants.PLATFORM_LINUX;
import static com.android.SdkConstants.PLATFORM_WINDOWS;
import static com.android.SdkConstants.currentPlatform;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A Source File processor for AIDL files. This compiles each aidl file found by the SourceSearcher.
 */
public class ShaderProcessor {

    public static final String EXT_VERT = "vert";
    public static final String EXT_TESC = "tesc";
    public static final String EXT_TESE = "tese";
    public static final String EXT_GEOM = "geom";
    public static final String EXT_FRAG = "frag";
    public static final String EXT_COMP = "comp";

    public static File getGlslcLocation(@NonNull File ndkLocation) {
        if (ndkLocation == null) {
            throw new IllegalStateException("NDK location is missing. It is required to compile shaders.");
        }

        if (ndkLocation == null || !ndkLocation.isDirectory()) {
            throw new IllegalStateException(
                    "NDK location does not exist. It is required to compile shaders: "
                            + ndkLocation);
        }

        // find the location of the compiler.
        File glslcRootFolder = new File(ndkLocation, SdkConstants.FD_SHADER_TOOLS);

        switch (currentPlatform()) {
            case PLATFORM_DARWIN:
                glslcRootFolder = new File(glslcRootFolder, "darwin-x86_64");
                break;
            case PLATFORM_WINDOWS:
                // try 64 bit first
                glslcRootFolder = new File(glslcRootFolder, "windows-x86_64");
                if (!glslcRootFolder.isDirectory()) {
                    // try 32 bit next
                    glslcRootFolder = new File(glslcRootFolder, "windows");
                }
                break;
            case PLATFORM_LINUX:
                glslcRootFolder = new File(glslcRootFolder, "linux-x86_64");
                break;
        }

        if (!glslcRootFolder.isDirectory()) {
            throw new IllegalStateException("Missing NDK subfolder: " + glslcRootFolder);
        }

        File glslcLocation = new File(glslcRootFolder, SdkConstants.FN_GLSLC);

        if (!glslcLocation.isFile()) {
            throw new IllegalStateException("glslc is missing: " + glslcLocation);
        }
        return glslcLocation;
    }

    public static class ShaderProcessorRunnable implements Runnable {
        private final ShaderProcessorParams params;

        public ShaderProcessorRunnable(ShaderProcessorParams params) {
            this.params = params;
        }

        @NonNull
        private List<String> getArgs(@NonNull String relativePath) {
            int pos = relativePath.indexOf(File.separatorChar);
            if (pos == -1) {
                return params.mDefaultArgs;
            }

            String key = relativePath.substring(0, pos);

            List<String> args = params.mScopedArgs.get(key);
            if (args != null) {
                return args;
            }

            return params.mDefaultArgs;
        }

        @Override
        public void run() {
            ProcessInfoBuilder builder = new ProcessInfoBuilder();
            builder.setExecutable(params.mGlslcLocation);

            // working dir for the includes
            builder.addArgs("-I", params.mSourceFolder.getPath());

            // compute the output file path
            String relativePath =
                    FileUtils.relativePath(params.path.toFile(), params.start.toFile());
            File destFile = new File(params.mOutputDir, relativePath + ".spv");

            // add the args
            builder.addArgs(getArgs(relativePath));

            // the source file
            builder.addArgs(params.path.toString());

            // add the output file
            builder.addArgs("-o", destFile.getPath());

            // make sure the output file's parent folder is created.
            FileUtils.mkdirs(destFile.getParentFile());

            ProcessResult result =
                    params.mProcessExecutor.execute(
                            builder.createProcess(), params.mProcessOutputHandler);

            try {
                result.rethrowFailure().assertNormalExitValue();
            } catch (ProcessException pe) {
                throw new RuntimeException(pe);
            }
        }
    }

    public static class ShaderProcessorParams implements Serializable {
        @NonNull private final File mSourceFolder;
        @NonNull private final File mOutputDir;
        @NonNull private final List<String> mDefaultArgs;
        @NonNull private final Map<String, List<String>> mScopedArgs;
        @NonNull private final ProcessExecutor mProcessExecutor;
        @NonNull private final ProcessOutputHandler mProcessOutputHandler;
        @NonNull private final Path start;
        @NonNull private final Path path;
        @NonNull private final File mGlslcLocation;

        public ShaderProcessorParams(
                @NonNull File mSourceFolder,
                @NonNull File mOutputDir,
                @NonNull List<String> mDefaultArgs,
                @NonNull Map<String, List<String>> mScopedArgs,
                @NonNull ProcessExecutor mProcessExecutor,
                @NonNull ProcessOutputHandler mProcessOutputHandler,
                @NonNull Path start,
                @NonNull Path path,
                @NonNull File mGlslcLocation) {
            this.mSourceFolder = mSourceFolder;
            this.mOutputDir = mOutputDir;
            this.mDefaultArgs = mDefaultArgs;
            this.mScopedArgs = mScopedArgs;
            this.mProcessExecutor = mProcessExecutor;
            this.mProcessOutputHandler = mProcessOutputHandler;
            this.start = start;
            this.path = path;
            this.mGlslcLocation = mGlslcLocation;
        }
    }
}
