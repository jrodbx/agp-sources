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

package com.android.build.gradle.tasks

import com.android.SdkConstants.FD_RES_NAVIGATION
import com.android.SdkConstants.FN_NAVIGATION_JSON
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationActionImpl
import com.android.ide.common.blame.SourceFilePosition
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.manifmerger.NavigationXmlDocumentData
import com.android.manifmerger.NavigationXmlLoader
import com.android.utils.FileUtils
import com.google.gson.GsonBuilder
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

private val DOT_XML_EXT = Regex("\\.xml$")

/**
 * A task that parses the navigation xml files and produces a single navigation.json file with the
 * deep link data needed for any downstream app manifest merging.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class ExtractDeepLinksTask: NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val navFilesFolders: ListProperty<Directory>

    @get:Optional
    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, String>

    /**
     * If [forAar] is true, (1) use [SourceFilePosition.UNKNOWN] to avoid leaking source file
     * locations into the AAR, and (2) don't write an output navigation.json when there are no
     * navigation xml inputs because we don't want to package an empty navigation.json in the AAR.
     */
    @get:Optional
    @get:Input
    abstract val forAar: Property<Boolean>

    @get:OutputFile
    abstract val navigationJson: RegularFileProperty

    override fun doTaskAction() {
        val navigationIds = mutableSetOf<String>()
        val navDatas = mutableListOf<NavigationXmlDocumentData>()
        navFilesFolders.get().forEach { directory ->
            val folder = directory.asFile
            if (folder.exists()) {
                folder.listFiles().map { navigationFile ->
                    val navigationId = navigationFile.name.replace(DOT_XML_EXT, "")
                    if (navigationIds.add(navigationId)) {
                        navigationFile.inputStream().use { inputStream ->
                            navDatas.add(
                                NavigationXmlLoader
                                    .load(navigationId, navigationFile, inputStream)
                                    .convertToData(manifestPlaceholders.get().toMap(), forAar.get())
                            )
                        }
                    }
                }
            }
        }
        if (!forAar.get() || navDatas.isNotEmpty()) {
            FileUtils.writeToFile(
                navigationJson.asFile.get(),
                GsonBuilder().setPrettyPrinting().create().toJson(navDatas)
            )
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : BaseCreationAction(creationConfig) {
        override val forAar = false
        override val internalArtifactType = InternalArtifactType.NAVIGATION_JSON
        override val name: String
            get() = computeTaskName("extractDeepLinks")
    }

    class AarCreationAction(
        creationConfig: ComponentCreationConfig
    ) : BaseCreationAction(creationConfig) {
        override val forAar = true
        override val internalArtifactType = InternalArtifactType.NAVIGATION_JSON_FOR_AAR
        override val name: String
            get() = computeTaskName("extractDeepLinksForAar")
    }

    abstract class BaseCreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<ExtractDeepLinksTask, ComponentCreationConfig>(
        creationConfig
    ), AndroidResourcesTaskCreationAction by AndroidResourcesTaskCreationActionImpl(
        creationConfig
    ) {

        abstract val forAar: Boolean
        abstract val internalArtifactType: InternalArtifactType<RegularFile>

        override val type: Class<ExtractDeepLinksTask>
            get() = ExtractDeepLinksTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ExtractDeepLinksTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExtractDeepLinksTask::navigationJson
            ).withName(FN_NAVIGATION_JSON).on(internalArtifactType)
        }

        override fun configure(
            task: ExtractDeepLinksTask
        ) {
            super.configure(task)
            creationConfig.sources.res { resSources ->
                task.navFilesFolders.set(
                    resSources.all.map {
                        it.flatten()
                    }.map { directories ->
                        directories.map { directory ->
                            directory.dir(FD_RES_NAVIGATION)
                        }
                    }
                )
            }
            task.navFilesFolders.disallowChanges()
            task.manifestPlaceholders.setDisallowChanges(
                creationConfig.manifestPlaceholdersCreationConfig?.placeholders,
                handleNullable = {
                    empty()
                }
            )
            task.forAar.set(forAar)
        }
    }
}
