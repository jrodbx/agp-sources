/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.ManifestProviderImpl
import com.android.build.gradle.internal.tasks.manifest.mergeManifests
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.manifmerger.ManifestMerger2
import com.android.utils.FileUtils
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class PrivacySandboxSdkManifestMergerTask: FusedLibraryManifestMergerTask() {

    @get: InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val mainManifestFile: RegularFileProperty

    abstract class PrivacySandboxManifestMergerParams: ProfileAwareWorkAction.Parameters() {
        abstract val mainAndroidManifest: RegularFileProperty
        abstract val dependencies: MapProperty<String, File>
        abstract val namespace: Property<String>
        abstract val minSdkVersion: Property<String>
        abstract val outMergedManifestLocation: RegularFileProperty
        abstract val reportFile: RegularFileProperty
    }
    abstract class PrivacySandboxManifestMergerWorkAction
        : ProfileAwareWorkAction<PrivacySandboxManifestMergerParams>() {

        override fun run() {
            with(parameters!!) {
                val dependencyManifests =
                        dependencies.get().map { ManifestProviderImpl(it.value, it.key) }
                mergeManifests(
                        mainManifest = mainAndroidManifest.get().asFile,
                        manifestOverlays = emptyList(),
                        dependencies = dependencyManifests,
                        navigationJsons = emptyList(),
                        featureName = null,
                        packageOverride = namespace.get(),
                        namespace = namespace.get(),
                        profileable = false,
                        versionCode = null,
                        versionName = null,
                        minSdkVersion = minSdkVersion.get(),
                        targetSdkVersion = null,
                        maxSdkVersion = null,
                        testOnly = false,
                        extractNativeLibs = null,
                        outMergedManifestLocation = outMergedManifestLocation.get().asFile.absolutePath,
                        outAaptSafeManifestLocation = null,
                        mergeType = ManifestMerger2.MergeType.PRIVACY_SANDBOX_LIBRARY,
                        placeHolders = emptyMap(),
                        optionalFeatures = listOf(
                                ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS,
                                ManifestMerger2.Invoker.Feature.NO_IMPLICIT_PERMISSION_ADDITION,
                                ManifestMerger2.Invoker.Feature.DISABLE_STRIP_LIBRARY_TARGET_SDK,
                                ),
                        dependencyFeatureNames = emptyList(),
                        generatedLocaleConfigAttribute = null,
                        reportFile = reportFile.get().asFile,
                        logger = LoggerWrapper.getLogger(FusedLibraryManifestMergerTask::class.java)
                )
            }
        }
    }

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(PrivacySandboxManifestMergerWorkAction::class.java) { params ->
            params.initializeFromAndroidVariantTask(this)
            val identifierToManifestDependencyFile = libraryManifests.get().associate { result ->
                ProcessApplicationManifest.getArtifactName(result) to result.file
            }
            params.dependencies.set(identifierToManifestDependencyFile)
            params.namespace.set(namespace)
            params.minSdkVersion.set(minSdkVersion)
            params.outMergedManifestLocation.set(mergedFusedLibraryManifest)
            params.reportFile.set(reportFile)
            params.mainAndroidManifest.set(mainManifestFile)
        }
    }

    class CreationAction(private val creationConfig: PrivacySandboxSdkVariantScope):
        TaskCreationAction<PrivacySandboxSdkManifestMergerTask>() {

        override val name: String
            get() = "mergeManifest"

        override val type: Class<PrivacySandboxSdkManifestMergerTask>
            get() = PrivacySandboxSdkManifestMergerTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkManifestMergerTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryManifestMergerTask::mergedFusedLibraryManifest
            ).withName(FN_ANDROID_MANIFEST_XML)
                    .on(FusedLibraryInternalArtifactType.MERGED_MANIFEST)

            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryManifestMergerTask::reportFile
            ).atLocation(
                    FileUtils.join(
                            creationConfig.layout.projectDirectory.asFile,
                            "build",
                            SdkConstants.FD_OUTPUTS,
                            SdkConstants.FD_LOGS
                    ).absolutePath
            ).withName("manifest-merger-$name-report.txt")
                    .on(FusedLibraryInternalArtifactType.MANIFEST_MERGE_REPORT)
            SdkConstants.FD_OUTPUT
        }

        override fun configure(task: PrivacySandboxSdkManifestMergerTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            val libraryManifests = creationConfig.dependencies.getArtifactCollection(
                    Usage.JAVA_RUNTIME,
                    creationConfig.mergeSpec,
                    AndroidArtifacts.ArtifactType.MANIFEST
            )
            task.libraryManifests.set(libraryManifests)
            task.minSdkVersion.setDisallowChanges(creationConfig.minSdkVersion.apiString)
            task.namespace.setDisallowChanges(creationConfig.extension.bundle.applicationId)
            task.tmpDir.setDisallowChanges(
                    creationConfig.layout.buildDirectory.dir("tmp/FusedLibraryManifestMerger")
            )
            task.mainManifestFile.set(
                creationConfig.artifacts.get(PrivacySandboxSdkInternalArtifactType.SANDBOX_MANIFEST)
            )
        }
    }
}
