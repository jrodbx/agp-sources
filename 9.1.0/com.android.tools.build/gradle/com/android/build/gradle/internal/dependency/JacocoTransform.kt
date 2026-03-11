/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.tasks.toSerializable
import com.android.builder.files.RelativeFile
import com.android.builder.files.SerializableChange
import com.android.builder.files.SerializableFileChanges
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

@CacheableTransform
abstract class JacocoTransform : TransformAction<JacocoTransform.Params> {

  abstract class Params : GenericTransformParameters {

    @get:Internal abstract val jacocoVersion: Property<String>

    @get:Classpath abstract val jacocoConfiguration: ConfigurableFileCollection

    @get:Internal abstract val jacocoInstrumentationService: Property<JacocoInstrumentationService>
  }

  @get:Inject abstract val inputChanges: InputChanges

  @get:InputArtifact
  // Take care when handling input changes with @Classpath and @Incremental.
  // Modified files may report multiple FileStatus events
  // such as ADDED, REMOVED may lead to file deletion if not carefully handled!
  // See https://github.com/gradle/gradle/issues/32244
  @get:Classpath
  @get:Incremental
  abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(transformOutputs: TransformOutputs) {
    val inputArtifactFile = inputArtifact.get().asFile
    if (isJarFile(inputArtifactFile)) {
      val outputFile = transformOutputs.file("instrumented_${inputArtifactFile.name}")
      instrumentJar(inputArtifactFile, outputFile)
    } else {
      val changes = inputChanges.getFileChanges(inputArtifact).toSerializable()
      transformDirectory(inputArtifactFile, changes, transformOutputs)
    }
  }

  private fun transformDirectory(rootDir: File, changes: SerializableFileChanges, transformOutputs: TransformOutputs) {
    if (!rootDir.isDirectory) {
      throw IOException("$rootDir must be a directory.")
    }
    val classOutputDir = transformOutputs.dir("instrumented_classes")
    val fileChanges: Map<String, List<SerializableChange>> = changes.fileChanges.groupBy { it.file.invariantSeparatorsPath }
    for ((_, changesForAbsoluteFilepath) in fileChanges.entries) {
      processIncrementalClasspathFileChange(
        changesForAbsoluteFilepath.first().file,
        changesForAbsoluteFilepath.first().normalizedPath,
        changesForAbsoluteFilepath.map { it.fileStatus },
        classOutputDir,
      )
    }
  }

  /**
   * This function processes file changes from input artifact annotated with
   *
   * @param file The input file
   * @param normalizedPath Classpath of the input file reported by Gradle
   * @param fileStatuses are file changes of input file. There should only be a single change, however as explained above there may be
   *   multiple.
   * @param classOutputDir is the directory where the instrumented class should be written or deleted
   * @Classpath and @Incremental. Gradle's file status reporting when using these annotations can ambiguous.
   *
   * When changing file order e.g. adding or deleting, files on the same normalized path that are ordered after the added or deleted file
   * will report as both ADDED and REMOVED. This happens regardless if these files are modified or not. This means we do not know if a
   * MODIFIED event has occurred. So the file must be instrumented in this case.
   *
   * This is an open Gradle issue to address this https://github.com/gradle/gradle/issues/32244.
   */
  private fun processIncrementalClasspathFileChange(
    file: File,
    normalizedPath: String,
    fileStatuses: List<FileStatus>,
    classOutputDir: File,
  ) {
    val relativeFile = RelativeFile.fileInDirectory(normalizedPath.replace(File.separatorChar, '/'), file)
    when {
      fileStatuses.size == 1 && fileStatuses.first() == FileStatus.REMOVED ->
        cleanTransformOutputFile(relativeFile, classOutputDir.toPath())

      fileStatuses.contains(FileStatus.NEW) || fileStatuses.contains(FileStatus.CHANGED) -> instrumentFile(relativeFile, classOutputDir)

      else ->
        // We should never hit this case.
        error(
          "Incremental Jacoco instrumentation failure: " +
            "Invalid FileStatus '${fileStatuses.joinToString()}' for normalised path '${file.absolutePath}'."
        )
    }
  }

  private fun cleanTransformOutputFile(relativeFile: RelativeFile, classesOutputPath: Path) {
    if (getInstrumentationAction(relativeFile.relativePath) == JacocoTask.Action.IGNORE) {
      return
    }
    val fileToRemove = FileUtils.join(classesOutputPath.toFile(), relativeFile.relativePath)
    FileUtils.deleteIfExists(fileToRemove)
  }

  private fun isJarFile(candidateFile: File) = candidateFile.isFile && candidateFile.extension == SdkConstants.EXT_JAR

  private fun instrumentJar(inputJar: File, outputJar: File) {
    try {
      ZipOutputStream(BufferedOutputStream(FileOutputStream(outputJar))).use { instrumentedJar ->
        ZipFile(inputJar).use { zip ->
          val entries = zip.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryName = entry.name
            val data =
              when (getInstrumentationAction(entryName)) {
                JacocoTask.Action.IGNORE -> continue
                JacocoTask.Action.COPY -> ByteStreams.toByteArray(zip.getInputStream(entry))
                JacocoTask.Action.INSTRUMENT ->
                  parameters.jacocoInstrumentationService
                    .get()
                    .instrument(zip.getInputStream(entry), entryName, parameters.jacocoConfiguration, parameters.jacocoVersion.get())
              }
            val nextEntry = ZipEntry(entryName)
            // Any negative time value sets ZipEntry's xdostime to DOSTIME_BEFORE_1980
            // constant.
            nextEntry.time = -1L
            instrumentedJar.putNextEntry(nextEntry)
            instrumentedJar.write(data)
            instrumentedJar.closeEntry()
          }
        }
      }
    } catch (e: IOException) {
      throw UncheckedIOException("Unable to instrument file with Jacoco: $inputJar", e)
    }
  }

  private fun instrumentFile(sourceFile: RelativeFile, outputDir: File) {
    val instrumentationAction = getInstrumentationAction(sourceFile)
    if (instrumentationAction == JacocoTask.Action.IGNORE) {
      return
    }
    val outputFile = outputDir.resolve(sourceFile.relativePath)
    if (!outputFile.exists()) {
      Files.createParentDirs(outputFile)
    }
    if (instrumentationAction == JacocoTask.Action.INSTRUMENT) {
      outputFile.writeBytes(
        sourceFile.file.inputStream().use {
          parameters.jacocoInstrumentationService
            .get()
            .instrument(it, sourceFile.relativePath, parameters.jacocoConfiguration, parameters.jacocoVersion.get())
        }
      )
    } else {
      // instrumentationAction == JacocoTask.Action.COPY
      FileUtils.copyFile(sourceFile.file, outputFile)
    }
  }

  fun getInstrumentationAction(relativeFile: RelativeFile): JacocoTask.Action {
    return getInstrumentationAction(relativeFile.relativePath)
  }

  fun getInstrumentationAction(relativePath: String): JacocoTask.Action {
    return JacocoTask.calculateAction(relativePath)
  }
}
