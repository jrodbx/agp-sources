/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.r8.BackportedMethodList
import com.android.tools.r8.BackportedMethodListCommand
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.StringConsumer
import java.io.File

/**
 * Generates backported desugared methods handled by D8, which will be consumed by Lint.
 *
 * Note: minSdkVersion should be the value specified from DSL which is the also one we pass to lint,
 * rather than [DexingCreationConfig.minSdkVersionForDexing] which is affected by the device.
 */
object D8DesugaredMethodsGenerator {
    fun generate(
        coreLibDesugarConfig: String?,
        bootclasspath: Set<File>,
        minSdkVersion: Int,
    ): List<String> {
        val consumer = CustomStringConsumer()
        val commandBuilder = BackportedMethodListCommand.builder()

        if (coreLibDesugarConfig != null) {
            commandBuilder
                .addDesugaredLibraryConfiguration(coreLibDesugarConfig)
                .addLibraryFiles(bootclasspath.map { it.toPath() })
        }

        BackportedMethodList.run(
            commandBuilder.setConsumer(consumer).setMinApiLevel(minSdkVersion).build())
        return consumer.strings
    }

    private class CustomStringConsumer : StringConsumer {
        val strings = mutableListOf<String>()

        override fun accept(string: String, handler: DiagnosticsHandler) {
            strings.add(string)
        }

        override fun finished(handler: DiagnosticsHandler) {}
    }
}
