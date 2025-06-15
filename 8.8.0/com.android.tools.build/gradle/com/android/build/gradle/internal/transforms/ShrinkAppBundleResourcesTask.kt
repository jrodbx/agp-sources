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

package com.android.build.gradle.internal.transforms

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.shrinker.LoggerAndFileDebugReporter
import com.android.build.shrinker.ResourceShrinkerImpl
import com.android.build.shrinker.gatherer.ProtoResourceTableGatherer
import com.android.build.shrinker.graph.ProtoResourcesGraphBuilder
import com.android.build.shrinker.obfuscation.ProguardMappingsRecorder
import com.android.build.shrinker.usages.DexUsageRecorder
import com.android.build.shrinker.usages.ProtoAndroidManifestUsageRecorder
import com.android.build.shrinker.usages.ToolsAttributeUsageRecorder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Task to shrink resources inside Android app bundle. Consumes a bundle built by bundletool
 * and replaces unused resources with dummy content there and outputs bundle with replaced
 * resources.
 *
 * Enabled when android.experimental.enableNewResourceShrinker=true.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION, secondaryTaskCategories = [TaskCategory.ANDROID_RESOURCES])
abstract class ShrinkAppBundleResourcesTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val shrunkBundle: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val originalBundle: RegularFileProperty

    @get:Input
    abstract val baseNamespace: Property<String>

    @get:Input
    abstract val usePreciseShrinking: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val featureSetMetadata: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val mappingFileSrc: RegularFileProperty

    override fun doTaskAction() {
        val modules = FeatureSetMetadata.load(featureSetMetadata.get().asFile)
            .featureNameToNamespaceMap + ("base" to baseNamespace.get())

        workerExecutor.noIsolation().submit(ShrinkAppBundleResourcesAction::class.java) {
            it.originalBundle.set(originalBundle)
            it.shrunkBundle.set(shrunkBundle)
            it.modules.set(modules)
            it.usePreciseShrinking.set(usePreciseShrinking)
            if (mappingFileSrc.isPresent) {
                mappingFileSrc.get().asFile.parentFile?.let { logDir ->
                    it.report.set(File(logDir, "resources.txt"));
                }
            }
        }
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<ShrinkAppBundleResourcesTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String = computeTaskName("shrinkBundle", "Resources")
        override val type: Class<ShrinkAppBundleResourcesTask>
            get() = ShrinkAppBundleResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ShrinkAppBundleResourcesTask>) {
            creationConfig.artifacts.use(taskProvider)
                .wiredWithFiles(
                    ShrinkAppBundleResourcesTask::originalBundle,
                    ShrinkAppBundleResourcesTask::shrunkBundle)
                .toTransform(InternalArtifactType.INTERMEDIARY_BUNDLE)
        }

        override fun configure(task: ShrinkAppBundleResourcesTask) {
            super.configure(task)
            task.usePreciseShrinking.set(creationConfig.services.projectOptions.get(
              BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER_PRECISE))
            task.baseNamespace.set(creationConfig.namespace)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.FEATURE_SET_METADATA,
                task.featureSetMetadata
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                SingleArtifact.OBFUSCATION_MAPPING_FILE,
                task.mappingFileSrc
            )
        }
    }
}

interface ResourceShrinkerParams : WorkParameters {
    val originalBundle: RegularFileProperty
    val shrunkBundle: RegularFileProperty
    val report: RegularFileProperty
    val modules: MapProperty<String, String>
    val usePreciseShrinking: Property<Boolean>
}

private abstract class ShrinkAppBundleResourcesAction @Inject constructor() :
    WorkAction<ResourceShrinkerParams> {

    override fun execute() {
        val logger = Logging.getLogger(ShrinkAppBundleResourcesTask::class.java)
        val allModules = parameters.modules.get()
        FileUtils.createZipFilesystem(originalBundleFile.toPath()).use { fs ->
            val proguardMappings = fs.getPath(
                "BUNDLE-METADATA",
                "com.android.tools.build.obfuscation",
                "proguard.map"
            )

            val resourcesGatherers = allModules.map {
                ProtoResourceTableGatherer(fs.getPath(it.key, "resources.pb"))
            }

            val obfuscationMappingsRecorder = proguardMappings
                .takeIf { Files.isRegularFile(it) }
                ?.let { ProguardMappingsRecorder(it) }

            val usageRecorders = allModules.flatMap {
                val dexPath = fs.getPath(it.key, "dex")
                val rawResourcesPath = fs.getPath(it.key, "res", "raw")
                val manifest = fs.getPath(it.key, "manifest", "AndroidManifest.xml")
                listOf(
                    DexUsageRecorder(dexPath)
                        .takeIf { Files.isDirectory(dexPath) },
                    ToolsAttributeUsageRecorder(fs.getPath(it.key, "res", "raw"))
                        .takeIf { Files.isDirectory(rawResourcesPath) },
                    ProtoAndroidManifestUsageRecorder(manifest)
                )
            }.filterNotNull()

            val graphBuilders = allModules.map {
                ProtoResourcesGraphBuilder(
                    resourceRoot = fs.getPath(it.key, "res"),
                    resourceTable = fs.getPath(it.key, "resources.pb")
                )
            }

            ResourceShrinkerImpl(
              resourcesGatherers,
              obfuscationMappingsRecorder,
              usageRecorders,
              graphBuilders,
              debugReporter = LoggerAndFileDebugReporter(
                    logDebug = { debugMessage ->
                        if (logger.isEnabled(LogLevel.DEBUG)) {
                            logger.log(LogLevel.DEBUG, debugMessage)
                        }
                    },
                    logInfo = { infoMessage ->
                        if (logger.isEnabled(LogLevel.DEBUG)) {
                            logger.log(LogLevel.DEBUG, infoMessage)
                        }
                    },
                    reportFile
                ),
              supportMultipackages = true,
              usePreciseShrinking = parameters.usePreciseShrinking.get()
            ).use { shrinker ->
                shrinker.analyze()

                shrinker.rewriteResourcesInBundleFormat(
                    originalBundleFile,
                    shrunkBundleFile,
                    allModules
                )

                // Dump some stats
                if (shrinker.unusedResourceCount > 0) {
                    val before = originalBundleFile.length()
                    val after = shrunkBundleFile.length()
                    val percent = ((before - after) * 100 / before).toInt()

                    val stat = "Removed unused resources: Binary bundle size reduced from " +
                            "${toKbString(before)}KB to ${toKbString(after)}KB. Removed $percent%"
                    logger.info(stat)
                }
            }
        }
    }

    private val originalBundleFile by lazy { parameters.originalBundle.get().asFile }
    private val shrunkBundleFile by lazy { parameters.shrunkBundle.get().asFile }
    private val reportFile by lazy { parameters.report.orNull?.asFile }

    companion object {
        private fun toKbString(size: Long): String {
            return (size.toInt() / 1024).toString()
        }
    }
}
