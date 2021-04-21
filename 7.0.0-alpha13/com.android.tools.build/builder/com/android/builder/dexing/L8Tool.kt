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

@file:JvmName("L8Tool")

package com.android.builder.dexing

import com.android.tools.r8.ByteDataView
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.DexIndexedConsumer
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.L8
import com.android.tools.r8.L8Command
import com.android.tools.r8.origin.Origin
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

// Starting index to make sure output dex files' names differ from classes.dex
internal const val START_CLASSES_DEX_INDEX = 1000

fun runL8(
    inputClasses: Collection<Path>,
    output: Path,
    libConfiguration: String,
    libraries: Collection<Path>,
    minSdkVersion: Int,
    keepRules: KeepRulesConfig,
    isDebuggable: Boolean
) {
    val logger: Logger = Logger.getLogger("L8")
    if (logger.isLoggable(Level.FINE)) {
        logger.fine("*** Using L8 to process code ***")
        logger.fine("Program classes: $inputClasses")
        logger.fine("Special library configuration: $libConfiguration")
        logger.fine("Library classes: $libraries")
        logger.fine("Min Api level: $minSdkVersion")
        logger.fine("Is debuggable: $isDebuggable")
        keepRules.keepRulesFiles.forEach { logger.fine("Keep rules file: $it") }
        keepRules.keepRulesConfigurations.forEach {
            logger.fine("Keep rules configuration: $it") }
    }
    FileUtils.cleanOutputDir(output.toFile())

    // Create our own consumer to write out dex files. We do not want them to be named classes.dex
    // because it confuses the packager in legacy multidex mode. See b/142452386.
    val programConsumer = object : DexIndexedConsumer.ForwardingConsumer(null) {

        override fun accept(
            fileIndex: Int,
            data: ByteDataView?,
            descriptors: MutableSet<String>?,
            handler: DiagnosticsHandler?
        ) {
            data ?: return

            val outputFile =
                output.resolve("classes${START_CLASSES_DEX_INDEX + fileIndex}.dex").toFile()
            outputFile.outputStream().buffered().use {
                it.write(data.buffer, data.offset, data.length)
            }
        }
    }

    val l8CommandBuilder = L8Command.builder()
        .addProgramFiles(inputClasses)
        .setProgramConsumer(programConsumer)
        .addSpecialLibraryConfiguration(libConfiguration)
        .addLibraryFiles(libraries)
        .setMinApiLevel(minSdkVersion)
        .setMode(if (isDebuggable) CompilationMode.DEBUG else CompilationMode.RELEASE)

    if (keepRules.keepRulesFiles.isNotEmpty()) {
        l8CommandBuilder.addProguardConfigurationFiles(keepRules.keepRulesFiles)
    }

    if (keepRules.keepRulesConfigurations.isNotEmpty()) {
        l8CommandBuilder.addProguardConfiguration(
            keepRules.keepRulesConfigurations, Origin.unknown())
    }

    L8.run(l8CommandBuilder.build(), MoreExecutors.newDirectExecutorService())
}

data class KeepRulesConfig(
    val keepRulesFiles: List<Path>,
    val keepRulesConfigurations: List<String>
)