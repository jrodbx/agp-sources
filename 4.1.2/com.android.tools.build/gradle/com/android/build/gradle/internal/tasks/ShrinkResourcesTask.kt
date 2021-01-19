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

import android.databinding.tool.util.Preconditions
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.res.shrinker.ApkFormat
import com.android.build.gradle.internal.res.shrinker.LoggerAndFileDebugReporter
import com.android.build.gradle.internal.res.shrinker.ResourceShrinker
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerImpl
import com.android.build.gradle.internal.res.shrinker.gatherer.ResourcesGathererFromRTxt
import com.android.build.gradle.internal.res.shrinker.graph.RawResourcesGraphBuilder
import com.android.build.gradle.internal.res.shrinker.obfuscation.ProguardMappingsRecorder
import com.android.build.gradle.internal.res.shrinker.usages.DexUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.XmlAndroidManifestUsageRecorder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ResourceUsageAnalyzer
import com.android.builder.model.CodeShrinker
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.Serializable
import javax.inject.Inject
import javax.xml.parsers.ParserConfigurationException

/**
 * Implementation of Resource Shrinking as a task.
 */
@CacheableTask
abstract class ShrinkResourcesTask : NonIncrementalTask() {

    private var buildTypeName: String? = null

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
    abstract val useNewResourceShrinker: Property<Boolean>

    @get:Nested
    abstract val variantOutputs: ListProperty<VariantOutputImpl>

    @get:Internal
    abstract val artifactTransformationRequest: Property<ArtifactTransformationRequest<ShrinkResourcesTask>>

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val compressedResources: DirectoryProperty

    override fun doTaskAction() {
        if (useNewResourceShrinker.get()) {
            Preconditions.check(
                enableRTxtResourceShrinking.get(),
                "New implementation of resource shrinker supports gathering resources from R " +
                        "text files only. Enable 'android.enableRTxtResourceShrinking' flag to " +
                        "use it."
            )
        }

        val mergedManifestsOutputs = BuiltArtifactsLoaderImpl().load(mergedManifests)
            ?: throw RuntimeException("Cannoft load merged manifests from $mergedManifests")

        artifactTransformationRequest.get().submit(
            task = this,
            workQueue = workerExecutor.noIsolation(),
            actionType = SplitterRunnable::class.java,
            parameterType = SplitterParams::class.java
        ) { builtArtifact: BuiltArtifact, directory: Directory, parameters: SplitterParams ->

            val mergedManifest = mergedManifestsOutputs.getBuiltArtifact(builtArtifact)
                ?: throw RuntimeException("Cannot find manifest file for $builtArtifact")

            val variantOutput = variantOutputs.get().find {
                it.variantOutputConfiguration.outputType == builtArtifact.outputType
                        && it.variantOutputConfiguration.filters == builtArtifact.filters
            } ?: throw java.lang.RuntimeException("Cannot find variant output for $builtArtifact")
            parameters.outputFile.set(File(
                directory.asFile,
                "resources-${variantOutput.baseName}-stripped.ap_"))
            parameters.rSourceVariant.set(if (enableRTxtResourceShrinking.get()){
                rTxtFile.get().asFile } else { lightRClasses.get().asFile })
            parameters.uncompressedResourceFile.set(File(builtArtifact.outputFile))
            parameters.resourceDir.set(resourceDir)
            parameters.infoLoggingEnabled.set(logger.isEnabled(LogLevel.INFO))
            parameters.debugLoggingEnabled.set(logger.isEnabled(LogLevel.DEBUG))
            parameters.useNewResourceShrinker.set(useNewResourceShrinker)
            parameters.classes.set(classes.toList())
            parameters.mergedManifest.set(mergedManifest)

            mappingFileSrc.orNull?.asFile?.also { parameters.mappingFile.set(it) }
            buildTypeName?.also { parameters.buildTypeName.set(it) }
            parameters.outputFile.get().asFile
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<ShrinkResourcesTask, ComponentPropertiesImpl>(
        componentProperties
    ) {
        override val type = ShrinkResourcesTask::class.java
        override val name = computeTaskName("shrink", "Res")

        private val classes = componentProperties.transformManager
            .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                scopes.intersect(TransformManager.SCOPE_FULL_PROJECT).isNotEmpty()
                        && (contentTypes.contains(DefaultContentType.CLASSES)
                        || contentTypes.contains(ExtendedContentType.DEX))
            }

        private lateinit var artifactTransformationRequest: ArtifactTransformationRequest<ShrinkResourcesTask>

        override fun handleProvider(
            taskProvider: TaskProvider<ShrinkResourcesTask>
        ) {
            super.handleProvider(taskProvider)

            artifactTransformationRequest = creationConfig.artifacts.use(taskProvider)
                .wiredWithDirectories(
                    ShrinkResourcesTask::uncompressedResources,
                    ShrinkResourcesTask::compressedResources)
                .toTransformMany(
                    InternalArtifactType.PROCESSED_RES,
                    InternalArtifactType.SHRUNK_PROCESSED_RES)

        }

        override fun configure(
            task: ShrinkResourcesTask
        ) {
            super.configure(task)

            val artifacts = creationConfig.artifacts

            if (creationConfig
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

            artifacts.setTaskInputToFinalProduct(InternalArtifactType.APK_MAPPING,
                task.mappingFileSrc)

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_MANIFESTS,
                task.mergedManifests
            )

            task.buildTypeName = creationConfig.variantDslInfo.componentIdentity.buildType

            task.debuggableBuildType.setDisallowChanges(creationConfig.variantDslInfo.isDebuggable)

            task.artifactTransformationRequest.setDisallowChanges(artifactTransformationRequest)

            task.enableRTxtResourceShrinking.set(creationConfig
                .globalScope.projectOptions[BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING])
            task.useNewResourceShrinker.set(creationConfig
                .globalScope.projectOptions[BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER])

            creationConfig.outputs.getEnabledVariantOutputs().forEach(task.variantOutputs::add)
            task.variantOutputs.disallowChanges()

            // When R8 produces dex files, this task analyzes them. If R8 or Proguard produce
            // class files, this task will analyze those. That is why both types are specified.
            task.classes.from(
                if (creationConfig.variantScope.codeShrinker == CodeShrinker.R8
                    && creationConfig.variantType.isAar) {
                    creationConfig.artifacts.get(InternalArtifactType.SHRUNK_CLASSES)
                } else {
                    artifacts.getAll(InternalMultipleArtifactType.DEX)
                        .map {
                            if (it.isEmpty()) { classes } else {
                                creationConfig.globalScope.project.files(it)
                            }
                    }
                }
            )
        }
    }

    abstract class SplitterRunnable @Inject constructor() : WorkAction<SplitterParams> {

        override fun execute() {
            var reportFile: File? = null
            if (parameters.mappingFile.isPresent) {
                val logDir = parameters.mappingFile.get().asFile.parentFile
                if (logDir != null) {
                    reportFile = File(logDir, "resources.txt")
                }
            }

            FileUtils.mkdirs(parameters.outputFile.get().asFile.parentFile)

            if (!parameters.mergedManifest.isPresent) {
                try {
                    FileUtils.copyFile(
                        parameters.uncompressedResourceFile.get().asFile,
                        parameters.outputFile.get().asFile
                    )
                } catch (e: IOException) {
                    Logging.getLogger(ShrinkResourcesTask::class.java)
                        .error("Failed to copy uncompressed resource file :", e)
                    throw RuntimeException("Failed to copy uncompressed resource file", e)
                }

                return
            }

            // Analyze resources and usages and strip out unused
            val analyzer = createResourceShrinker(reportFile)
            try {
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
                        parameters.uncompressedResourceFile.get().asFile,
                        parameters.outputFile.get().asFile
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
                    // int total = analyzer.getTotalResourceCount()
                    // sb.append("(" + unused + "/" + total + ")")

                    val before = parameters.uncompressedResourceFile.get().asFile.length()
                    val after = parameters.outputFile.get().asFile.length()
                    val percent = ((before - after) * 100 / before).toInt().toLong()

                    sb.append(": Binary resource data reduced from ${toKbString(before)}")
                        .append("KB to ${toKbString(after)}")
                        .append("KB: Removed $percent%")

                    if (!ourWarned) {
                        ourWarned = true
                        sb.append(
                            """
                            Note: If necessary, you can disable resource shrinking by adding
                            android {
                                buildTypes {
                                    ${parameters.buildTypeName} {
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
                analyzer.close()
            }
        }

        private fun createResourceShrinker(reportFile: File?): ResourceShrinker =
            when {
                parameters.useNewResourceShrinker.get() -> createNewResourceShrinker(reportFile)
                else -> createLegacyResourceShrinker(reportFile)
            }

        private fun createLegacyResourceShrinker(reportFile: File?): ResourceUsageAnalyzer {
            val analyzer = ResourceUsageAnalyzer(
                parameters.rSourceVariant.get().asFile,
                parameters.classes.get(),
                File(parameters.mergedManifest.get().outputFile),
                parameters.mappingFile.get().asFile,
                parameters.resourceDir.get().asFile,
                reportFile,
                ApkFormat.BINARY
            )
            analyzer.isVerbose = parameters.infoLoggingEnabled.get()
            analyzer.isDebug = parameters.debugLoggingEnabled.get()
            return analyzer
        }

        private fun createNewResourceShrinker(reportFile: File?): ResourceShrinkerImpl {
            val mergedManifestFile = File(parameters.mergedManifest.get().outputFile).toPath()
            val classes = parameters.classes.get()

            val manifestUsageRecorder = XmlAndroidManifestUsageRecorder(mergedManifestFile)
            val dexClassesUsageRecorder = classes.map { DexUsageRecorder(it.toPath()) }

            val reporter = LoggerAndFileDebugReporter(
                Logging.getLogger(ShrinkResourcesTask::class.java),
                reportFile
            )

            return ResourceShrinkerImpl(
                ResourcesGathererFromRTxt(parameters.rSourceVariant.get().asFile, ""),
                ProguardMappingsRecorder(parameters.mappingFile.get().asFile.toPath()),
                listOf(manifestUsageRecorder) + dexClassesUsageRecorder,
                RawResourcesGraphBuilder(parameters.resourceDir.get().asFile.toPath()),
                reporter,
                ApkFormat.BINARY
            )
        }
    }

    abstract class SplitterParams : WorkParameters, Serializable {

        abstract val outputFile: RegularFileProperty

        abstract val uncompressedResourceFile: RegularFileProperty

        @get:Optional
        abstract val mappingFile: RegularFileProperty

        @get:Optional
        abstract val mergedManifest: Property<BuiltArtifact>

        @get:Optional
        abstract val buildTypeName: Property<String>
        abstract val classes: ListProperty<File>
        abstract val rSourceVariant: RegularFileProperty
        abstract val resourceDir: DirectoryProperty
        abstract val infoLoggingEnabled: Property<Boolean>
        abstract val debugLoggingEnabled: Property<Boolean>
        abstract val useNewResourceShrinker: Property<Boolean>
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
