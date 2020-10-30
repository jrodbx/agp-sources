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

package com.android.build.gradle.internal.tasks

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.PROVIDED_ONLY
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.Scope.TESTED_CODE
import com.android.build.gradle.internal.InternalScope.FEATURES
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import com.android.builder.multidex.D8MainDexList
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Calculate the main dex list using D8.
 */
@CacheableTask
abstract class D8MainDexListTask : NonIncrementalTask() {

    @get:Input
    abstract val errorFormat: Property<SyncOptions.ErrorFormatMode>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aaptGeneratedRules: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val userMultidexProguardRules: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val userMultidexKeepFile: RegularFileProperty

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val inputClasses: ConfigurableFileCollection

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
            userMultidexProguardRules.orNull?.let { userRules ->
                params.proguardRules.from(userRules)
            }
            params.programClasses.from(inputClasses)
            params.libraryClasses.from(libraryClasses)
            params.bootClasspath.from(bootClasspath)
            params.userMultidexKeepFile.set(userMultidexKeepFile.orNull?.asFile)
            params.output.set(output.asFile)
            params.errorFormat.set(errorFormat)
        }
    }

    abstract class MainDexListWorkerAction : ProfileAwareWorkAction<MainDexListWorkerAction.Params>() {

        abstract class Params: ProfileAwareWorkAction.Parameters() {
            abstract val proguardRules: ConfigurableFileCollection
            abstract val programClasses: ConfigurableFileCollection
            abstract val libraryClasses: ConfigurableFileCollection
            abstract val bootClasspath: ConfigurableFileCollection
            abstract val userMultidexKeepFile: Property<File>
            abstract val output: Property<File>
            abstract val errorFormat: Property<SyncOptions.ErrorFormatMode>
        }

        override fun run() {
            // Javac output may be missing if there are no .java sources, so filter missing b/152759930.
            val programClasses = parameters.programClasses.files.filter { it.exists() }
            val libraryFilesNotInInputs =
                parameters.libraryClasses.files.filter { !programClasses.contains(it) } + parameters.bootClasspath.files
            val logger = Logging.getLogger(D8MainDexListTask::class.java)

            logger.debug("Generating the main dex list using D8.")
            logger.debug("Program files: %s", programClasses.joinToString())
            logger.debug("Library files: %s", libraryFilesNotInInputs.joinToString())
            logger.debug("Proguard rule files: %s", parameters.proguardRules.joinToString())

            val mainDexClasses = mutableSetOf<String>()

            mainDexClasses.addAll(
                D8MainDexList.generate(
                    getPlatformRules(),
                    parameters.proguardRules.map { it.toPath() },
                    programClasses.map { it.toPath() },
                    libraryFilesNotInInputs.map { it.toPath() },
                    MessageReceiverImpl(parameters.errorFormat.get(), logger)
                )
            )

            parameters.userMultidexKeepFile.orNull?.let {
                mainDexClasses.addAll(it.readLines())
            }

            parameters.output.get().writeText(mainDexClasses.joinToString(separator = System.lineSeparator()))
        }
    }

    class CreationAction(
        creationConfig: VariantCreationConfig,
        private val includeDynamicFeatures: Boolean
    ) : VariantTaskCreationAction<D8MainDexListTask, VariantCreationConfig>(
        creationConfig
    ) {

        private val inputClasses: FileCollection
        private val libraryClasses: FileCollection

        init {
            val inputScopes: Set<QualifiedContent.ScopeType> = setOf(
                PROJECT,
                SUB_PROJECTS,
                EXTERNAL_LIBRARIES
            ) + (if (includeDynamicFeatures) setOf(FEATURES) else emptySet())

            val libraryScopes = setOf(PROVIDED_ONLY, TESTED_CODE)

            // It is ok to get streams that have more types/scopes than we are asking for, so just
            // check if intersection is not empty. This is what TransformManager does.
            inputClasses = creationConfig.transformManager
                .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                    contentTypes.contains(
                        QualifiedContent.DefaultContentType.CLASSES
                    ) && inputScopes.intersect(scopes).isNotEmpty()
                }
            libraryClasses = creationConfig.transformManager
                .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                    contentTypes.contains(
                        QualifiedContent.DefaultContentType.CLASSES
                    ) && libraryScopes.intersect(scopes).isNotEmpty()
                }
        }

        override val name: String =
            creationConfig.computeTaskName(if (includeDynamicFeatures) "bundleMultiDexList" else "multiDexList")
        override val type: Class<D8MainDexListTask> = D8MainDexListTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<D8MainDexListTask>
        ) {
            super.handleProvider(taskProvider)
            val request = creationConfig.artifacts.setInitialProvider(
                taskProvider, D8MainDexListTask::output
            ).withName("mainDexList.txt")
            if (includeDynamicFeatures) {
                request.on(InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE)
            } else {
                request.on(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
            }
        }

        override fun configure(
            task: D8MainDexListTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES,
                task.aaptGeneratedRules
            )

            val variantDslInfo = creationConfig.variantDslInfo
            val project = creationConfig.globalScope.project

            if (variantDslInfo.multiDexKeepProguard != null) {
                task.userMultidexProguardRules.fileProvider(
                    project.provider { variantDslInfo.multiDexKeepProguard }
                )
            }
            task.userMultidexProguardRules.disallowChanges()

            if (variantDslInfo.multiDexKeepFile != null) {
                task.userMultidexKeepFile.fileProvider(
                    project.provider { variantDslInfo.multiDexKeepFile }
                )
            }
            task.userMultidexKeepFile.disallowChanges()

            task.inputClasses.from(inputClasses).disallowChanges()
            task.libraryClasses.from(libraryClasses).disallowChanges()

            task.bootClasspath.from(creationConfig.variantScope.bootClasspath).disallowChanges()
            task.errorFormat
                .setDisallowChanges(
                    SyncOptions.getErrorFormatMode(creationConfig.services.projectOptions))
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
    "-keep public class * implements java.lang.annotation.Annotation { *;}",
    "-keep public class * extends android.test.InstrumentationTestCase { <init>(); }"
)