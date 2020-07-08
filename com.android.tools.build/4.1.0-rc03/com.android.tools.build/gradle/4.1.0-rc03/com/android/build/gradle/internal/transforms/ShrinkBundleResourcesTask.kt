/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms

import android.databinding.tool.util.Preconditions
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.variant.VariantOutput
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
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
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ResourceUsageAnalyzer
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/**
 * Task to shrink resources for the android app bundle
 */
abstract class ShrinkBundleResourcesTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val compressedResources: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val uncompressedResources: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var dex: FileCollection
        private set

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val lightRClasses: RegularFileProperty

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rTxtFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val mappingFileSrc: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifests: DirectoryProperty

    @get:Input
    abstract val enableRTxtResourceShrinking: Property<Boolean>

    @get:Input
    abstract val useNewResourceShrinker: Property<Boolean>

    private lateinit var mainSplit: VariantOutput

    override fun doTaskAction() {
        if (useNewResourceShrinker.get()) {
            Preconditions.check(
                enableRTxtResourceShrinking.get(),
                "New implementation of resource shrinker supports gathering resources from R " +
                        "text files only. Enable 'android.enableRTxtResourceShrinking' flag to " +
                        "use it."
            )
        }

        val uncompressedResourceFile = uncompressedResources.get().asFile
        val compressedResourceFile = compressedResources.get().asFile

        var reportFile: File? = null
        val mappingFile = mappingFileSrc.orNull?.asFile
        if (mappingFile != null) {
            val logDir = mappingFile.parentFile
            if (logDir != null) {
                reportFile = File(logDir, "resources.txt")
            }
        }

        FileUtils.mkdirs(compressedResourceFile.parentFile)

        val rSource = if (enableRTxtResourceShrinking.get()){
            rTxtFile.get().asFile
        } else {
            lightRClasses.get().asFile
        }

        val manifestFile = BuiltArtifactsLoaderImpl().load(mergedManifests)
            ?.getBuiltArtifact(mainSplit)
            ?: throw java.lang.RuntimeException("Cannot find merged manifest file for $mainSplit")

        // Analyze resources and usages and strip out unused
        val analyzer = createResourceShrinker(rSource, File(manifestFile.outputFile), reportFile)
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
            analyzer.rewriteResourceZip(uncompressedResourceFile, compressedResourceFile)

            // Dump some stats
            val unused = analyzer.unusedResourceCount
            if (unused > 0) {
                val sb = StringBuilder(200)
                sb.append("Removed unused resources")

                // This is a bit misleading until we can strip out all resource types:
                //int total = analyzer.getTotalResourceCount()
                //sb.append("(" + unused + "/" + total + ")")

                val before = uncompressedResourceFile.length()
                val after = compressedResourceFile.length()
                val percent = ((before - after) * 100 / before).toInt().toLong()
                sb.append(": Binary resource data reduced from ").append(toKbString(before))
                    .append("KB to ").append(toKbString(after)).append("KB: Removed ")
                    .append(percent).append("%")

                println(sb.toString())
            }
        } finally {
            analyzer.close()
        }
    }

    private fun createResourceShrinker(
        rSource: File,
        manifestFile: File,
        reportFile: File?
    ): ResourceShrinker =
        when {
            useNewResourceShrinker.get() -> createNewResourceShrinker(
                rSource,
                manifestFile,
                reportFile
            )
            else -> createLegacyResourceShrinker(rSource, manifestFile, reportFile)
        }

    private fun createLegacyResourceShrinker(
        rSource: File,
        manifestFile: File,
        reportFile: File?
    ): ResourceUsageAnalyzer {
        val analyzer = ResourceUsageAnalyzer(
            rSource,
            dex.files,
            manifestFile,
            mappingFileSrc.orNull?.asFile,
            resourceDir.get().asFile,
            reportFile,
            ApkFormat.PROTO
        )
        analyzer.isVerbose = logger.isEnabled(LogLevel.INFO)
        analyzer.isDebug = logger.isEnabled(LogLevel.DEBUG)
        return analyzer
    }

    private fun createNewResourceShrinker(
        rSource: File,
        manifestFile: File,
        reportFile: File?
    ): ResourceShrinkerImpl {
        val manifestUsageRecorder = XmlAndroidManifestUsageRecorder(manifestFile.toPath())
        val dexClassesUsageRecorder = dex.files.map { DexUsageRecorder(it.toPath()) }

        return ResourceShrinkerImpl(
            ResourcesGathererFromRTxt(rSource, ""),
            mappingFileSrc.orNull?.asFile?.let { ProguardMappingsRecorder(it.toPath()) },
            listOf(manifestUsageRecorder) + dexClassesUsageRecorder,
            RawResourcesGraphBuilder(resourceDir.get().asFile.toPath()),
            LoggerAndFileDebugReporter(logger, reportFile),
            ApkFormat.PROTO
        )
    }

    companion object {
        private fun toKbString(size: Long): String {
            return (size.toInt() / 1024).toString()
        }
    }

    class CreationAction(componentProperties: ComponentPropertiesImpl) :
        VariantTaskCreationAction<ShrinkBundleResourcesTask, ComponentPropertiesImpl>(
            componentProperties
        ) {

        override val name: String = computeTaskName("shrink", "Resources")
        override val type: Class<ShrinkBundleResourcesTask>
            get() = ShrinkBundleResourcesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ShrinkBundleResourcesTask>
        ) {
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ShrinkBundleResourcesTask::compressedResources
            )
                .withName("shrunk-bundled-res.ap_")
                .on(InternalArtifactType.SHRUNK_LINKED_RES_FOR_BUNDLE)
        }

        override fun configure(
            task: ShrinkBundleResourcesTask
        ) {
            super.configure(task)

            val artifacts = creationConfig.artifacts

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LINKED_RES_FOR_BUNDLE,
                task.uncompressedResources
            )
            task.mainSplit = creationConfig.outputs.getMainSplit()

            task.dex = creationConfig.globalScope.project.files(
                if (creationConfig.variantScope.consumesFeatureJars()) {
                    creationConfig.artifacts.get(InternalArtifactType.BASE_DEX)
                } else {
                    artifacts.getAll(InternalMultipleArtifactType.DEX)
                })

            if (creationConfig
                    .services.projectOptions[BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING]
            ) {
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

            task.enableRTxtResourceShrinking.set(creationConfig
                .services.projectOptions[BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING])
            task.useNewResourceShrinker.set(creationConfig
                .services.projectOptions[BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER])

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_NOT_COMPILED_RES,
                task.resourceDir)

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.APK_MAPPING,
                task.mappingFileSrc)

            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_MANIFESTS,
                task.mergedManifests)
        }
    }
}
