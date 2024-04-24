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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.component.impl.KmpUnitTestImpl
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.AndroidTestTaskManager
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.UnitTestTaskManager
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.plugins.LINT_PLUGIN_ID
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.GenerateEmptyResourceFilesTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.R8ParallelBuildService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback
import com.android.build.gradle.internal.tasks.factory.registerTask
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.tasks.BundleAar
import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.android.build.gradle.tasks.ZipMergingTask
import com.android.builder.core.ComponentTypeImpl
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile

class KmpTaskManager(
    project: Project,
    global: GlobalTaskCreationConfig
): TaskManager(
    project,
    global
) {

    override val javaResMergingScopes = setOf(
        InternalScopedArtifacts.InternalScope.LOCAL_DEPS,
    )

    private val lintTaskManager = LintTaskManager(globalConfig, taskFactory, project)

    var hasCreatedTasks = false

    fun createTasks(
        project: Project,
        variant: KmpCreationConfig,
        unitTest: KmpUnitTestImpl?,
        androidTest: KmpAndroidTestImpl?,
    ) {
        createMainVariantTasks(project, variant)
        unitTest?.let {
            createUnitTestTasks(project, unitTest)
        }
        androidTest?.let {
            createAndroidTestTasks(project, androidTest)
        }

        taskFactory.register(PrepareLintJarForPublish.CreationAction(globalConfig))
        variant.artifacts
            .copy(
                InternalArtifactType.LINT_PUBLISH_JAR,
                globalConfig.globalArtifacts
            )

        taskFactory.register(globalConfig.taskNames.compileLintChecks) { task: Task ->
            task.dependsOn(globalConfig.localCustomLintChecks)
        }

        variant.publishBuildArtifacts()

        hasCreatedTasks = true
    }

    private fun createMainVariantTasks(
        project: Project,
        variant: KmpCreationConfig
    ) {
        createAnchorTasks(project, variant)

        maybeCreateJavacTask(variant)

        project.tasks.registerTask(
            BundleLibraryClassesJar.KotlinMultiplatformCreationAction(
                component = variant,
                publishedType = AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
            )
        )
        project.tasks.registerTask(
            BundleLibraryClassesJar.KotlinMultiplatformCreationAction(
                component = variant,
                publishedType = AndroidArtifacts.PublishedConfigType.API_ELEMENTS
            )
        )

        project.tasks.registerTask(
            BundleLibraryClassesDir.KotlinMultiplatformCreationAction(variant)
        )

        // Create a task to generate empty/mock required resource artifacts.
        project.tasks.registerTask(GenerateEmptyResourceFilesTask.CreateAction(variant))

        // Add a task to merge or generate the manifest
        project.tasks.registerTask(
            ProcessLibraryManifest.CreationAction(
                variant,
                targetSdkVersion = null,
                maxSdkVersion = variant.maxSdk
            )
        )

        project.tasks.registerTask(ProcessJavaResTask.CreationAction(variant))
        project.tasks.registerTask(
            MergeJavaResourceTask.CreationAction(
                javaResMergingScopes,
                variant.packaging,
                variant
            )
        )

        if (variant.optimizationCreationConfig.minifiedEnabled) {
            project.tasks.registerTask(
                GenerateLibraryProguardRulesTask.CreationAction(variant)
            )
            R8ParallelBuildService.RegistrationAction(
                project,
                variant.services.projectOptions.get(IntegerOption.R8_MAX_WORKERS)
            ).execute()
            project.tasks.registerTask(
                R8Task.CreationAction(
                    variant,
                    isTestApplication = false,
                    addCompileRClass = false
                )
            )
        }

        // Publishing consumer proguard rules is an opt-in feature for KMP
        if (globalConfig.publishConsumerProguardRules) {
            project.tasks.registerTask(MergeGeneratedProguardFilesCreationAction(variant))
            project.tasks.registerTask(MergeConsumerProguardFilesTask.CreationAction(variant))
            project.tasks.registerTask(ExportConsumerProguardFilesTask.CreationAction(variant))
        }

        // No jacoco transformation for kmp variants, however, we need to publish the classes
        // pre transformation as the artifact is used in the jacoco report task.
        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
            .publishCurrent(
                ScopedArtifact.CLASSES,
                InternalScopedArtifact.PRE_JACOCO_TRANSFORMED_CLASSES,
            )

        if (variant.isAndroidTestCoverageEnabled) {
            val jacocoTask = project.tasks.registerTask(
                JacocoTask.CreationAction(variant)
            )
            // in case of library, we never want to publish the jacoco instrumented classes, so
            // we basically fork the CLASSES into a specific internal type that is consumed
            // by the jacoco report task.
            variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                .use(jacocoTask)
                .toFork(
                    type = ScopedArtifact.CLASSES,
                    inputJars = { a ->  a.jarsWithIdentity.inputJars },
                    inputDirectories = JacocoTask::classesDir,
                    intoJarDirectory = JacocoTask::outputForJars,
                    intoDirDirectory = JacocoTask::outputForDirs,
                    intoType = InternalScopedArtifact.JACOCO_TRANSFORMED_CLASSES
                )
        }

        // Add a task to create the AAR metadata file
        project.tasks.registerTask(AarMetadataTask.CreationAction(variant))


        // Create a jar with both classes and java resources.  This artifact is not
        // used by the Android application plugin and the task usually don't need to
        // be executed.  The artifact is useful for other Gradle users who needs the
        // 'jar' artifact as API dependency.
        project.tasks.registerTask(ZipMergingTask.CreationAction(variant))

        // now add a task that will take all the classes and java resources and package them
        // into the main and secondary jar files that goes in the AAR.
        // This is used for building the AAR.
        project.tasks.registerTask(
            LibraryAarJarsTask.CreationAction(
                variant,
                variant.optimizationCreationConfig.minifiedEnabled
            )
        )

        // Create lint tasks here only if the lint standalone plugin is applied (to avoid
        // Android-specific behavior)
        if (project.plugins.hasPlugin(LINT_PLUGIN_ID)) {
            lintTaskManager.createLintTasks(
                ComponentTypeImpl.KMP_ANDROID,
                variant.name,
                listOf(variant),
                testComponentPropertiesList = emptyList(),
                isPerComponent = true
            )
        }

        project.tasks.registerTask(BundleAar.KotlinMultiplatformLocalLintCreationAction(variant))

        project.tasks.registerTask(BundleAar.KotlinMultiplatformCreationAction(variant))
        variant.taskContainer
            .assembleTask
            .configure { task: Task ->
                task.dependsOn(
                    variant.artifacts.get(
                        SingleArtifact.AAR
                    )
                )
            }

        project.tasks.getByName("assemble").dependsOn(variant.taskContainer.assembleTask)
    }

    private fun createUnitTestTasks(
        project: Project,
        component: KmpUnitTestImpl
    ) {
        createAnchorTasks(project, component, false)

        maybeCreateJavacTask(component)

        with(UnitTestTaskManager(project, globalConfig)) {
            createTopLevelTasks()
            createTasks(component)
        }
    }

    private fun createAndroidTestTasks(
        project: Project,
        component: KmpAndroidTestImpl
    ) {
        createAnchorTasks(project, component, false)

        maybeCreateJavacTask(component)

        with(AndroidTestTaskManager(project, globalConfig)) {
            createTopLevelTasks()
            createTasks(component)
        }
    }

    private fun createAnchorTasks(
        project: Project,
        component: ComponentCreationConfig,
        createPreBuildTask: Boolean = true
    ) {
        project.tasks.registerTask(
            component.computeTaskName("assemble"),
            Task::class.java,
            null,
            object : TaskConfigAction<Task> {
                override fun configure(task: Task) {
                    task.description =
                        "Assembles main Android output for variant <${component.name}>"
                }

            },
            object : TaskProviderCallback<Task> {
                override fun handleProvider(taskProvider: TaskProvider<Task>) {
                    component.taskContainer.assembleTask = taskProvider
                }
            }
        )

        if (createPreBuildTask) {
            project.tasks.registerTask(PreBuildCreationAction(component))
            createDependencyStreams(component)
        }
    }

    private fun maybeCreateJavacTask(
        component: KmpComponentCreationConfig
    ) {
        if (!component.withJava) {
            return
        }
        val javacTask = createJavacTask(component)
        component.androidKotlinCompilation.compileTaskProvider.configure { kotlincTask ->
            component.sources.java {
                (kotlincTask as AbstractKotlinCompile<*>).source(it.getAsFileTrees())
            }
        }

        javacTask.configure { javaCompile ->
            javaCompile.classpath += component.androidKotlinCompilation.output.classesDirs
        }
    }
}
