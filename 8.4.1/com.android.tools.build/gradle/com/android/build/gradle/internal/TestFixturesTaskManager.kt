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

package com.android.build.gradle.internal

import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.dependency.ConfigurationVariantMapping
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.res.GenerateApiPublicTxtTask
import com.android.build.gradle.internal.res.GenerateEmptyResourceFilesTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.BundleLibraryClassesDir
import com.android.build.gradle.internal.tasks.BundleLibraryClassesJar
import com.android.build.gradle.internal.tasks.LibraryAarJarsTask
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.BooleanOption.LINT_ANALYSIS_PER_COMPONENT
import com.android.build.gradle.tasks.BundleAar
import com.android.build.gradle.tasks.CompileLibraryResourcesTask
import com.android.build.gradle.tasks.ExtractAnnotations
import com.android.build.gradle.tasks.ExtractDeepLinksTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.android.build.gradle.tasks.ZipMergingTask
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.TaskProvider

class TestFixturesTaskManager(
    project: Project,
    globalConfig: GlobalTaskCreationConfig,
    private val localConfig: TaskManagerConfig,
): TaskManager(project, globalConfig) {

    /** Create tasks for the specified test fixtures component.  */
    fun createTasks(testFixturesComponent: TestFixturesCreationConfig) {
        createAssembleTask(testFixturesComponent)
        createAnchorTasks(testFixturesComponent)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(testFixturesComponent)

        // java resources tasks
        createProcessJavaResTask(testFixturesComponent)

        // java resources merging task
        taskFactory.register(
            MergeJavaResourceTask.CreationAction(
                javaResMergingScopes, testFixturesComponent.mainVariant.packaging, testFixturesComponent
            )
        )

        // android resources tasks
        if (testFixturesComponent.buildFeatures.androidResources) {
            taskFactory.register(ExtractDeepLinksTask.CreationAction(testFixturesComponent))
            taskFactory.register(ExtractDeepLinksTask.AarCreationAction(testFixturesComponent))

            createGenerateResValuesTask(testFixturesComponent)

            val flags: Set<MergeResources.Flag> =
                if (globalConfig.namespacedAndroidResources) {
                    Sets.immutableEnumSet(
                        MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                        MergeResources.Flag.PROCESS_VECTOR_DRAWABLES
                    )
                } else {
                    Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES)
                }
            // Create a merge task to only merge the resources from this library and not
            // the dependencies. This is what gets packaged in the aar.
            basicCreateMergeResourcesTask(
                testFixturesComponent,
                TaskManager.MergeType.PACKAGE,
                includeDependencies = false,
                processResources = false,
                alsoOutputNotCompiledResources = false,
                flags = flags,
                taskProviderCallback = object: TaskProviderCallback<MergeResources> {
                    override fun handleProvider(taskProvider: TaskProvider<MergeResources>) {
                        testFixturesComponent.artifacts
                            .setInitialProvider<RegularFile, MergeResources>(taskProvider)
                            { obj: MergeResources -> obj.publicFile }
                            .withName(SdkConstants.FN_PUBLIC_TXT)
                            .on(InternalArtifactType.PUBLIC_RES)
                    }
                }
            )

            // This task merges all the resources, including the dependencies of this library.
            // This should be unused, except that external libraries might consume it.
            // Also used by the VerifyLibraryResourcesTask (only ran in release builds).
            createMergeResourcesTask(
                testFixturesComponent,
                processResources = false,
                flags = ImmutableSet.of()
            )

            // Task to generate the public.txt for the API that always exists
            // Unlike the internal one which is packaged in the AAR which only exists if the
            // developer has explicitly marked resources as public.
            taskFactory.register(GenerateApiPublicTxtTask.CreationAction(testFixturesComponent))

            taskFactory.register(CompileLibraryResourcesTask.CreationAction(testFixturesComponent))

            // Add a task to generate resource source files, directing the location
            // of the r.txt file to be directly in the bundle.
            createProcessResTask(
                testFixturesComponent,
                null,
                TaskManager.MergeType.PACKAGE,
                testFixturesComponent.services.projectInfo.getProjectBaseName()
            )

            // Only verify resources if in Release and not namespaced.
            if (!testFixturesComponent.debuggable &&
                !globalConfig.namespacedAndroidResources) {
                createVerifyLibraryResTask(testFixturesComponent)
            }

            registerLibraryRClassTransformStream(testFixturesComponent)
        } else {
            // Create a task to generate empty/mock required resource artifacts.
            taskFactory.register(GenerateEmptyResourceFilesTask.CreateAction(testFixturesComponent))
        }

        // Add a task to merge or generate the manifest
        taskFactory.register(
            ProcessLibraryManifest.CreationAction(
                testFixturesComponent,
                targetSdkVersion = null,
                maxSdkVersion = null
            )
        )

        // Add tasks to merge the assets folders
        createMergeAssetsTask(testFixturesComponent)
        taskFactory.register(MergeSourceSetFolders.LibraryAssetCreationAction(testFixturesComponent))

        // compilation tasks

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(testFixturesComponent)

        // Add a task to auto-generate classes for ML model files.
        createMlkitTask(testFixturesComponent)

        val javacTask = createJavacTask(testFixturesComponent)
        TaskManager.setJavaCompilerTask(javacTask, testFixturesComponent)

        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (testFixturesComponent
                .services
                .projectOptions
                .get(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS)) {
            taskFactory.register(ExtractAnnotations.CreationAction(testFixturesComponent))
        }

        maybeCreateTransformClassesWithAsmTask(testFixturesComponent)

        // packaging tasks

        // Create jar with library classes used for publishing to runtime elements.
        taskFactory.register(
            BundleLibraryClassesJar.CreationAction(
                testFixturesComponent, AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
            )
        )

        // Also create a directory containing the same classes for incremental dexing
        taskFactory.register(BundleLibraryClassesDir.CreationAction(testFixturesComponent))

        // Add a task to create the AAR metadata file
        taskFactory.register(AarMetadataTask.CreationAction(testFixturesComponent))

        // Add a task to write the local lint AAR file
        taskFactory.register(BundleAar.TestFixturesLocalLintCreationAction(testFixturesComponent))

        // Create a jar with both classes and java resources.  This artifact is not
        // used by the Android application plugin and the task usually don't need to
        // be executed.  The artifact is useful for other Gradle users who needs the
        // 'jar' artifact as API dependency.
        taskFactory.register(ZipMergingTask.CreationAction(testFixturesComponent))

        // now add a task that will take all the classes and java resources and package them
        // into the main and secondary jar files that goes in the AAR.
        // This is used for building the AAR.
        taskFactory.register(
            LibraryAarJarsTask.CreationAction(
                testFixturesComponent,
                minifyEnabled = false
            )
        )

        createBundleTaskForTestFixtures(testFixturesComponent)

        if (globalConfig.avoidTaskRegistration.not()
            && testFixturesComponent.services.projectOptions.get(LINT_ANALYSIS_PER_COMPONENT)
            && globalConfig.lintOptions.ignoreTestFixturesSources.not()
        ) {
            taskFactory.register(
                AndroidLintAnalysisTask.PerComponentCreationAction(
                    testFixturesComponent,
                    fatalOnly = false
                )
            )
            taskFactory.register(
                LintModelWriterTask.PerComponentCreationAction(
                    testFixturesComponent,
                    useModuleDependencyLintModels = false,
                    fatalOnly = false,
                    isMainModelForLocalReportTask = false
                )
            )
        }

        // This hides the assemble test fixtures task from the task list.
        testFixturesComponent.taskContainer.assembleTask.configure { task: Task ->
            task.group = null
        }
    }

    private fun createBundleTaskForTestFixtures(testFixturesComponent: TestFixturesCreationConfig) {
        taskFactory.register(BundleAar.TestFixturesCreationAction(testFixturesComponent))
        testFixturesComponent.taskContainer
            .assembleTask
            .configure { task: Task ->
                task.dependsOn(
                    testFixturesComponent.artifacts.get(
                        SingleArtifact.AAR
                    )
                )
            }
        // publish testFixtures for libraries only
        if (testFixturesComponent.mainVariant !is LibraryCreationConfig) {
            return
        }
        val variantDependencies = testFixturesComponent.variantDependencies
        testFixturesComponent.publishInfo?.components?.forEach {
            val componentName = it.componentName
            val component = project.components.findByName(componentName) as AdhocComponentWithVariants? ?:
            localConfig.componentFactory.adhoc(componentName).let { project.components.add(it) } as AdhocComponentWithVariants
            val apiPub = variantDependencies.getElements(PublishedConfigSpec(AndroidArtifacts.PublishedConfigType.API_PUBLICATION, it))
            val runtimePub = variantDependencies.getElements(PublishedConfigSpec(AndroidArtifacts.PublishedConfigType.RUNTIME_PUBLICATION, it))
            component.addVariantsFromConfiguration(
                apiPub, ConfigurationVariantMapping("compile", it.isClassifierRequired)
            )
            component.addVariantsFromConfiguration(
                runtimePub, ConfigurationVariantMapping("runtime", it.isClassifierRequired)
            )
        }
    }

    override val javaResMergingScopes = setOf(
        InternalScopedArtifacts.InternalScope.LOCAL_DEPS,
    )
}
