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

import com.android.SdkConstants.FD_MAIN
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.resources.generateLocaleList
import com.android.ide.common.resources.readResourcesPropertiesFile
import com.android.ide.common.resources.validateLocale
import com.android.ide.common.resources.writeSupportedLocales
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/** Task to extract supported locales from res qualifiers for locale configuration */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class ExtractSupportedLocalesTask : NonIncrementalTask() {
    // A txt file containing the list of supported languages for this variant in gradle project
    @get:OutputFile
    abstract val localeList: RegularFileProperty

    // TODO (b/273374246): replace this with a res map
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val nonMainResSet: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val mainResSet: ConfigurableFileCollection

    @get:Input
    abstract val fromAppModule: Property<Boolean>

    public override fun doTaskAction() {
        val resources = listOf(nonMainResSet.files, mainResSet.files).flatten()

        val localeList = generateLocaleList(resources)

        var validatedDefaultLocale: String? = null

        if (fromAppModule.get()) {
            // Find resources.properties file
            val propFiles = mainResSet.files.map { File(it, "resources.properties") }.filter { it.exists() }
            val noResourcesPropertiesMessage = "No resources.properties file found. " +
                "See https://developer.android.com/r/studio-ui/build/automatic-per-app-languages"
            if (propFiles.isEmpty() && resources.isNotEmpty()) { // Mandate a resource property file
                logger.error(noResourcesPropertiesMessage)
                throw RuntimeException(noResourcesPropertiesMessage)
            }

            val defaultLocale = readResourcesPropertiesFile(propFiles)

            if (defaultLocale == null) {
                val noDefaultLocaleMessage = "No locale is set for unqualified res. " +
                    "See https://developer.android.com/r/studio-ui/build/automatic-per-app-languages"
                logger.error(noDefaultLocaleMessage)
                throw RuntimeException(noDefaultLocaleMessage)
            }

            validatedDefaultLocale = validateLocale(defaultLocale) ?:
                throw RuntimeException("The default locale \"$defaultLocale\" from the file " +
                    "\"${propFiles.first().path}\" is invalid. Please choose a valid locale. " +
                    "See https://developer.android.com/r/studio-ui/build/automatic-per-app-languages")
        }

        writeSupportedLocales(this.localeList.get().asFile, localeList, validatedDefaultLocale)
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<ExtractSupportedLocalesTask, ComponentCreationConfig>(
            creationConfig
        ) {
        override val name: String
            get() = computeTaskName("extract", "SupportedLocales")
        override val type: Class<ExtractSupportedLocalesTask>
            get() = ExtractSupportedLocalesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ExtractSupportedLocalesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExtractSupportedLocalesTask::localeList
            ).withName("supported_locales.txt")
                .on(InternalArtifactType.SUPPORTED_LOCALE_LIST)
        }

        override fun configure(
            task: ExtractSupportedLocalesTask
        ) {
            super.configure(task)
            creationConfig
                .sources.res { resSources ->
                    val localeSources = resSources.getVariantSourcesWithFilter()
                    task.nonMainResSet.from(
                        *(localeSources.filter { it.key != FD_MAIN }.values.toTypedArray())
                    )
                    task.mainResSet.from(localeSources[FD_MAIN])
                }
            task.nonMainResSet.disallowChanges()
            task.mainResSet.disallowChanges()

            task.fromAppModule.setDisallowChanges(creationConfig.componentType.isBaseModule)
        }
    }
}
