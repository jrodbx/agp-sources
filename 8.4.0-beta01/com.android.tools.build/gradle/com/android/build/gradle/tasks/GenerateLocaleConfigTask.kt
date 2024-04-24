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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.resources.writeLocaleConfig
import com.android.ide.common.resources.mergeLocaleLists
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkParameters
import java.io.File

const val LOCALE_CONFIG_FILE_NAME = "_generated_res_locale_config"

/** Task to generate locale configuration from res qualifiers */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class GenerateLocaleConfigTask : NonIncrementalTask() {
    // A generated xml res file containing the supported locales
    @get:OutputDirectory
    abstract val localeConfig: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val appLocales: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyLocales: ConfigurableFileCollection

    public override fun doTaskAction() {
        workerExecutor.noIsolation().submit(GenerateLocaleWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.appLocales.set(appLocales)
            it.dependencyLocales.setFrom(dependencyLocales)
            it.localeConfig.set(localeConfig)
        }
    }

    /** [WorkParameters] for [GenerateLocaleWorkAction] */
    abstract class GenerateLocaleWorkParameters: ProfileAwareWorkAction.Parameters() {
        abstract val appLocales: RegularFileProperty
        abstract val dependencyLocales: ConfigurableFileCollection
        abstract val localeConfig: DirectoryProperty
    }

    abstract class GenerateLocaleWorkAction: ProfileAwareWorkAction<GenerateLocaleWorkParameters>() {
        override fun run() {
            val appLocaleList = parameters.appLocales.get().asFile.readLines()

            // get a list of all locales to merge
            val allLocales = mutableListOf(appLocaleList)
            parameters.dependencyLocales.files.forEach {
                val supportedLocales = it.readLines()
                allLocales.add(supportedLocales)
            }

            val mergedLocaleList = mergeLocaleLists(allLocales)
            val localeConfigFolder = parameters.localeConfig.get().asFile
            val localeConfigFile =
                File(localeConfigFolder, "xml${File.separator}$LOCALE_CONFIG_FILE_NAME.xml")
            localeConfigFile.parentFile.mkdirs()

            writeLocaleConfig(output = localeConfigFile, mergedLocaleList)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<GenerateLocaleConfigTask, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("generate", "LocaleConfig")
        override val type: Class<GenerateLocaleConfigTask>
            get() = GenerateLocaleConfigTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<GenerateLocaleConfigTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateLocaleConfigTask::localeConfig
            ).atLocation(
                creationConfig.paths.getGeneratedResourcesDir("localeConfig")
                    .get().asFile.absolutePath
            ).on(InternalArtifactType.GENERATED_LOCALE_CONFIG)
        }

        override fun configure(
            task: GenerateLocaleConfigTask
        ) {
            super.configure(task)

            task.dependencyLocales.fromDisallowChanges(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.SUPPORTED_LOCALE_LIST
                )
            )
            task.appLocales.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.SUPPORTED_LOCALE_LIST))
        }
    }
}
