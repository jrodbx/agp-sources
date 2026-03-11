/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.dependency.PluginDependencies
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.tasks.MergeJavaResWorkAction.SourcedInput
import com.android.build.gradle.internal.tasks.creationconfig.ProcessJavaResCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.tasks.getChangesInSerializableForm
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.files.SerializableInputChanges
import java.io.Serializable
import java.util.function.Predicate
import javax.inject.Inject
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

interface MergeJavaResourcesInputsOutputs {

  @get:InputFiles @get:Incremental @get:PathSensitive(PathSensitivity.RELATIVE) @get:Optional val projectJavaRes: ConfigurableFileCollection

  @get:Nested val subProjectJavaRes: OptionalCollectionWithProvenance

  @get:Nested val externalLibJavaRes: OptionalCollectionWithProvenance

  @get:Classpath @get:Incremental @get:Optional val localDepsJavaRes: ConfigurableFileCollection

  @get:Nested val featureJavaRes: OptionalCollectionWithProvenance

  @get:Input val mergeScopes: SetProperty<InternalScopedArtifacts.InternalScope>

  @get:Input val excludes: SetProperty<String>

  @get:Input val pickFirsts: SetProperty<String>

  @get:Input val merges: SetProperty<String>

  @get:Input @get:Optional val noCompress: ListProperty<String>

  @get:Internal val intermediateDir: DirectoryProperty

  @get:OutputDirectory val cacheDir: DirectoryProperty

  @get:Internal val incrementalStateFile: RegularFileProperty

  @get:OutputFile val outputFile: RegularFileProperty

  @get:Internal val hasIncludedBuilds: Property<Boolean>
}

/** Variant Task to merge java resources from multiple modules */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.JAVA_RESOURCES, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergeJavaResourceTask : MergeJavaResourcesInputsOutputs, NewIncrementalTask() {

  override fun doTaskAction(inputChanges: InputChanges) = runTaskAction(inputChanges, this, this)

  class CreationAction(
    private val mergeScopes: Set<InternalScopedArtifacts.InternalScope>,
    private val packaging: Packaging,
    creationConfig: ComponentCreationConfig,
  ) : VariantTaskCreationAction<MergeJavaResourceTask, ComponentCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("merge", "JavaResource")

    override val type: Class<MergeJavaResourceTask>
      get() = MergeJavaResourceTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<MergeJavaResourceTask>) {
      super.handleProvider(taskProvider)
      val fileName =
        if (creationConfig.componentType.isBaseModule) {
          "base.jar"
        } else {
          TaskManager.getFeatureFileName(creationConfig.services.projectInfo.path, SdkConstants.DOT_JAR)
        }
      creationConfig.artifacts
        .setInitialProvider(taskProvider, MergeJavaResourceTask::outputFile)
        .withName(fileName)
        .on(InternalArtifactType.MERGED_JAVA_RES)
    }

    override fun configure(task: MergeJavaResourceTask) {
      super.configure(task)

      // Add all JAVA_RES artifacts that were added through the variant API, using
      // the ScopedArtifact APIs. This will not include all generated or static
      // directories that are added to the variant's sources interface.
      task.projectJavaRes.from(creationConfig.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).getFinalArtifacts(ScopedArtifact.JAVA_RES))

      // Add the copied source files which will include the source set folders as
      // well as all the generated or static directories added through the variant's
      // Sources APIs.
      task.projectJavaRes.fromDisallowChanges(creationConfig.artifacts.get(JAVA_RES))

      run {
        if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)) {
          task.subProjectJavaRes.setDisallowChanges(
            creationConfig.variantDependencies.getArtifactCollection(
              AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
              AndroidArtifacts.ArtifactScope.PROJECT,
              AndroidArtifacts.ArtifactType.JAVA_RES,
            )
          )
        }
        task.subProjectJavaRes.disallowChanges()

        if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)) {
          task.externalLibJavaRes.set(
            creationConfig.variantDependencies.getArtifactCollection(
              AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
              AndroidArtifacts.ArtifactScope.EXTERNAL,
              AndroidArtifacts.ArtifactType.JAVA_RES,
            )
          )
        } else if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.LOCAL_DEPS)) {
          task.localDepsJavaRes.from(creationConfig.computeLocalPackagedJars())
        }
        task.externalLibJavaRes.disallowChanges()
        task.localDepsJavaRes.disallowChanges()
      }

      if (mergeScopes.contains(InternalScopedArtifacts.InternalScope.FEATURES)) {
        task.featureJavaRes.set(
          creationConfig.variantDependencies.getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.REVERSE_METADATA_JAVA_RES,
          )
        )
      }
      task.featureJavaRes.disallowChanges()

      task.mergeScopes.addAll(mergeScopes)
      task.mergeScopes.disallowChanges()
      task.excludes.setDisallowChanges(packaging.resources.excludes)
      task.pickFirsts.setDisallowChanges(packaging.resources.pickFirsts)
      task.merges.setDisallowChanges(packaging.resources.merges)
      val intermediatesDir = creationConfig.paths.getIncrementalDir("${creationConfig.name}-mergeJavaRes")
      task.intermediateDir.set(intermediatesDir)
      task.cacheDir.set(intermediatesDir.resolve("zip-cache"))
      task.incrementalStateFile.set(intermediatesDir.resolve("merge-state"))
      if (creationConfig is ApkCreationConfig) {
        task.noCompress.set(creationConfig.androidResources.noCompress)
      }
      task.noCompress.disallowChanges()

      configureHasIncludedBuilds(task.project.gradle, task)
    }
  }

  companion object {

    private val excludedFileSuffixes = listOf(SdkConstants.DOT_CLASS, SdkConstants.DOT_NATIVE_LIBS)

    // predicate logic must match patternSet logic below
    val predicate = Predicate<String> { path -> excludedFileSuffixes.none { path.endsWith(it) } }

    // patternSet logic must match predicate logic above
    val patternSet: PatternSet
      get() {
        val patternSet = PatternSet()
        excludedFileSuffixes.forEach { patternSet.exclude("**/*$it") }
        return patternSet
      }
  }
}

/*
 * Global task to merge java resources from multiple modules
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.JAVA_RESOURCES, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergeJavaResourcesGlobalTask : MergeJavaResourcesInputsOutputs, NewIncrementalGlobalTask() {

  override fun doTaskAction(inputChanges: InputChanges) = runTaskAction(inputChanges, this, this)

  abstract class CommonCreationAction(
    val artifacts: ArtifactsImpl,
    val dependencies: PluginDependencies,
    val projectLayout: ProjectLayout,
    val packaging: Packaging? = null,
  ) : GlobalTaskCreationAction<MergeJavaResourcesGlobalTask>() {

    override val name: String
      get() = "mergeLibraryJavaResources"

    override val type: Class<MergeJavaResourcesGlobalTask>
      get() = MergeJavaResourcesGlobalTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<MergeJavaResourcesGlobalTask>) {
      super.handleProvider(taskProvider)

      artifacts
        .setInitialProvider(taskProvider, MergeJavaResourcesGlobalTask::outputFile)
        .withName("base.jar")
        .on(FusedLibraryInternalArtifactType.MERGED_JAVA_RES)
    }

    override fun configure(task: MergeJavaResourcesGlobalTask) {
      super.configure(task)

      task.subProjectJavaRes.set(
        dependencies.getArtifactCollection(AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH, AndroidArtifacts.ArtifactType.JAVA_RES)
      )

      if (packaging != null) {
        task.excludes.setDisallowChanges(packaging.resources.excludes)
        task.pickFirsts.setDisallowChanges(packaging.resources.pickFirsts)
        task.merges.setDisallowChanges(packaging.resources.merges)
      } else {
        task.excludes.setDisallowChanges(defaultExcludes)
        task.pickFirsts.setDisallowChanges(emptySet())
        task.merges.setDisallowChanges(emptySet())
      }

      val mergeJavaResDir = projectLayout.buildDirectory.dir(SdkConstants.FD_INTERMEDIATES).map { it.dir("mergeJavaRes") }
      task.intermediateDir.set(mergeJavaResDir)
      task.cacheDir.set(mergeJavaResDir.map { it.dir("zip-cache") })
      task.incrementalStateFile.set(mergeJavaResDir.map { it.file("merge-state") })

      // External libraries can just be consumed via the subProjectJavaRes (the inputs are
      // only intended for finer grain incremental runs.)
      task.externalLibJavaRes.disallowChanges()

      // No sources in fused library projects, so none of the below need set.
      task.projectJavaRes.disallowChanges()
      task.featureJavaRes.disallowChanges()

      configureHasIncludedBuilds(task.project.gradle, task)
    }
  }

  class FusedLibraryCreationAction(private val creationConfig: FusedLibraryGlobalScope) :
    CommonCreationAction(creationConfig.artifacts, creationConfig.dependencies, creationConfig.projectLayout, creationConfig.packaging)
}

private fun runTaskAction(inputChanges: InputChanges, inputOutputs: MergeJavaResourcesInputsOutputs, baseTask: BaseTask) {
  fun doFullTaskAction() {
    with(inputOutputs) {
      baseTask.workerExecutor.noIsolation().submit(MergeJavaResWorkAction::class.java) {
        it.initializeFromBaseTask(baseTask)
        it.projectJavaRes.from(projectJavaRes)
        it.subProjectJavaRes.set(subProjectJavaRes.toSourcedInputs(hasIncludedBuilds.get()))
        it.externalLibJavaRes.set(externalLibJavaRes.toSourcedInputs(hasIncludedBuilds.get()) + localDepsJavaRes.toSourcedInputs())
        it.featureJavaRes.set(featureJavaRes.toSourcedInputs(hasIncludedBuilds.get()))
        it.outputFile.set(outputFile)
        it.incrementalStateFile.set(incrementalStateFile)
        it.incremental.set(false)
        it.cacheDir.set(cacheDir)
        it.noCompress.set(noCompress)
        it.excludes.set(excludes)
        it.pickFirsts.set(pickFirsts)
        it.merges.set(merges)
      }
    }
  }

  fun doIncrementalTaskAction(changedInputs: SerializableInputChanges) {
    with(inputOutputs) {
      if (!incrementalStateFile.get().asFile.isFile) {
        doFullTaskAction()
        return
      }
      baseTask.workerExecutor.noIsolation().submit(MergeJavaResWorkAction::class.java) {
        it.initializeFromBaseTask(baseTask)
        it.projectJavaRes.from(projectJavaRes)
        it.subProjectJavaRes.set(subProjectJavaRes.toSourcedInputs(hasIncludedBuilds.get()))
        it.externalLibJavaRes.set(externalLibJavaRes.toSourcedInputs(hasIncludedBuilds.get()) + localDepsJavaRes.toSourcedInputs())
        it.featureJavaRes.set(featureJavaRes.toSourcedInputs(hasIncludedBuilds.get()))
        it.outputFile.set(outputFile)
        it.incrementalStateFile.set(incrementalStateFile)
        it.incremental.set(true)
        it.cacheDir.set(cacheDir)
        it.changedInputs.set(changedInputs)
        it.noCompress.set(noCompress)
        it.excludes.set(excludes)
        it.pickFirsts.set(pickFirsts)
        it.merges.set(merges)
      }
    }
  }
  if (inputChanges.isIncremental) {
    // TODO(b/225872980): Unify with IncrementalChanges.classpathToRelativeFileSet
    //  (see IncrementalMergerFileUtils.collectChanges)
    doIncrementalTaskAction(
      with(inputOutputs) {
        listOf(
            inputChanges.getChangesInSerializableForm(projectJavaRes),
            inputChanges.getChangesInSerializableForm(subProjectJavaRes.fileCollection),
            inputChanges.getChangesInSerializableForm(externalLibJavaRes.fileCollection),
            inputChanges.getChangesInSerializableForm(featureJavaRes.fileCollection),
          )
          .let {
            SerializableInputChanges(
              roots = it.flatMap(SerializableInputChanges::roots),
              changes = it.flatMap(SerializableInputChanges::changes),
            )
          }
      }
    )
  } else {
    doFullTaskAction()
  }
}

/**
 * Wrapper around an [ArtifactCollection] that's optional.
 *
 * There are cases where the collection is optional and not set. This makes it cumbersome to hold the artifact collection and compute
 * on-demand the fileCollection from it. Instead, we have to set the file collection manually if the collection is set.
 *
 * While this is not a problem on the declaration side, it makes the setup side more awkward and error-prone. Therefore, this class attempts
 * to encapsulate all this in a single place so that we can have all the instance behave the same reliably.
 */
abstract class OptionalCollectionWithProvenance @Inject constructor(objects: ObjectFactory) : Serializable {

  @get:Internal val artifactCollection: Property<ArtifactCollection> = objects.property(ArtifactCollection::class.java)

  @get:Classpath @get:Incremental @get:Optional val fileCollection: ConfigurableFileCollection = objects.fileCollection()

  internal fun set(collection: ArtifactCollection) {
    artifactCollection.set(collection)
    // We cannot have the fileCollection be a getter that just queries the
    // artifact collection in case it's not set.
    fileCollection.from(collection.artifactFiles)
  }

  internal fun disallowChanges() {
    artifactCollection.disallowChanges()
    fileCollection.disallowChanges()
  }

  internal fun setDisallowChanges(collection: ArtifactCollection) {
    set(collection)
    disallowChanges()
  }

  internal fun toSourcedInputs(displayBuildInfo: Boolean): List<SourcedInput> {
    if (!artifactCollection.isPresent) {
      return listOf()
    }

    return artifactCollection.get().artifacts.map {
      val owner = it.variant.owner

      val name =
        when (owner) {
          is ModuleComponentIdentifier ->
            // Add a file-specific name so the file merger can distinguish between multiple
            // files in one artifact (b/377366954)
            "${owner.group}:${owner.module}:${owner.version}" + "/${it.file.name}"
          is ProjectComponentIdentifier -> {
            if (displayBuildInfo) {
              "project(\"${owner.projectPath}\") - Build: ${owner.build.buildPath}"
            } else {
              "project(\"${owner.projectPath}\")"
            }
          }
          else -> owner.displayName + "/${it.file.name}"
        }
      SourcedInput(it.file, name)
    }
  }
}

private fun ConfigurableFileCollection.toSourcedInputs(): List<SourcedInput> = map { SourcedInput(it, it.absolutePath) }

private fun configureHasIncludedBuilds(gradle: Gradle, task: MergeJavaResourcesInputsOutputs) {
  val gradle = gradle.parent ?: gradle
  task.hasIncludedBuilds.setDisallowChanges(gradle.includedBuilds.isNotEmpty())
}

fun projectHasAnnotationProcessors(creationConfig: ProcessJavaResCreationConfig): Boolean {
  val config = creationConfig.annotationProcessorConfiguration
  return config != null && config.incoming.dependencies.isNotEmpty()
}
