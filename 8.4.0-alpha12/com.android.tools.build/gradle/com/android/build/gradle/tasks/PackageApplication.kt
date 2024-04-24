/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_IDE_MODEL
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.build.BaselineProfileDetails
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.stats.GradleBuildProjectMetrics
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Task to package an Android application (APK).  */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.APK_PACKAGING)
abstract class PackageApplication : PackageAndroidArtifact() {
    private lateinit var _transformationRequest: ArtifactTransformationRequest<PackageApplication>

    @Internal
    override fun getTransformationRequest(): ArtifactTransformationRequest<PackageAndroidArtifact> {
        @Suppress("UNCHECKED_CAST")
        return _transformationRequest as ArtifactTransformationRequest<PackageAndroidArtifact>
    }

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val dexMetadataDirectory: DirectoryProperty

    /**
     * Minimum SDK version for dexing, which may be different from [minSdkVersion] (see
     * [DexingCreationConfig.getMinSdkVersionForDexing]).
     */
    @get:Input
    abstract val minSdkVersionForDexing: Property<Int>

    // ----- CreationAction -----
    /**
     * Configures the task to perform the "standard" packaging, including all files that should end
     * up in the APK.
     */
    class CreationAction(
        creationConfig: ApkCreationConfig,
        private val outputDirectory: File,
        manifests: Provider<Directory>,
        manifestType: Artifact<Directory>
    ) : PackageAndroidArtifact.CreationAction<PackageApplication>(
        creationConfig,
        manifests,
        manifestType
    ) {

        private var transformationRequest: ArtifactTransformationRequest<PackageApplication>? = null
        private var task: PackageApplication? = null
        override val name: String
            get() = computeTaskName("package")

        override val type: Class<PackageApplication>
            get() = PackageApplication::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PackageApplication>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.packageAndroidTask = taskProvider
            val useOptimizedResources = !creationConfig.debuggable &&
                    !creationConfig.componentType.isForTesting &&
                    creationConfig.services.projectOptions[BooleanOption.ENABLE_RESOURCE_OPTIMIZATIONS]
            val useResourcesShrinker = creationConfig
                .androidResourcesCreationConfig
                ?.useResourceShrinker == true
            val operationRequest = creationConfig.artifacts.use(taskProvider)
                .wiredWithDirectories(
                    PackageAndroidArtifact::resourceFiles,
                    PackageApplication::outputDirectory
                )

            transformationRequest = when {
                useOptimizedResources -> operationRequest.toTransformMany(
                    InternalArtifactType.OPTIMIZED_PROCESSED_RES,
                    SingleArtifact.APK,
                    outputDirectory.absolutePath,
                    ::customizeBuiltArtifacts
                )

                useResourcesShrinker -> operationRequest.toTransformMany(
                    InternalArtifactType.SHRUNK_PROCESSED_RES,
                    SingleArtifact.APK,
                    outputDirectory.absolutePath,
                    ::customizeBuiltArtifacts
                )

                else -> operationRequest.toTransformMany(
                    InternalArtifactType.PROCESSED_RES,
                    SingleArtifact.APK,
                    outputDirectory.absolutePath,
                    ::customizeBuiltArtifacts
                )
            }

            // in case configure is called before handleProvider, we need to save the request.
            transformationRequest?.let {
                task?.let { t -> t._transformationRequest = it }
            }
            creationConfig
                .artifacts
                .setInitialProvider(taskProvider, PackageApplication::ideModelOutputFile)
                .atLocation(outputDirectory)
                .withName(BuiltArtifactsImpl.METADATA_FILE_NAME)
                .on(APK_IDE_MODEL)
        }

        override fun configure(task: PackageApplication) {
            super.configure(task)
            task.dexMetadataDirectory.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.DEX_METADATA_DIRECTORY)
            )
            task.minSdkVersionForDexing.setDisallowChanges(
                creationConfig.dexing.minSdkVersionForDexing
            )
        }

        override fun finalConfigure(task: PackageApplication) {
            super.finalConfigure(task)
            this.task = task
            transformationRequest?.let {
                task._transformationRequest = it
            }
        }
    }

    companion object {

        private fun customizeBuiltArtifacts(task: PackageApplication, input: BuiltArtifactsImpl): BuiltArtifactsImpl {
            check(input.baselineProfiles == null)
            check(input.minSdkVersionForDexing == null)

            return input.copy(
                baselineProfiles = if (task.dexMetadataDirectory.isPresent) {
                    val dexMetadataPropertiesFile = File(
                        task.dexMetadataDirectory.get().asFile, SdkConstants.FN_DEX_METADATA_PROP)
                    if (dexMetadataPropertiesFile.exists()) {
                        baselineProfileDataForJson(
                            input.elements,
                            dexMetadataPropertiesFile,
                            task.outputDirectory.get().asFile,
                        )
                    } else {
                        null
                    }
                } else null,
                minSdkVersionForDexing = task.minSdkVersionForDexing.get() // See b/284201412
            )
        }

        private fun baselineProfileDataForJson(
            mappedElements: Collection<BuiltArtifactImpl>,
            dexMetadataPropertiesFile: File,
            apkDirectory: File
        ): List<BaselineProfileDetails> {
            val apkNames = mappedElements.map {
                File(it.outputFile).nameWithoutExtension
            }
            val baselineProfilesMapping = mutableMapOf<String, MutableList<String>>()
            dexMetadataPropertiesFile.readLines().forEach {
                val entry = it.split("=")
                baselineProfilesMapping.getOrPut(entry[1]) { mutableListOf() }.add(entry[0])
            }
            val baselineProfileData = mutableListOf<BaselineProfileDetails>()
            baselineProfilesMapping.forEach { entry ->
                val supportedApis = entry.value.map { it.toInt() }
                val baselineProfiles = mutableSetOf<File>()
                val dmFile = dexMetadataPropertiesFile.parentFile.resolve(entry.key)
                val fileIndex = dmFile.parentFile.name
                apkNames.forEach {
                    val renamedDmFile = FileUtils.join(
                        apkDirectory, "baselineProfiles", fileIndex, "$it.dm")
                    renamedDmFile.parentFile.mkdirs()
                    FileUtils.copyFile(dmFile, renamedDmFile)
                    baselineProfiles.add(renamedDmFile)
                }
                baselineProfileData.add(BaselineProfileDetails(
                        supportedApis.min(), supportedApis.max(), baselineProfiles)
                )
            }
            baselineProfileData.sortBy { it.minApi }
            return baselineProfileData
        }

        @JvmStatic
        fun recordMetrics(
            projectPath: String?,
            apkOutputFile: File?,
            resourcesApFile: File?,
            analyticsService: AnalyticsService
        ) {
            val metricsStartTime = System.nanoTime()
            val metrics = GradleBuildProjectMetrics.newBuilder()
            val apkSize = getSize(apkOutputFile)
            if (apkSize != null) {
                metrics.apkSize = apkSize
            }
            val resourcesApSize =
                getSize(resourcesApFile)
            if (resourcesApSize != null) {
                metrics.resourcesApSize = resourcesApSize
            }
            metrics.metricsTimeNs = System.nanoTime() - metricsStartTime
            analyticsService.getProjectBuillder(projectPath!!)?.setMetrics(metrics)
        }

        private fun getSize(file: File?): Long? {
            return if (file == null) {
                null
            } else try {
                Files.size(file.toPath())
            } catch (e: IOException) {
                null
            }
        }
    }
}
