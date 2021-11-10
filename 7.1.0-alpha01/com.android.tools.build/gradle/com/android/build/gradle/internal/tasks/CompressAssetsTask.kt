/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks

import com.android.SdkConstants.DOT_JAR
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.files.KeyedFileCache
import com.android.builder.packaging.PackagingUtils
import com.android.zipflinger.BytesSource
import com.android.zipflinger.ZipArchive
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import java.io.File
import java.nio.file.Files
import java.util.function.Predicate
import java.util.zip.Deflater
import java.util.zip.Deflater.BEST_SPEED
import java.util.zip.Deflater.DEFAULT_COMPRESSION
import javax.inject.Inject

/**
 * Task to compress assets before they're packaged in the APK.
 *
 * This task outputs a directory of single-entry jars (instead of a single jar) so that the
 * downstream packaging task doesn't have to manage its incremental state via a [KeyedFileCache].
 *
 * Each single-entry jar file's relative path in the output directory is equal to "assets/" + the
 * corresponding asset's relative path in the input directory + ".jar".
 */
@CacheableTask
abstract class CompressAssetsTask : NewIncrementalTask() {
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:Input
    abstract val noCompress: ListProperty<String>

    @get:Input
    abstract val compressionLevel: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    override fun doTaskAction(inputChanges: InputChanges) {
        CompressAssetsDelegate(
            workerExecutor.noIsolation(),
            inputDir.get().asFile,
            outputDir.get().asFile,
            PackagingUtils.getNoCompressPredicateForJavaRes(noCompress.get()),
            compressionLevel.get(),
            inputChanges.getFileChanges(inputDir)
        ).run()
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<CompressAssetsTask, ApkCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("compress", "Assets")

        override val type: Class<CompressAssetsTask>
            get() = CompressAssetsTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<CompressAssetsTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CompressAssetsTask::outputDir
            ).withName("out").on(InternalArtifactType.COMPRESSED_ASSETS)
        }

        override fun configure(
            task: CompressAssetsTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_ASSETS,
                task.inputDir
            )
            task.noCompress.setDisallowChanges(creationConfig.services.projectInfo.getExtension().aaptOptions.noCompress)
            task.compressionLevel.setDisallowChanges(
                if (creationConfig.debuggable) {
                    BEST_SPEED
                } else {
                    DEFAULT_COMPRESSION
                }
            )
        }
    }
}

/**
 * Delegate to compress assets
 */
@VisibleForTesting
class CompressAssetsDelegate(
    private val workQueue: WorkQueue,
    val inputDir: File,
    val outputDir: File,
    private val noCompressPredicate: Predicate<String>,
    private val compressionLevel: Int,
    val changes: Iterable<FileChange>
) {

    fun run() {
        for (change in changes) {
            if (change.fileType == FileType.DIRECTORY) {
                continue
            }
            val entryPath = "assets/${change.normalizedPath}"
            val targetFile = File(outputDir, entryPath + DOT_JAR)
            val entryCompressionLevel = if (noCompressPredicate.test(entryPath)) {
                Deflater.NO_COMPRESSION
            } else {
                compressionLevel
            }
            workQueue.submit(CompressAssetsWorkAction::class.java) {
                it.input.set(change.file)
                it.output.set(targetFile)
                it.entryPath.set(entryPath)
                it.entryCompressionLevel.set(entryCompressionLevel)
                it.changeType.set(change.changeType)
            }
        }
    }
}

/**
 * [WorkAction] to compress an asset file into a single-entry jar
 */
abstract class CompressAssetsWorkAction @Inject constructor(
    private val compressAssetsWorkParameters: CompressAssetsWorkParameters
): WorkAction<CompressAssetsWorkParameters> {

    override fun execute() {
        val output = compressAssetsWorkParameters.output.get().asFile.toPath()
        val changeType = compressAssetsWorkParameters.changeType.get()
        if (changeType != ChangeType.ADDED) {
            Files.deleteIfExists(output)
        }
        if (changeType != ChangeType.REMOVED) {
            Files.createDirectories(output.parent)
            ZipArchive(output).use { jar ->
                jar.add(
                    BytesSource(
                        compressAssetsWorkParameters.input.get().asFile.toPath(),
                        compressAssetsWorkParameters.entryPath.get(),
                        compressAssetsWorkParameters.entryCompressionLevel.get()
                    )
                )
            }
        }
    }
}

/**
 * [WorkParameters] for [CompressAssetsWorkAction]
 */
abstract class CompressAssetsWorkParameters: WorkParameters {
    abstract val input: RegularFileProperty
    abstract val output: RegularFileProperty
    abstract val entryPath: Property<String>
    abstract val entryCompressionLevel: Property<Int>
    abstract val changeType: Property<ChangeType>
}
