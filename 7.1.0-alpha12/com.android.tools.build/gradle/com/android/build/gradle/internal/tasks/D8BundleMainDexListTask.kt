/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import com.android.builder.multidex.D8MainDexList
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * A task calculating the main dex list for bundle using D8.
 */
@CacheableTask
abstract class D8BundleMainDexListTask : NonIncrementalTask() {

    @get:Input
    abstract val errorFormat: Property<SyncOptions.ErrorFormatMode>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aaptGeneratedRules: RegularFileProperty

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val userMultidexProguardRules: ListProperty<RegularFile>

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val userMultidexKeepFile: Property<File>

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baseDexDirs: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val featureDexDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val libraryClasses: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            MainDexListWorkerAction::class.java
        ) { params ->
            params.initializeFromAndroidVariantTask(this)
            params.proguardRules.from(aaptGeneratedRules)
            params.proguardRules.from(userMultidexProguardRules.get())
            params.programDexFiles.from(baseDexDirs, featureDexDirs)
            params.libraryClasses.from(libraryClasses)
            params.bootClasspath.from(bootClasspath)
            params.userMultidexKeepFile.set(userMultidexKeepFile)
            params.output.set(output)
            params.errorFormat.set(errorFormat)
        }
    }

    abstract class MainDexListWorkerAction : ProfileAwareWorkAction<MainDexListWorkerAction.Params>() {
        abstract class Params: ProfileAwareWorkAction.Parameters() {
            abstract val proguardRules: ConfigurableFileCollection
            abstract val programDexFiles: ConfigurableFileCollection
            abstract val libraryClasses: ConfigurableFileCollection
            abstract val bootClasspath: ConfigurableFileCollection
            abstract val userMultidexKeepFile: Property<File>
            abstract val output: RegularFileProperty
            abstract val errorFormat: Property<SyncOptions.ErrorFormatMode>
        }

        override fun run() {
            val libraryFiles = parameters.libraryClasses.files + parameters.bootClasspath.files
            val logger = Logging.getLogger(D8BundleMainDexListTask::class.java)

            logger.debug("Generating the main dex list using D8.")
            logger.debug("Program files: %s", parameters.programDexFiles.joinToString())
            logger.debug("Library files: %s", libraryFiles.joinToString())
            logger.debug("Proguard rule files: %s", parameters.proguardRules.joinToString())

            val mainDexClasses = mutableSetOf<String>()

            mainDexClasses.addAll(
                D8MainDexList.generate(
                    getPlatformRules(),
                    parameters.proguardRules.map { it.toPath() },
                    parameters.programDexFiles.map { it.toPath() },
                    libraryFiles.map { it.toPath() },
                    MessageReceiverImpl(parameters.errorFormat.get(), logger)
                )
            )

            parameters.userMultidexKeepFile.orNull?.let {
                mainDexClasses.addAll(it.readLines())
            }

            parameters.output.asFile.get().writeText(
                mainDexClasses.joinToString(separator = System.lineSeparator()))
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<D8BundleMainDexListTask, ApkCreationConfig> (
        creationConfig
    ) {
        private val libraryClasses: FileCollection

        init {
            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            val libraryScopes = setOf(
                com.android.build.api.transform.QualifiedContent.Scope.PROVIDED_ONLY,
                com.android.build.api.transform.QualifiedContent.Scope.TESTED_CODE
            )

            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            libraryClasses = creationConfig.transformManager
                .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                    contentTypes.contains(
                        com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
                    ) && libraryScopes.intersect(scopes).isNotEmpty()
                }
        }

        override val name: String = creationConfig.computeTaskName("bundleMultiDexList")
        override val type: Class<D8BundleMainDexListTask> = D8BundleMainDexListTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<D8BundleMainDexListTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider, D8BundleMainDexListTask::output
            ).withName("mainDexList.txt").on(InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE)
        }

        override fun configure(task: D8BundleMainDexListTask) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES,
                task.aaptGeneratedRules
            )

            task.userMultidexProguardRules.setDisallowChanges(
                creationConfig.artifacts.getAll(MultipleArtifact.MULTIDEX_KEEP_PROGUARD)
            )
            task.userMultidexKeepFile.setDisallowChanges(creationConfig.multiDexKeepFile)
            task.bootClasspath.from(creationConfig.sdkComponents.bootClasspath).disallowChanges()
            task.errorFormat
                .setDisallowChanges(
                    SyncOptions.getErrorFormatMode(creationConfig.services.projectOptions))

            task.libraryClasses.from(libraryClasses).disallowChanges()

            task.baseDexDirs.from(
                creationConfig.artifacts.getAll(InternalMultipleArtifactType.DEX))

            task.featureDexDirs.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.FEATURE_PUBLISHED_DEX
                )
            )
        }
    }
}

internal fun getPlatformRules(): List<String> = listOf(
    "-keep public class * extends android.app.Instrumentation {\n"
            + "  <init>(); \n"
            + "  void onCreate(...);\n"
            + "  android.app.Application newApplication(...);\n"
            + "  void callApplicationOnCreate(android.app.Application);\n"
            + "}",
    "-keep public class * extends android.app.Application { "
            + "  <init>();\n"
            + "  void attachBaseContext(android.content.Context);\n"
            + "}",
    "-keep public class * extends android.app.backup.BackupAgent { <init>(); }",
    "-keep public class * extends android.test.InstrumentationTestCase { <init>(); }"
)
