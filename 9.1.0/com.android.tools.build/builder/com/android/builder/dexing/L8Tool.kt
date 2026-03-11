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

@file:JvmName("L8Tool")

package com.android.builder.dexing

import com.android.tools.r8.ByteDataView
import com.android.tools.r8.ClassFileConsumer
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.DexIndexedConsumer
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.L8
import com.android.tools.r8.L8Command
import com.android.tools.r8.origin.Origin
import com.android.utils.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.exists

// Starting index to make sure output dex files' names differ from classes.dex
internal const val START_CLASSES_DEX_INDEX = 1000

internal const val L8_MAPPING_HEADER = "# L8 Desugaring Mapping"

enum class L8OutputMode {
  DexIndexed,
  ClassFile,
}

/**
 * Process desugar library jars with L8 for library desugaring.
 *
 * The processing can be run in two modes based on the [outputMode]. With [L8OutputMode.ClassFile], the desugar library jars are desugared
 * into class files which will be used as input for trace reference tool (see [runTraceReferenceTool]). With [L8OutputMode.DexIndexed], the
 * desugar library jars are processed into a dex file which will be packaged into apk/aab.
 *
 * @param inputClasses the class files to be desugared
 * @param output the output directory where the dex files will be written
 * @param libConfiguration the configuration of the desugared library
 * @param libraries the library dependencies
 * @param minSdkVersion the minimum sdk version
 * @param keepRules the keep rules to use for shrinking
 * @param isDebuggable whether the build is debuggable
 * @param outputMode the output mode of the dex files
 * @param inputArtProfile the input art profile to be rewritten (optional)
 * @param outputArtProfile the output art profile path (optional)
 * @param inputMappingFile the existing mapping file (e.g. from R8) to be merged with L8's mapping (optional)
 * @param outputMappingFile the path where the final merged mapping file should be written (optional)
 */
fun runL8(
  inputClasses: Collection<Path>,
  output: Path,
  libConfiguration: String,
  libraries: Collection<Path>,
  minSdkVersion: Int,
  keepRules: KeepRulesConfig,
  isDebuggable: Boolean,
  outputMode: L8OutputMode,
  inputArtProfile: Path? = null,
  outputArtProfile: Path? = null,
  inputMappingFile: Path? = null,
  outputMappingFile: Path? = null,
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
    keepRules.keepRulesConfigurations.forEach { logger.fine("Keep rules configuration: $it") }
    logger.fine("Output mode: $outputMode")
  }
  FileUtils.cleanOutputDir(output.toFile())

  val programConsumer =
    when (outputMode) {
      // Create our own consumer to write out dex files. We do not want them to be named
      // classes.dex because it confuses the packager in legacy multidex mode.
      // See b/142452386.
      L8OutputMode.DexIndexed ->
        object : DexIndexedConsumer.ForwardingConsumer(null) {
          override fun accept(fileIndex: Int, data: ByteDataView?, descriptors: MutableSet<String>?, handler: DiagnosticsHandler?) {
            data ?: return

            val outputFile = output.resolve("classes${START_CLASSES_DEX_INDEX + fileIndex}.dex").toFile()
            outputFile.outputStream().buffered().use { it.write(data.buffer, data.offset, data.length) }
          }
        }
      L8OutputMode.ClassFile -> ClassFileConsumer.ArchiveConsumer(output.resolve("desugared-desugar-lib.jar"))
    }

  val l8CommandBuilder =
    L8Command.builder().addProgramFiles(inputClasses).addSpecialLibraryConfiguration(libConfiguration).addLibraryFiles(libraries)

  if (inputArtProfile != null && inputArtProfile.exists() && outputArtProfile != null) {
    wireArtProfileRewriting(l8CommandBuilder, inputArtProfile, outputArtProfile)
  }

  if (inputMappingFile != null && Files.exists(inputMappingFile)) {
    val r8MapId = extractMapId(inputMappingFile)
    if (r8MapId != null) {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("Stamping L8 Dex with R8 Map ID: $r8MapId")
      }

      l8CommandBuilder.addProguardConfiguration(
        listOf("-keepattributes SourceFile", "-renamesourcefileattribute \"r8-map-id-$r8MapId\""),
        Origin.unknown(),
      )
    } else {
      logger.warning("Could not find 'pg_map_id' in R8 mapping file at $inputMappingFile. L8 Dex will not be stamped with an ID.")
    }
  }

  val tempL8Mapping: Path? = if (outputMappingFile != null) Files.createTempFile("l8_map", ".txt") else null

  if (tempL8Mapping != null) {
    l8CommandBuilder.setProguardMapOutputPath(tempL8Mapping)
  }

  l8CommandBuilder
    .setMinApiLevel(minSdkVersion)
    .setMode(if (isDebuggable) CompilationMode.DEBUG else CompilationMode.RELEASE)
    .setProgramConsumer(programConsumer)

  if (keepRules.keepRulesFiles.isNotEmpty()) {
    l8CommandBuilder.addProguardConfigurationFiles(keepRules.keepRulesFiles)
  }

  if (keepRules.keepRulesConfigurations.isNotEmpty()) {
    l8CommandBuilder.addProguardConfiguration(keepRules.keepRulesConfigurations, Origin.unknown())
  }

  try {
    // Multi-threading is managed at the Gradle task level, so here we run L8 directly in the
    // current thread
    L8.run(l8CommandBuilder.build(), MoreExecutors.newDirectExecutorService())

    if (outputMappingFile != null) {
      // Initialize Output with R8's mapping
      if (inputMappingFile != null && Files.exists(inputMappingFile)) {
        inputMappingFile.toFile().copyTo(outputMappingFile.toFile(), overwrite = true)
      } else {
        // R8 didn't produce a map (e.g. -dontobfuscate), but L8 might have.
        // Or neither did. We must ensure the output file exists.
        Files.createFile(outputMappingFile)
      }

      // Append L8's mapping
      if (tempL8Mapping != null && Files.exists(tempL8Mapping)) {
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("Merging L8 mapping into main mapping file")
        }

        // Append L8 content to the output file
        Files.newOutputStream(outputMappingFile, StandardOpenOption.APPEND).use { outputStream ->
          Files.newBufferedReader(tempL8Mapping).use { reader ->
            outputStream.write("\n$L8_MAPPING_HEADER\n".toByteArray(StandardCharsets.UTF_8))

            // Strip headers (#) from the appended file.
            reader
              .lineSequence()
              .dropWhile { it.trimStart().startsWith("#") }
              .forEach { line ->
                outputStream.write(line.toByteArray(StandardCharsets.UTF_8))
                outputStream.write("\n".toByteArray(StandardCharsets.UTF_8))
              }
          }
        }
      }
    }
  } finally {
    if (tempL8Mapping != null) {
      Files.deleteIfExists(tempL8Mapping)
    }
  }
}

private fun extractMapId(mappingFile: Path): String? {
  if (!Files.exists(mappingFile)) return null

  Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8).use { reader ->
    var line = reader.readLine()
    while (line != null) {
      val trimmed = line.trim()

      if (trimmed.startsWith("# pg_map_id:")) {
        return trimmed.substringAfter(":").trim()
      }

      if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
        return null
      }

      line = reader.readLine()
    }
  }
  return null
}

data class KeepRulesConfig(val keepRulesFiles: List<Path>, val keepRulesConfigurations: List<String>)
