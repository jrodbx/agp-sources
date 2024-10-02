/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.dependency.ConfigurationVariantMapping
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.publishing.ComponentPublishingInfo
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.res.GenerateApiPublicTxtTask
import com.android.build.gradle.internal.res.GenerateEmptyResourceFilesTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.DokkaParallelBuildService
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.BundleLibraryClassesDir
import com.android.build.gradle.internal.tasks.BundleLibraryClassesJar
import com.android.build.gradle.internal.tasks.CheckManifest
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.internal.tasks.LibraryAarJarsTask
import com.android.build.gradle.internal.tasks.LibraryJniLibsTask.ProjectAndLocalJarsCreationAction
import com.android.build.gradle.internal.tasks.LibraryJniLibsTask.ProjectOnlyCreationAction
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask
import com.android.build.gradle.internal.tasks.MergeGeneratedProguardFilesCreationAction
import com.android.build.gradle.internal.tasks.PackageRenderscriptTask
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.BundleAar.LibraryCreationAction
import com.android.build.gradle.tasks.BundleAar.LibraryLocalLintCreationAction
import com.android.build.gradle.tasks.CompileLibraryResourcesTask
import com.android.build.gradle.tasks.ExtractAnnotations
import com.android.build.gradle.tasks.ExtractDeepLinksTask
import com.android.build.gradle.tasks.ExtractDeepLinksTask.AarCreationAction
import com.android.build.gradle.tasks.ExtractSupportedLocalesTask
import com.android.build.gradle.tasks.JavaDocGenerationTask
import com.android.build.gradle.tasks.JavaDocJarTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.MergeSourceSetFolders.LibraryAssetCreationAction
import com.android.build.gradle.tasks.ProcessLibraryArtProfileTask
import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.android.build.gradle.tasks.SourceJarTask
import com.android.build.gradle.tasks.ZipMergingTask
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.tasks.TaskProvider

/** TaskManager for creating tasks in an Android library project.  */
class LibraryTaskManager(
    project: Project,
    variants: Collection<ComponentInfo<LibraryVariantBuilder, LibraryCreationConfig>>,
    testComponents: Collection<TestComponentCreationConfig>,
    testFixturesComponents: Collection<TestFixturesCreationConfig>,
    globalConfig: GlobalTaskCreationConfig,
    localConfig: TaskManagerConfig,
    extension: BaseExtension
) : VariantTaskManager<LibraryVariantBuilder, LibraryCreationConfig>(
    project,
    variants,
    testComponents,
    testFixturesComponents,
    globalConfig,
    localConfig,
    extension
) {
    override fun doCreateTasksForVariant(
        variantInfo: ComponentInfo<LibraryVariantBuilder, LibraryCreationConfig>
    ) {
        val libraryVariant = variantInfo.variant
        val buildFeatures = libraryVariant.buildFeatures
        createAnchorTasks(libraryVariant)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(libraryVariant)
        if (buildFeatures.androidResources) {
            createGenerateResValuesTask(libraryVariant)
            taskFactory.register(ExtractDeepLinksTask.CreationAction(libraryVariant))
            taskFactory.register(AarCreationAction(libraryVariant))
        } else { // Resource processing is disabled.
            // TODO(b/147579629): add a warning for manifests containing resource references.
            if (globalConfig.namespacedAndroidResources) {
                logger
                    .error(
                        "Disabling resource processing in resource namespace aware "
                                + "modules is not supported currently."
                    )
            }

            // Create a task to generate empty/mock required resource artifacts.
            taskFactory.register(GenerateEmptyResourceFilesTask.CreateAction(libraryVariant))
        }

        // Add a task to check the manifest
        taskFactory.register(CheckManifest.CreationAction(libraryVariant))
        taskFactory.register(
            ProcessLibraryManifest.CreationAction(
                libraryVariant,
                libraryVariant.targetSdk,
                libraryVariant.maxSdk
            )
        )
        createRenderscriptTask(libraryVariant)
        if (buildFeatures.androidResources) {
            createMergeResourcesTasks(libraryVariant)
            createCompileLibraryResourcesTask(libraryVariant)
        }
        createShaderTask(libraryVariant)

        // Add tasks to merge the assets folders
        createMergeAssetsTask(libraryVariant)
        createLibraryAssetsTask(libraryVariant)

        // Add a task to create the BuildConfig class
        createBuildConfigTask(libraryVariant)
        if (buildFeatures.androidResources) {
            // Add a task to generate resource source files, directing the location
            // of the r.txt file to be directly in the bundle.
            createProcessResTask(
                libraryVariant,
                null,  // Switch to package where possible so we stop merging resources in
                // libraries
                MergeType.PACKAGE,
                libraryVariant.services.projectInfo.getProjectBaseName()
            )

            // Only verify resources if in Release and not namespaced.
            if (!libraryVariant.debuggable && !globalConfig.namespacedAndroidResources) {
                createVerifyLibraryResTask(libraryVariant)
            }
            registerLibraryRClassTransformStream(libraryVariant)
        }

        // process java resources only, the merge is setup after
        // the task to generate intermediate jars for project to project publishing.
        createProcessJavaResTask(libraryVariant)
        createAidlTask(libraryVariant)

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(libraryVariant)

        // Add a task to auto-generate classes for ML model files.
        createMlkitTask(libraryVariant)

        // Add a compile task
        setJavaCompilerTask(createJavacTask(libraryVariant), libraryVariant)
        taskFactory.register(MergeGeneratedProguardFilesCreationAction(libraryVariant))
        createMergeJniLibFoldersTasks(libraryVariant)
        taskFactory.register(StripDebugSymbolsTask.CreationAction(libraryVariant))
        taskFactory.register(PackageRenderscriptTask.CreationAction(libraryVariant))

        // merge consumer proguard files from different build types and flavors
        taskFactory.register(MergeConsumerProguardFilesTask.CreationAction(libraryVariant))
        taskFactory.register(ExportConsumerProguardFilesTask.CreationAction(libraryVariant))

        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (libraryVariant
                .services
                .projectOptions[BooleanOption.ENABLE_EXTRACT_ANNOTATIONS]
        ) {
            taskFactory.register(ExtractAnnotations.CreationAction(libraryVariant))
        }

        val instrumented = libraryVariant.isAndroidTestCoverageEnabled

        maybeCreateTransformClassesWithAsmTask(libraryVariant)
        if (instrumented) {
            createJacocoTask(libraryVariant)
        }

        // Create jar with library classes used for publishing to runtime elements.
        taskFactory.register(
            BundleLibraryClassesJar.CreationAction(
                libraryVariant, PublishedConfigType.RUNTIME_ELEMENTS
            )
        )

        // Also create a directory containing the same classes for incremental dexing
        taskFactory.register(BundleLibraryClassesDir.CreationAction(libraryVariant))

        // Create a jar with both classes and java resources.  This artifact is not
        // used by the Android application plugin and the task usually don't need to
        // be executed.  The artifact is useful for other Gradle users who needs the
        // 'jar' artifact as API dependency.
        taskFactory.register(ZipMergingTask.CreationAction(libraryVariant))

        // now add a task that will take all the native libs and package
        // them into an intermediary folder. This processes only the PROJECT
        // scope.
        taskFactory.register(
            ProjectOnlyCreationAction(
                libraryVariant, InternalArtifactType.LIBRARY_JNI
            )
        )

        // Now go back to fill the pipeline with transforms used when
        // publishing the AAR

        // first merge the java resources.
        createMergeJavaResTask(libraryVariant)

        // ----- Minify next -----
        maybeCreateJavaCodeShrinkerTask(libraryVariant)

        // now add a task that will take all the classes and java resources and package them
        // into the main and secondary jar files that goes in the AAR.
        // This is used for building the AAR.
        taskFactory.register(
            LibraryAarJarsTask.CreationAction(
                libraryVariant, libraryVariant.optimizationCreationConfig.minifiedEnabled
            )
        )

        // now add a task that will take all the native libs and package
        // them into the libs folder of the bundle. This processes both the PROJECT
        // and the LOCAL_PROJECT scopes
        taskFactory.register(
            ProjectAndLocalJarsCreationAction(
                libraryVariant, InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI
            )
        )

        // Add a task to create the AAR metadata file
        taskFactory.register(AarMetadataTask.CreationAction(libraryVariant))

        // Add a task to write the local lint AAR file
        taskFactory.register(LibraryLocalLintCreationAction(libraryVariant))

        val experimentalProperties = libraryVariant.experimentalProperties
        experimentalProperties.finalizeValue()
        if (!libraryVariant.debuggable &&
                (ModulePropertyKey.OptionalBoolean.VERIFY_AAR_CLASSES
                        .getValue(experimentalProperties.get())
                        ?: globalConfig.services.projectOptions[BooleanOption.VERIFY_AAR_CLASSES])) {
            createVerifyLibraryClassesTask(libraryVariant)
        }

        taskFactory.register(ExtractSupportedLocalesTask.CreationAction(libraryVariant))

        createBundleTask(libraryVariant)
    }

    private fun createBundleTask(variant: LibraryCreationConfig) {
        taskFactory.register(LibraryCreationAction(variant))
        variant.taskContainer
            .assembleTask
            .configure { task: Task -> task.dependsOn(variant.artifacts.get(SingleArtifact.AAR)) }
        val publishInfo = variant.publishInfo
        if (publishInfo != null) {
            val components = publishInfo.components

            // Checks all components which the current variant is published to and see if there is
            // any component that is configured to publish source or javadoc.
            if (components.stream().anyMatch(ComponentPublishingInfo::withSourcesJar)) {
                taskFactory.register(SourceJarTask.CreationAction(variant))
            }
            if (components.stream().anyMatch(ComponentPublishingInfo::withJavadocJar)) {
                DokkaParallelBuildService.RegistrationAction(project).execute()
                taskFactory.register(JavaDocGenerationTask.CreationAction(variant))
                taskFactory.register(JavaDocJarTask.CreationAction(variant))
            }
            components.forEach {
                createComponent(
                    variant, it.componentName, it.isClassifierRequired
                )
            }
        }
    }

    override fun createJacocoTask(creationConfig: ComponentCreationConfig) {
        val jacocoTask = taskFactory.register(JacocoTask.CreationAction(creationConfig))
        creationConfig.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
            .use(jacocoTask)
            .toFork(
                type = ScopedArtifact.CLASSES,
                inputJars = { a ->  a.jarsWithIdentity.inputJars },
                inputDirectories = JacocoTask::classesDir,
                intoJarDirectory = JacocoTask::outputForJars,
                intoDirDirectory = JacocoTask::outputForDirs,
                intoType = InternalScopedArtifact.FINAL_TRANSFORMED_CLASSES
            )
    }

    private fun createComponent(
        variant: LibraryCreationConfig,
        componentName: String,
        isClassifierRequired: Boolean
    ) {
        val variantDependencies = variant.variantDependencies
        var component = project.components.findByName(componentName) as AdhocComponentWithVariants?
        if (component == null) {
            component = localConfig.componentFactory.adhoc(componentName)
            project.components.add(component)
        }
        val apiPub = variantDependencies.getElements(
            PublishedConfigSpec(PublishedConfigType.API_PUBLICATION, componentName, isClassifierRequired)
        )
        val runtimePub = variantDependencies.getElements(
            PublishedConfigSpec(PublishedConfigType.RUNTIME_PUBLICATION, componentName, isClassifierRequired)
        )
        val sourcePub = variantDependencies.getElements(
            PublishedConfigSpec(PublishedConfigType.SOURCE_PUBLICATION, componentName, isClassifierRequired)
        )
        val javaDocPub = variantDependencies.getElements(
            PublishedConfigSpec(PublishedConfigType.JAVA_DOC_PUBLICATION, componentName, isClassifierRequired)
        )
        component!!.addVariantsFromConfiguration(
            apiPub, ConfigurationVariantMapping("compile", isClassifierRequired)
        )
        component.addVariantsFromConfiguration(
            runtimePub, ConfigurationVariantMapping("runtime", isClassifierRequired)
        )
        if (sourcePub != null) {
            component.addVariantsFromConfiguration(
                sourcePub, ConfigurationVariantMapping("runtime", true)
            )
        }
        if (javaDocPub != null) {
            component.addVariantsFromConfiguration(
                javaDocPub, ConfigurationVariantMapping("runtime", true)
            )
        }
    }

    private class MergeResourceCallback(private val variant: LibraryCreationConfig) :
        TaskProviderCallback<MergeResources> {
        override fun handleProvider(taskProvider: TaskProvider<MergeResources>) {
            variant.artifacts
                .setInitialProvider(taskProvider, MergeResources::publicFile)
                .withName(SdkConstants.FN_PUBLIC_TXT)
                .on(InternalArtifactType.PUBLIC_RES)
        }
    }

    private fun createMergeResourcesTasks(variant: LibraryCreationConfig) {
        val flags: ImmutableSet<MergeResources.Flag> = if (globalConfig.namespacedAndroidResources) {
            Sets.immutableEnumSet(
                MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                MergeResources.Flag.PROCESS_VECTOR_DRAWABLES
            )
        } else {
            Sets.immutableEnumSet(
                MergeResources.Flag.PROCESS_VECTOR_DRAWABLES
            )
        }
        val callback = MergeResourceCallback(variant)

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        basicCreateMergeResourcesTask(
            creationConfig = variant,
            mergeType = MergeType.PACKAGE,
            includeDependencies = false,
            processResources = false,
            alsoOutputNotCompiledResources = false,
            flags = flags,
            taskProviderCallback = callback
        )

        // This task merges all the resources, including the dependencies of this library.
        // This should be unused, except that external libraries might consume it.
        // Also used by the VerifyLibraryResourcesTask (only ran in release builds).
        createMergeResourcesTask(variant, false /*processResources*/, ImmutableSet.of())

        // Task to generate the public.txt for the API that always exists
        // Unlike the internal one which is packaged in the AAR which only exists if the
        // developer has explicitly marked resources as public.
        taskFactory.register(GenerateApiPublicTxtTask.CreationAction(variant))
    }

    private fun createCompileLibraryResourcesTask(variant: LibraryCreationConfig) {
        if (variant.androidResourcesCreationConfig != null
            && variant.androidResourcesCreationConfig!!
                .isPrecompileDependenciesResourcesEnabled
        ) {
            taskFactory.register(CompileLibraryResourcesTask.CreationAction(variant))
        }
    }

    override fun postJavacCreation(creationConfig: ComponentCreationConfig) {
        super.postJavacCreation(creationConfig)
        taskFactory.register(ProcessLibraryArtProfileTask.CreationAction(creationConfig))

        // Create jar used for publishing to API elements (for other projects to compile against).
        taskFactory.register(
            BundleLibraryClassesJar.CreationAction(
                creationConfig, PublishedConfigType.API_ELEMENTS
            )
        )
    }

    private fun createLibraryAssetsTask(variant: LibraryCreationConfig) {
        taskFactory.register(LibraryAssetCreationAction(variant))
    }

    override val javaResMergingScopes = setOf(
        InternalScopedArtifacts.InternalScope.LOCAL_DEPS,
    )

    override fun createPrepareLintJarForPublishTask() {
        super.createPrepareLintJarForPublishTask()

        // publish the local lint.jar to all the variants.
        // This takes the global jar (output of PrepareLintJar) and publishes to each variants
        // as we don't have variant-free publishing at the moment.
        for (variant in variantPropertiesList) {
            variant.artifacts
                .copy(
                    InternalArtifactType.LINT_PUBLISH_JAR,
                    globalConfig.globalArtifacts
                )
        }
    }
}
