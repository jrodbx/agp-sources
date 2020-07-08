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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessEnvBuilder;
import com.android.ide.common.process.ProcessInfoBuilder;
import java.nio.file.Path;

/** A builder to create an information necessary to run Desugar in a separate JVM process. */
public final class DesugarProcessBuilder extends ProcessEnvBuilder<DesugarProcessBuilder> {
    private static final String DESUGAR_MAIN = "com.google.devtools.build.android.desugar.Desugar";

    @NonNull private final DesugarProcessArgs args;
    @NonNull private final Path java8LangSupportJar;

    public DesugarProcessBuilder(
            @NonNull DesugarProcessArgs args, @NonNull Path java8LangSupportJar) {
        this.args = args;
        this.java8LangSupportJar = java8LangSupportJar;
    }

    @NonNull
    public JavaProcessInfo build(boolean isWindows) {

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.addEnvironments(mEnvironment);

        builder.setClasspath(java8LangSupportJar.toString());
        builder.setMain(DESUGAR_MAIN);
        builder.addJvmArg("-Xmx64M");

        builder.addArgs(args.getArgs(isWindows));

        return builder.createJavaProcess();
    }
}
