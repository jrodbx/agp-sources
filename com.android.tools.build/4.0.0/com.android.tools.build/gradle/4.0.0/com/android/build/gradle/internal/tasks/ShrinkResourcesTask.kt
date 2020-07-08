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

package com.android.build.gradle.internal.tasks

import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildElementsTransformParams
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MultipleArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ResourceUsageAnalyzer
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.xml.parsers.ParserConfigurationException

/**
 * Implementation of Resource Shrinking as a task.
 */
@CacheableTask
abstract class ShrinkResourcesTask : NonIncrementalTask() {

    private var buildTypeName: String? = null

    private lateinit var aaptOptions: AaptOptions

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val uncompressedResources: DirectoryProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val lightRClasses: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rTxtFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDir: DirectoryProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mappingFileSrc: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifests: DirectoryProperty

    @get:Input
    abstract val debuggableBuildType: Property<Boolean>

    @get:Input
    abstract val enableRTxtResourceShrinking: Property<Boolean>

    @get:Input
    abstract val variantTypeName: Property<String>

    @Input
    fun getAaptOptionsAsString(): String {
        return Joiner.on(";")
            .join(
                aaptOptions.ignoreAssetsPattern ?: "",
                Joiner.on(":")
                    .join(aaptOptions.noCompress ?: listOf<String>()),
                aaptOptions.failOnMissingConfigEntry,
                Joiner.on(":")
                    .join(aaptOptions.additionalParameters ?:listOf<String>()),
                aaptOptions.cruncherProcesses
            )
    }

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val compressedResources: DirectoryProperty

    override fun doTaskAction() {

        val mergedManifestsOutputs =
            ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, mergedManifests)

        ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, uncompressedResources)
            .transform(
                getWorkerFacadeWithWorkers(),
                SplitterRunnable::class.java
            ) { apkInfo: ApkData, buildInput: File ->
                SplitterParams(
                    apkInfo,
                    buildInput,
                    mergedManifestsOutputs,
                    classes.toList(),
                    this
                )
            }.into(
                InternalArtifactType.SHRUNK_PROCESSED_RES,
                compressedResources.get().asFile
            )
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<ShrinkResourcesTask>(variantScope) {
        override val type = ShrinkResourcesTask::class.java
        override val name = variantScope.getTaskName("shrink", "Res")

        private val classes = variantScope.transformManager
            .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                scopes.intersect(TransformManager.SCOPE_FULL_PROJECT).isNotEmpty()
                        && (contentTypes.contains(DefaultContentType.CLASSES)
                        || contentTypes.contains(ExtendedContentType.DEX))
            }

        override fun handleProvider(taskProvider: TaskProvider<out ShrinkResourcesTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                artifactType = InternalArtifactType.SHRUNK_PROCESSED_RES,
                taskProvider = taskProvider,
                productProvider = ShrinkResourcesTask::compressedResources,
                fileName = "out"
            )
        }

        override fun configure(task: ShrinkResourcesTask) {

            super.configure(task)

            val variantData = variantScope.variantData

            val artifacts = variantScope.artifacts

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PROCESSED_RES,
                task.uncompressedResources
            )

            if (variantScope
                    .globalScope.projectOptions[BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING]) {
                artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.RUNTIME_SYMBOL_LIST,
                    task.rTxtFile
                )
            } else {
                artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR,
                    task.lightRClasses
                )
            }

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_NOT_COMPILED_RES,
                task.resourceDir
            )

            if (artifacts.hasFinalProduct(InternalArtifactType.APK_MAPPING)) {
                artifacts.setTaskInputToFinalProduct(InternalArtifactType.APK_MAPPING,
                    task.mappingFileSrc)
            }

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_MANIFESTS,
                task.mergedManifests
            )

            task.aaptOptions = variantScope.globalScope.extension.aaptOptions

            task.buildTypeName = variantData.variantDslInfo.componentIdentity.buildType

            task.variantTypeName.setDisallowChanges(variantData.type.name)

            task.debuggableBuildType.setDisallowChanges(variantData.variantDslInfo.isDebuggable)

            task.enableRTxtResourceShrinking.set(variantScope
                .globalScope.projectOptions[BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING])

            // When R8 produces dex files, this task analyzes them. If R8 or Proguard produce
            // class files, this task will analyze those. That is why both types are specified.
            when {
                artifacts.hasFinalProduct(InternalArtifactType.SHRUNK_CLASSES) -> task.classes.from(
                    artifacts.getFinalProductAsFileCollection(InternalArtifactType.SHRUNK_CLASSES))
                artifacts.hasFinalProducts(MultipleArtifactType.DEX) -> task.classes.from(
                    variantScope.globalScope.project.files(
                        artifacts.getOperations().getAll(MultipleArtifactType.DEX)))
                else -> task.classes.from(classes)
            }
        }
    }

    private class SplitterRunnable @Inject
    constructor(params: SplitterParams) : BuildElementsTransformRunnable(params) {

        override fun run() {
            val params = params as SplitterParams
            var reportFile: File? = null
            if (params.mappingFile != null) {
                val logDir = params.mappingFile.parentFile
                if (logDir != null) {
                    reportFile = File(logDir, "resources.txt")
                }
            }

            FileUtils.mkdirs(params.output.parentFile)

            if (params.mergedManifest == null) {
                try {
                    FileUtils.copyFile(
                        params.uncompressedResourceFile, params.output
                    )
                } catch (e: IOException) {
                    Logging.getLogger(ShrinkResourcesTask::class.java)
                        .error("Failed to copy uncompressed resource file :", e)
                    throw RuntimeException("Failed to copy uncompressed resource file", e)
                }

                return
            }

            // Analyze resources and usages and strip out unused
            val analyzer = ResourceUsageAnalyzer(
                params.rSourceVariant,
                params.classes,
                params.mergedManifest.outputFile,
                params.mappingFile,
                params.resourceDir,
                reportFile,
                ResourceUsageAnalyzer.ApkFormat.BINARY
            )
            try {
                analyzer.isVerbose = params.isInfoLoggingEnabled
                analyzer.isDebug = params.isDebugLoggingEnabled
                try {
                    analyzer.analyze()
                } catch (e: IOException) {
                    throw RuntimeException(e)
                } catch (e: ParserConfigurationException) {
                    throw RuntimeException(e)
                } catch (e: SAXException) {
                    throw RuntimeException(e)
                }

                // Just rewrite the .ap_ file to strip out the res/ files for unused resources
                try {
                    analyzer.rewriteResourceZip(
                        params.uncompressedResourceFile, params.output
                    )
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }

                // Dump some stats
                val unused = analyzer.unusedResourceCount
                if (unused > 0) {
                    val sb = StringBuilder(200)
                    sb.append("Removed unused resources")

                    // This is a bit misleading until we can strip out all resource types:
                    //int total = analyzer.getTotalResourceCount()
                    //sb.append("(" + unused + "/" + total + ")")

                    val before = params.uncompressedResourceFile.length()
                    val after = params.output.length()
                    val percent = ((before - after) * 100 / before).toInt().toLong()

                    sb.append(": Binary resource data reduced from ${toKbString(before)}")
                        .append("KB to ${toKbString(after)}")
                        .append("KB: Removed ${percent}%")

                    if (!ourWarned) {
                        ourWarned = true
                        sb.append(
                            """
                            Note: If necessary, you can disable resource shrinking by adding
                            android {
                                buildTypes {
                                    ${params.buildTypeName} {
                                        shrinkResources false
                                    }
                                }
                            }""".trimIndent()
                        )
                    }

                    Logging.getLogger(SplitterRunnable::class.java)
                        .log(LogLevel.INFO, sb.toString())
                }
            } finally {
                analyzer.dispose()
            }
        }
    }

    private class SplitterParams internal constructor(
        apkInfo: ApkData,
        val uncompressedResourceFile: File,
        mergedManifests: BuildElements,
        val classes: List<File>,
        task: ShrinkResourcesTask
    ) : BuildElementsTransformParams() {

        override val output: File = File(
            task.compressedResources.get().asFile,
            "resources-${apkInfo.baseName}-stripped.ap_"
        )
        val mergedManifest: BuildOutput? = mergedManifests.element(apkInfo)
        val mappingFile: File? = task.mappingFileSrc.orNull?.asFile
        val buildTypeName: String? = task.buildTypeName
        val rSourceVariant: File = if (task.enableRTxtResourceShrinking.get()){
            task.rTxtFile.get().asFile
        } else {
            task.lightRClasses.get().asFile
        }
        val resourceDir: File = task.resourceDir.get().asFile
        val isInfoLoggingEnabled: Boolean = task.logger.isEnabled(LogLevel.INFO)
        val isDebugLoggingEnabled: Boolean = task.logger.isEnabled(LogLevel.DEBUG)
    }

    companion object {

        /** Whether we've already warned about how to turn off shrinking. Used to avoid
         * repeating the same multi-line message for every repeated abi split.  */
        private var ourWarned = true // Logging disabled until shrinking is on by default.

        private fun toKbString(size: Long): String {
            return (size.toInt() / 1024).toString()
        }
    }
}