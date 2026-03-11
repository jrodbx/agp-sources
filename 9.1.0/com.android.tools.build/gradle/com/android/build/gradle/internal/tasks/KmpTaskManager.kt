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

import com.android.SdkConstants
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.component.impl.KmpHostTestImpl
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.AndroidTestTaskManager
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.UnitTestTaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
import com.android.build.gradle.internal.component.TaskCreationConfig
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.plugins.LINT_PLUGIN_ID
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.ConvertShrunkResourcesToBinaryTask
import com.android.build.gradle.internal.res.GenerateApiPublicTxtTask
import com.android.build.gradle.internal.res.GenerateEmptyResourceFilesTask
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.R8MaxParallelTasksBuildService
import com.android.build.gradle.internal.tasks.LibraryJniLibsTask.ProjectOnlyCreationAction
import com.android.build.gradle.internal.tasks.creationconfig.ProcessJavaResCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.tasks.factory.registerTask
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.BundleAar
import com.android.build.gradle.tasks.CompileLibraryResourcesTask
import com.android.build.gradle.tasks.ExtractAnnotations
import com.android.build.gradle.tasks.MapSourceSetPathsTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.ProcessLibraryArtProfileTask
import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.android.build.gradle.tasks.ZipMergingTask
import com.android.builder.core.ComponentTypeImpl
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile

class KmpTaskManager(project: Project, global: GlobalTaskCreationConfig) : TaskManager(project, global) {

  override val javaResMergingScopes = setOf(InternalScopedArtifacts.InternalScope.LOCAL_DEPS)

  private val lintTaskManager = LintTaskManager(globalConfig, taskFactory, project)

  val canParseManifest = project.objects.property(Boolean::class.java)
  var hasCreatedTasks = false

  fun createTasks(project: Project, variant: KmpCreationConfig, unitTest: KmpHostTestImpl?, androidTest: KmpAndroidTestImpl?) {
    createMainVariantTasks(project, variant)
    unitTest?.let { createUnitTestTasks(project, unitTest) }
    androidTest?.let { createAndroidTestTasks(project, androidTest) }

    taskFactory.register(PrepareLintJarForPublish.CreationAction(globalConfig))
    variant.artifacts.copy(InternalArtifactType.LINT_PUBLISH_JAR, globalConfig.globalArtifacts)

    taskFactory.register(globalConfig.taskNames.compileLintChecks) { task: Task -> task.dependsOn(globalConfig.localCustomLintChecks) }

    variant.publishBuildArtifacts()

    canParseManifest.set(true)
    hasCreatedTasks = true
  }

  private fun createMainVariantTasks(project: Project, variant: KmpCreationConfig) {
    createAnchorTasks(project, variant)
    maybeCreateJavacTask(variant)
    createMergeJniLibFoldersTasks(variant)
    taskFactory.register(ProjectOnlyCreationAction(variant, InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI))

    if (variant.buildFeatures.androidResources) {
      taskFactory.register(MapSourceSetPathsTask.CreateAction(variant))

      createPackageResourcesTask(
        creationConfig = variant,
        taskProviderCallback =
          object : TaskProviderCallback<MergeResources> {
            override fun handleProvider(taskProvider: TaskProvider<MergeResources>) {
              variant.artifacts
                .setInitialProvider<RegularFile, MergeResources>(taskProvider) { obj: MergeResources -> obj.publicFile }
                .withName(SdkConstants.FN_PUBLIC_TXT)
                .on(InternalArtifactType.PUBLIC_RES)
            }
          },
      )
      registerParseLibraryResourcesTask(variant)
      createCompileLibraryResourcesTask(variant)
      taskFactory.register(GenerateLibraryRFileTask.CreationAction(variant))

      // Task to generate the public.txt for the API that always exists
      // Unlike the internal one which is packaged in the AAR which only exists if the
      // developer has explicitly marked resources as public.
      taskFactory.register(GenerateApiPublicTxtTask.CreationAction(variant))

      // tasks to package assets in the library aar
      variant.taskContainer.assetGenTask = taskFactory.register(variant.computeTaskNameInternal("generate", "Assets"))
      taskFactory.register(MergeSourceSetFolders.MergeAssetCreationAction(variant, false))
    }

    project.tasks.registerTask(
      BundleLibraryClassesJar.KotlinMultiplatformCreationAction(
        component = variant,
        publishedType = AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS,
      )
    )
    project.tasks.registerTask(
      BundleLibraryClassesJar.KotlinMultiplatformCreationAction(
        component = variant,
        publishedType = AndroidArtifacts.PublishedConfigType.API_ELEMENTS,
      )
    )

    project.tasks.registerTask(BundleLibraryClassesDir.KotlinMultiplatformCreationAction(variant))

    // Create a task to generate empty/mock required resource artifacts.
    project.tasks.registerTask(GenerateEmptyResourceFilesTask.CreateAction(variant))

    // Add a task to merge or generate the manifest
    project.tasks.registerTask(ProcessLibraryManifest.CreationAction(variant, targetSdkVersion = null, maxSdkVersion = variant.maxSdk))

    val taskConfig =
      object : ProcessJavaResCreationConfig, TaskCreationConfig by variant {
        override val extraClasses: Collection<FileCollection>
          get() = listOf(variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.CLASSES))

        override val useBuiltInKotlinSupport: Boolean
          get() = variant.useBuiltInKotlinSupport

        override val packageJacocoRuntime: Boolean
          get() = (variant as? ApkCreationConfig)?.packageJacocoRuntime == true

        override val annotationProcessorConfiguration: Configuration?
          get() = variant.variantDependencies.annotationProcessorConfiguration

        override val sources: FlatSourceDirectoriesImpl?
          get() = variant.sources.resources

        override fun setJavaResTask(task: TaskProvider<out Sync>) {
          variant.taskContainer.processJavaResourcesTask = task
        }
      }
    project.tasks.registerTask(ProcessJavaResTask.CreationAction(taskConfig))
    project.tasks.registerTask(MergeJavaResourceTask.CreationAction(javaResMergingScopes, variant.packaging, variant))

    taskFactory.register(ProcessLibraryArtProfileTask.CreationAction(variant))

    if (variant.optimizationCreationConfig.minifiedEnabled) {
      project.tasks.registerTask(GenerateLibraryProguardRulesTask.CreationAction(variant))
      R8MaxParallelTasksBuildService.RegistrationAction(project, variant.services.projectOptions).execute()
      project.tasks.registerTask(R8Task.CreationAction(variant, isTestApplication = false, addCompileRClass = false))
      if ((variant as? ApplicationCreationConfig)?.runResourceShrinking() == true) {
        // Also convert shrunk resources from proto format to binary format so it can be
        // included in an APK
        taskFactory.register(ConvertShrunkResourcesToBinaryTask.CreationAction(variant))
      }
    }

    // Publishing consumer proguard rules is an opt-in feature for KMP
    if (globalConfig.publishConsumerProguardRules) {
      project.tasks.registerTask(MergeGeneratedProguardFilesCreationAction(variant))
      project.tasks.registerTask(MergeConsumerProguardFilesTask.CreationAction(variant))
      project.tasks.registerTask(ExportConsumerProguardFilesTask.CreationAction(variant))
    }

    // Some versions of retrolambda remove the actions from the extract annotations task.
    // TODO: remove this hack once tests are moved to a version that doesn't do this
    // b/37564303
    if (variant.services.projectOptions[BooleanOption.ENABLE_EXTRACT_ANNOTATIONS]) {
      taskFactory.register(ExtractAnnotations.CreationAction(variant))
    }

    if (variant.requiresJacocoTransformation) {
      val jacocoTask = project.tasks.registerTask(JacocoTask.CreationAction(variant))
      variant.artifacts
        .forScope(ScopedArtifacts.Scope.PROJECT)
        .use(jacocoTask)
        .toFork(
          type = ScopedArtifact.CLASSES,
          inputJars = { a -> a.jarsWithIdentity.inputJars },
          inputDirectories = JacocoTask::classesDir,
          intoJarDirectory = JacocoTask::outputForJars,
          intoDirDirectory = JacocoTask::outputForDirs,
          intoType = InternalScopedArtifact.FINAL_TRANSFORMED_CLASSES,
        )
    }

    // Add a task to create the AAR metadata file
    project.tasks.registerTask(AarMetadataTask.CreationAction(variant))

    // Add a task to check the AAR metadata file
    createCheckAarMetadataTask(variant)

    // Create a jar with both classes and java resources.  This artifact is not
    // used by the Android application plugin and the task usually don't need to
    // be executed.  The artifact is useful for other Gradle users who needs the
    // 'jar' artifact as API dependency.
    project.tasks.registerTask(ZipMergingTask.CreationAction(variant))

    // now add a task that will take all the classes and java resources and package them
    // into the main and secondary jar files that goes in the AAR.
    // This is used for building the AAR.
    project.tasks.registerTask(LibraryAarJarsTask.CreationAction(variant, variant.optimizationCreationConfig.minifiedEnabled))

    // Create lint tasks here only if the lint standalone plugin is applied (to avoid
    // Android-specific behavior)
    if (project.plugins.hasPlugin(LINT_PLUGIN_ID)) {
      globalConfig.lintOptions?.let { lintOptions ->
        lintTaskManager.createLintTasks(
          ComponentTypeImpl.KMP_ANDROID,
          variant.name,
          listOf(variant),
          testComponentPropertiesList = emptyList(),
          isPerComponent = true,
        )
      }
    }

    project.tasks.registerTask(BundleAar.KotlinMultiplatformLocalLintCreationAction(variant))

    project.tasks.registerTask(BundleAar.KotlinMultiplatformCreationAction(variant))
    variant.taskContainer.assembleTask.configure { task: Task -> task.dependsOn(variant.artifacts.get(SingleArtifact.AAR)) }

    project.tasks.named("assemble").dependsOn(variant.taskContainer.assembleTask)
  }

  private fun createCompileLibraryResourcesTask(variant: KmpCreationConfig) {
    if (
      variant.androidResourcesCreationConfig != null && variant.androidResourcesCreationConfig!!.isPrecompileDependenciesResourcesEnabled
    ) {
      taskFactory.register(CompileLibraryResourcesTask.CreationAction(variant))
    }
  }

  private fun createPackageResourcesTask(creationConfig: KmpCreationConfig, taskProviderCallback: TaskProviderCallback<MergeResources>) {
    val mergeResourcesTask: TaskProvider<MergeResources> =
      project.tasks.registerTask(MergeResources.KotlinMultiplatformCreationAction(creationConfig), null, null, taskProviderCallback)

    creationConfig.androidKotlinCompilation.compileTaskProvider.dependsOn(mergeResourcesTask)
  }

  private fun createUnitTestTasks(project: Project, component: KmpHostTestImpl) {
    createAnchorTasks(project, component, false)

    maybeCreateJavacTask(component)

    with(UnitTestTaskManager(project, globalConfig)) {
      createTopLevelTasks()
      createTasks(component)
    }
  }

  private fun createAndroidTestTasks(project: Project, component: KmpAndroidTestImpl) {
    createAnchorTasks(project, component, false)

    maybeCreateJavacTask(component)

    with(AndroidTestTaskManager(project, globalConfig)) {
      createTopLevelTasks()
      createTasks(component)
    }
  }

  private fun createAnchorTasks(project: Project, component: ComponentCreationConfig, createPreBuildTask: Boolean = true) {
    project.tasks.registerTask(
      component.computeTaskNameInternal("assemble"),
      Task::class.java,
      null,
      object : TaskConfigAction<Task> {
        override fun configure(task: Task) {
          task.description = "Assembles main Android output for variant <${component.name}>"
        }
      },
      object : TaskProviderCallback<Task> {
        override fun handleProvider(taskProvider: TaskProvider<Task>) {
          component.taskContainer.assembleTask = taskProvider
        }
      },
    )

    if (createPreBuildTask) {
      project.tasks.registerTask(PreBuildCreationAction(component))
      createDependencyStreams(component)
    }
  }

  private fun maybeCreateJavacTask(component: KmpComponentCreationConfig) {
    if (!component.withJava) {
      return
    }
    val javacTask = createJavacTask(component)
    component.androidKotlinCompilation.compileTaskProvider.configure { kotlincTask ->
      component.sources.java { (kotlincTask as AbstractKotlinCompile<*>).source(it.getAsFileTrees()) }
    }

    javacTask.configure { javaCompile -> javaCompile.classpath += component.androidKotlinCompilation.output.classesDirs }
  }
}
