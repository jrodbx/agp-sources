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
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.ANDROID_AAPT_IGNORE
import com.android.manifmerger.NavigationXmlDocumentData
import com.android.manifmerger.NavigationXmlLoader
import com.android.utils.FileUtils
import com.google.gson.GsonBuilder
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.stream.Collectors

private val DOT_XML_EXT = Regex("\\.xml$")

@CacheableTask
abstract class ExtractDeepLinksTask: AndroidVariantTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var navFilesFolders: List<File>
        private set

    @get:OutputFile
    abstract val navigationJson: RegularFileProperty

    @TaskAction
    fun create() {
        val navigationIds = mutableSetOf<String>()
        val navDatas = mutableListOf<NavigationXmlDocumentData>()
        navFilesFolders.forEach { folder ->
            if (folder.exists()) {
                folder.listFiles().map { navigationFile ->
                    val navigationId = navigationFile.name.replace(DOT_XML_EXT, "")
                    if (navigationIds.add(navigationId)) {
                        navigationFile.inputStream().use { inputStream ->
                            navDatas.add(
                                NavigationXmlLoader
                                    .load(navigationId, navigationFile, inputStream)
                                    .convertToData())
                        }
                    }
                }
            }
        }
        FileUtils.writeToFile(
            navigationJson.asFile.get(),
            GsonBuilder().setPrettyPrinting().create().toJson(navDatas))
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<ExtractDeepLinksTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("extractDeepLinks")
        override val type: Class<ExtractDeepLinksTask>
            get() = ExtractDeepLinksTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ExtractDeepLinksTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExtractDeepLinksTask::navigationJson
            ).withName("navigation.json").on(InternalArtifactType.NAVIGATION_JSON)
        }

        override fun configure(
            task: ExtractDeepLinksTask
        ) {
            super.configure(task)
            val aaptEnv = creationConfig.services.gradleEnvironmentProvider.getEnvVariable(
                ANDROID_AAPT_IGNORE
            ).forUseAtConfigurationTime().orNull
            task.navFilesFolders =
                creationConfig.variantSources
                    .getResourceSets(false, aaptEnv).stream()
                    .flatMap {
                        it.sourceFiles.stream().map { File(it, FD_RES_NAVIGATION) }
                    }.collect(Collectors.toList()).reversed()
        }
    }
}
