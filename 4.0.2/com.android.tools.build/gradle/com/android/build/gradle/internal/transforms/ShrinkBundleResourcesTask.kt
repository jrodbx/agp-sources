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

import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MultipleArtifactType
import com.android.build.gradle.internal.scope.VariantScope
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

    private lateinit var mainSplit: ApkData

    override fun doTaskAction() {
        val uncompressedResourceFile = uncompressedResources.get().asFile
        val compressedResourceFile= compressedResources.get().asFile

        val classes = dex.files

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

        val manifestFile = ExistingBuildElements.from(InternalArtifactType.BUNDLE_MANIFEST, mergedManifests)
            .element(mainSplit)
            ?.outputFile
                ?: throw RuntimeException("Cannot find merged manifest file")

        // Analyze resources and usages and strip out unused
        val analyzer = ResourceUsageAnalyzer(
            rSource,
            classes,
            manifestFile,
            mappingFile,
            resourceDir.get().asFile,
            reportFile,
            ResourceUsageAnalyzer.ApkFormat.PROTO
        )
        try {
            analyzer.isVerbose = logger.isEnabled(LogLevel.INFO)
            analyzer.isDebug = logger.isEnabled(LogLevel.DEBUG)
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
            analyzer.dispose()
        }
    }

    companion object {
        private fun toKbString(size: Long): String {
            return (size.toInt() / 1024).toString()
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ShrinkBundleResourcesTask>(variantScope) {

        override val name: String = variantScope.getTaskName("shrink", "Resources")
        override val type: Class<ShrinkBundleResourcesTask>
            get() = ShrinkBundleResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ShrinkBundleResourcesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.SHRUNK_LINKED_RES_FOR_BUNDLE,
                taskProvider,
                ShrinkBundleResourcesTask::compressedResources,
                "shrunk-bundled-res.ap_"
            )
        }

        override fun configure(task: ShrinkBundleResourcesTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LINKED_RES_FOR_BUNDLE,
                task.uncompressedResources
            )
            task.mainSplit = variantScope.variantData.publicVariantPropertiesApi.outputs.getMainSplit().apkData

            task.dex = if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.BASE_DEX)) {
                variantScope
                    .artifacts
                    .getFinalProductAsFileCollection(InternalArtifactType.BASE_DEX).get()
            } else if (variantScope.artifacts.hasFinalProducts(MultipleArtifactType.DEX)) {
                variantScope.globalScope.project.files(
                    variantScope.artifacts.getOperations().getAll(MultipleArtifactType.DEX))
            } else {
                variantScope.transformManager.getPipelineOutputAsFileCollection(StreamFilter.DEX)
            }

            if (variantScope
                    .globalScope.projectOptions[BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING]) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.RUNTIME_SYMBOL_LIST,
                    task.rTxtFile
                )
            } else {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR,
                    task.lightRClasses
                )
            }

            task.enableRTxtResourceShrinking.set(variantScope.globalScope
                .projectOptions[BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING])

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_NOT_COMPILED_RES,
                task.resourceDir)

            if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.APK_MAPPING))
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.APK_MAPPING,
                    task.mappingFileSrc)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.BUNDLE_MANIFEST,
                task.mergedManifests)
        }
    }
}
