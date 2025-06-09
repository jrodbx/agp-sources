/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.builder.dexing

import com.android.tools.r8.StringConsumer
import com.android.tools.r8.tracereferences.TraceReferences
import com.android.tools.r8.tracereferences.TraceReferencesCommand
import com.android.tools.r8.tracereferences.TraceReferencesKeepRules
import java.nio.file.Path

/**
 * Compute keep rules for shrinking desugar library jars.
 */
fun runTraceReferenceTool(
    fullBootClasspath: List<Path>,
    desugaredDesugarLib: Collection<Path>,
    dexFiles: Collection<Path>,
    keepRuleOutput: Path
) {
    val consumer = TraceReferencesKeepRules.builder()
        .setAllowObfuscation(false)
        .setOutputConsumer(StringConsumer.FileConsumer(keepRuleOutput))
        .build()

    val command = TraceReferencesCommand.builder()
        .setConsumer(consumer)
        .addLibraryFiles(fullBootClasspath)
        .addTargetFiles(desugaredDesugarLib)
        .addSourceFiles(dexFiles)
        .build()

    TraceReferences.run(command)
}
