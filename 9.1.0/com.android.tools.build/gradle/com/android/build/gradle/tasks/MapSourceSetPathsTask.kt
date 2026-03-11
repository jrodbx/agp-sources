package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.AarCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationActionImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.resources.writeIdentifiedSourceSetsFile
import com.android.utils.FileUtils
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/** Produces a file which lists project resource source set directories with an identifier. */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class MapSourceSetPathsTask : NonIncrementalTask() {

  @get:Input abstract val namespace: Property<String>

  @get:Input @get:Optional abstract val generatedLocaleIncrementalDir: Property<String>

  @get:Input @get:Optional abstract val generatedPngsOutputDir: Property<String>

  @get:Input @get:Optional abstract val mergedNotCompiledDir: Property<String>

  @get:Input @get:Optional abstract val packagedResDir: Property<String>

  @get:Input @get:Optional abstract val navigationUpdatedFolder: Property<String>

  @get:Input @get:Optional abstract val generatedResDir: Property<String>

  @get:Input @get:Optional abstract val mergeResourcesOutputDir: Property<String>

  @get:Input @get:Optional abstract val renderscriptResOutputDir: Property<String>

  @get:Input @get:Optional abstract val incrementalMergedDir: Property<String>

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val localResources: ConfigurableFileCollection

  @get:Input abstract val allGeneratedRes: ListProperty<String>

  @get:OutputFile abstract val filepathMappingFile: RegularFileProperty

  override fun doTaskAction() {
    val uncreatedSourceSets =
      listOfNotNull(
        generatedLocaleIncrementalDir.orNull,
        generatedPngsOutputDir.orNull,
        generatedResDir.orNull,
        renderscriptResOutputDir.orNull,
        mergeResourcesOutputDir.orNull,
        mergedNotCompiledDir.orNull,
        navigationUpdatedFolder.orNull,
      )
    writeIdentifiedSourceSetsFile(
      resourceSourceSets = listConfigurationSourceSets(uncreatedSourceSets, allGeneratedRes.get()),
      namespace = namespace.get(),
      projectPath = projectPath.get(),
      output = filepathMappingFile.get().asFile,
    )
  }

  private fun listConfigurationSourceSets(additionalSourceSets: List<String>, generatedSourceSets: List<String>): List<File> {
    val uncreatedSourceSets =
      listOfNotNull(
        getPathIfPresentOrNull(incrementalMergedDir, listOf(SdkConstants.FD_MERGED_DOT_DIR)),
        getPathIfPresentOrNull(incrementalMergedDir, listOf(SdkConstants.FD_STRIPPED_DOT_DIR)),
        getPathIfPresentOrNull(packagedResDir, emptyList()),
      )
    return localResources.files
      .asSequence()
      .plus(uncreatedSourceSets.map(::File))
      .plus(additionalSourceSets.map(::File))
      .plus(generatedSourceSets.map(::File))
      .toList()
  }

  private fun getPathIfPresentOrNull(property: Provider<String>, paths: List<String>): String? {
    return FileUtils.join(listOf(File(property.orNull ?: return null).absolutePath) + paths)
  }

  internal class CreateAction(creationConfig: ComponentCreationConfig) :
    VariantTaskCreationAction<MapSourceSetPathsTask, ComponentCreationConfig>(creationConfig),
    AndroidResourcesTaskCreationAction by AndroidResourcesTaskCreationActionImpl(creationConfig) {

    override val name: String = computeTaskName("map", "SourceSetPaths")

    override val type: Class<MapSourceSetPathsTask> = MapSourceSetPathsTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<MapSourceSetPathsTask>) {
      super.handleProvider(taskProvider)
      creationConfig.artifacts
        .setInitialProvider(taskProvider, MapSourceSetPathsTask::filepathMappingFile)
        .withName("file-map${SdkConstants.DOT_TXT}")
        .on(InternalArtifactType.ANDROID_RES_SOURCE_SET_PATH_MAP)
    }

    override fun configure(task: MapSourceSetPathsTask) {
      super.configure(task)
      task.generatedResDir.setDisallowChanges(
        creationConfig.artifacts.get(InternalArtifactType.GENERATED_RES).map { it.asFile.absolutePath }
      )
      task.mergeResourcesOutputDir.setDisallowChanges(
        (creationConfig.artifacts.get(InternalArtifactType.MERGED_RES) as FileSystemLocationProperty).locationOnly.map {
          it.asFile.absolutePath
        }
      )
      task.renderscriptResOutputDir.setDisallowChanges(
        creationConfig.artifacts.get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES).map { it.asFile.absolutePath }
      )

      task.incrementalMergedDir.setDisallowChanges(
        (creationConfig.artifacts.get(InternalArtifactType.MERGED_RES_INCREMENTAL_FOLDER) as FileSystemLocationProperty).locationOnly.map {
          it.asFile.absolutePath
        }
      )
      task.mergedNotCompiledDir.setDisallowChanges(
        (creationConfig.artifacts.get(InternalArtifactType.MERGED_NOT_COMPILED_RES) as FileSystemLocationProperty).locationOnly.map {
          it.asFile.absolutePath
        }
      )
      task.generatedLocaleIncrementalDir.setDisallowChanges(
        (creationConfig.artifacts.get(InternalArtifactType.GENERATED_LOCALE_CONFIG_INCREMENTAL_DIR) as FileSystemLocationProperty)
          .locationOnly
          .map { it.asFile.absolutePath }
      )

      task.namespace.setDisallowChanges(creationConfig.namespace)

      if (creationConfig is AarCreationConfig) {
        task.packagedResDir.setDisallowChanges(
          (creationConfig.artifacts.get(InternalArtifactType.PACKAGED_RES) as FileSystemLocationProperty).locationOnly.map {
            it.asFile.absolutePath
          }
        )
      }

      creationConfig.sources.res { resSources ->
        val list = task.project.objects.listProperty(Directory::class.java)
        resSources.getVariantSources().forEach { directoryEntries ->
          directoryEntries.directoryEntries
            .filter { it.isGenerated }
            .forEach { directoryEntry -> directoryEntry.addTo(task.project.layout.projectDirectory, list) }
        }
        task.allGeneratedRes.addAll(list.map { it.map { directory -> directory.asFile.absolutePath } })

        creationConfig.oldVariantApiLegacySupport?.registerPostOldVariantApiAction {
          resSources.getVariantSourcesWithFilter { !it.isUserAdded && !it.isGenerated }.values.forEach { task.localResources.from(it) }
          task.localResources.disallowChanges()
        }
      }
      task.allGeneratedRes.disallowChanges()
      if (androidResourcesCreationConfig.vectorDrawables.useSupportLibrary == false) {
        task.generatedPngsOutputDir.setDisallowChanges(
          (creationConfig.artifacts.get(InternalArtifactType.GENERATED_PNGS_RES) as FileSystemLocationProperty).locationOnly.map {
            it.asFile.absolutePath
          }
        )
      }

      task.navigationUpdatedFolder.setDisallowChanges(
        (creationConfig.artifacts.get(InternalArtifactType.UPDATED_NAVIGATION_XML) as FileSystemLocationProperty).locationOnly.map {
          it.asFile.absolutePath
        }
      )
    }
  }
}
