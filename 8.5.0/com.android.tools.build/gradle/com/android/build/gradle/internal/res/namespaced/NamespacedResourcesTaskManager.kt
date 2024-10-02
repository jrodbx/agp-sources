/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.res.namespaced

import com.android.build.api.artifact.Artifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.tasks.CompileLibraryResourcesTask
import com.android.utils.appendCapitalized
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * Responsible for the creation of tasks to build namespaced resources.
 */
class NamespacedResourcesTaskManager(
    private val taskFactory: TaskFactory,
    private val creationConfig: ComponentCreationConfig
) {

    /**
     * Creates the tasks for dealing with resources in a namespaced way.
     *
     * The current implementation:
     *
     *  1. Links the app as a static library. This provides a *non-final* R-class, and means that
     *  the distinction between app and library is reduced. We can revisit that in the future if
     *  final ids in apps are a vital feature.
     *  2. Links the app and its dependency to produce the final APK. This re-uses the same
     *  [LinkApplicationAndroidResourcesTask] task, as it needs to be split aware.
     *
     * TODO: Test support, Synthesize non-namespaced output.
     */
    fun createNamespacedResourceTasks(
            packageOutputType: Artifact.Single<Directory>?,
            baseName: Provider<String>,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {

        // Compile
        createCompileResourcesTask()
        // We need to strip namespaces from the manifest to bundle, so that it's consumable by
        // non-namespaced projects.
        taskFactory.register(CreateNonNamespacedLibraryManifestTask.CreationAction(creationConfig))
        // TODO: If we want to read the namespaced manifest from the static library, we need to keep
        //       all the data in it, not just a skeleton with the package. See b/117869877
        taskFactory.register(StaticLibraryManifestTask.CreationAction(creationConfig))
        taskFactory.register(LinkLibraryAndroidResourcesTask.CreationAction(creationConfig))
        // TODO: also generate a private R.jar holding private resources.
        taskFactory.register(GenerateNamespacedLibraryRFilesTask.CreationAction(creationConfig))
        if (creationConfig is TestComponentCreationConfig) {
            if (creationConfig.mainVariant.componentType.isAar) {
                createNamespacedLibraryTestProcessResourcesTask(
                    packageOutputType = packageOutputType
                )
            } else {
                createNamespacedAppProcessTask(
                    packageOutputType = packageOutputType,
                    baseName = baseName,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules = false
                )
            }
        } else if (creationConfig.componentType.isApk) {
            createNamespacedAppProcessTask(
                packageOutputType = packageOutputType,
                baseName = baseName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules = useAaptToGenerateLegacyMultidexMainDexProguardRules
            )
        }
        taskFactory.register(CompileRClassTaskCreationAction(creationConfig))
    }

    private fun createNamespacedAppProcessTask(
            packageOutputType: Artifact.Single<Directory>?,
            baseName: Provider<String>,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
        // TODO fix by using the right type for field componentProperties
       taskFactory.register(
           LinkApplicationAndroidResourcesTask.NamespacedCreationAction(
               creationConfig as ApkCreationConfig,
               useAaptToGenerateLegacyMultidexMainDexProguardRules,
               baseName
           )
       )
        if (packageOutputType != null) {
            creationConfig.artifacts.republish(InternalArtifactType.PROCESSED_RES, packageOutputType)
        }
    }

    private fun createNamespacedLibraryTestProcessResourcesTask(
            packageOutputType: Artifact.Single<Directory>?) {
        taskFactory.register(ProcessAndroidAppResourcesTask.CreationAction(creationConfig))
        if (packageOutputType != null) {
            creationConfig.artifacts.republish(InternalArtifactType.PROCESSED_RES, packageOutputType)
        }
    }

    private fun createCompileResourcesTask() {
        // By the time this is called, the list of potential source files is known since the
        // variant API has run. Eventually, it could be better to not do a get() here and instead
        // create a unique Task that uses workers for parallelization.
        creationConfig.sources.res { resSources ->
            resSources.getVariantSources().forEach { dimensionSources ->

                val artifacts = creationConfig.services.fileCollection().also { fileCollection ->
                    fileCollection.from(dimensionSources.directoryEntries
                        .filter { !it.isGenerated }
                        .map { it.asFiles(
                          creationConfig.services.provider {
                          creationConfig.services.projectInfo.projectDirectory
                      }) }
                    )
                }

                val sourceSetName = dimensionSources.name

                val name = "compile".appendCapitalized(sourceSetName) +
                        "ResourcesFor".appendCapitalized(creationConfig.name)
                // TODO : figure out when we need explicit task dependency and potentially remove it.
                taskFactory.register(
                    CompileLibraryResourcesTask.NamespacedCreationAction(
                        name = name,
                        inputDirectories = artifacts,
                        creationConfig = creationConfig
                    )
                )
            }
        }
    }
}
