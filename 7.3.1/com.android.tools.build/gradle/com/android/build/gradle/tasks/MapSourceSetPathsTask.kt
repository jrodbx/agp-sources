package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.resources.writeIdentifiedSourceSetsFile
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
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
import java.io.File

/**
 * Produces a file which lists project resource source set directories with an identifier.
 */
@DisableCachingByDefault
abstract class MapSourceSetPathsTask : NonIncrementalTask() {

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    @get:Optional
    abstract val generatedPngsOutputDir: Property<String>

    @get:Input
    @get:Optional
    abstract val generatedResDir: Property<String>

    @get:Input
    @get:Optional
    abstract val mergeResourcesOutputDir: Property<String>

    @get:Input
    @get:Optional
    abstract val renderscriptResOutputDir: Property<String>

    @get:Input
    @get:Optional
    abstract val incrementalMergedDir: Property<String>

    @get:Input
    abstract val localResources: MapProperty<String, FileCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val librarySourceSets: ConfigurableFileCollection

    @get:Input
    abstract val allGeneratedRes: ListProperty<Collection<String>>

    @get:Input
    @get:Optional
    abstract val extraGeneratedResDir: ListProperty<String>

    @get:OutputFile
    abstract val filepathMappingFile: RegularFileProperty

    override fun doTaskAction() {
        val uncreatedSourceSets = listOfNotNull(
            generatedPngsOutputDir.orNull,
            generatedResDir.orNull,
            renderscriptResOutputDir.orNull,
            mergeResourcesOutputDir.orNull,
        )
        val generatedSourceSets = allGeneratedRes.get().flatten()

        writeIdentifiedSourceSetsFile(
            resourceSourceSets = listConfigurationSourceSets(uncreatedSourceSets, generatedSourceSets),
            namespace = namespace.get(),
            projectPath = projectPath.get(),
            output = filepathMappingFile.get().asFile
        )
    }

    private fun listConfigurationSourceSets(
        additionalSourceSets: List<String>,
        generatedSourceSets: List<String>
    ): List<File> {
        val uncreatedSourceSets = listOfNotNull(
            getPathIfPresentOrNull(incrementalMergedDir, listOf(SdkConstants.FD_MERGED_DOT_DIR)),
            getPathIfPresentOrNull(incrementalMergedDir, listOf(SdkConstants.FD_STRIPPED_DOT_DIR))
        )
        return localResources.get().values.flatMap { it.files }.asSequence()
            .plus(librarySourceSets.files)
            .plus(extraGeneratedResDir.get().map(::File))
            .plus(uncreatedSourceSets.map(::File))
            .plus(additionalSourceSets.map(::File))
            .plus(generatedSourceSets.map(::File)).toList()
    }

    private fun getPathIfPresentOrNull(property: Provider<String>, paths: List<String>): String? {
        return FileUtils.join(
            listOf(
                File(property.orNull ?: return null).absolutePath
            ) + paths
        )
    }

    internal class CreateAction(
        creationConfig: ComponentCreationConfig,
        val includeDependencies: Boolean
    ) :
        VariantTaskCreationAction<MapSourceSetPathsTask, ComponentCreationConfig>(creationConfig) {

        override val name: String = computeTaskName("map", "SourceSetPaths")

        override val type: Class<MapSourceSetPathsTask> = MapSourceSetPathsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<MapSourceSetPathsTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MapSourceSetPathsTask::filepathMappingFile
            ).withName("file-map${SdkConstants.DOT_TXT}")
                .on(InternalArtifactType.SOURCE_SET_PATH_MAP)
        }

        override fun configure(task: MapSourceSetPathsTask) {
            super.configure(task)
            task.generatedResDir.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.GENERATED_RES).map {
                    it.asFile.absolutePath
                }
            )
            task.mergeResourcesOutputDir.setDisallowChanges(
                (creationConfig.artifacts.get(InternalArtifactType.MERGED_RES)
                        as DefaultFilePropertyFactory.DefaultDirectoryVar).locationOnly.map {
                    it.asFile.absolutePath
                }
            )
            task.renderscriptResOutputDir.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES).map {
                    it.asFile.absolutePath
                }
            )
            task.extraGeneratedResDir.addAll(
                creationConfig.variantData.extraGeneratedResFolders.elements.map { allDirs ->
                    allDirs.map {
                        it.asFile.absolutePath
                    }
                }
            )
            task.incrementalMergedDir.setDisallowChanges(
                (creationConfig.artifacts.get(InternalArtifactType.MERGED_RES_INCREMENTAL_FOLDER)
                        as DefaultFilePropertyFactory.DefaultDirectoryVar).locationOnly.map {
                    it.asFile.absolutePath
                }
            )
            task.namespace.setDisallowChanges(creationConfig.namespace)
            if (includeDependencies) {
                task.librarySourceSets.setFrom(
                    creationConfig.variantDependencies.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.ANDROID_RES
                    ).artifactFiles
                )
            }
            task.allGeneratedRes.setDisallowChanges(creationConfig.sources.res.getVariantSources().map { allRes ->
                allRes.map { directoryEntries ->
                    directoryEntries.directoryEntries
                        .filter { it.isGenerated }
                        .map { it.asFiles(task.project.objects::directoryProperty) }
                        .map { it.get().asFile.absolutePath }
                }
            })
            task.localResources.setDisallowChanges(
                creationConfig.sources.res.getLocalSourcesAsFileCollection()
            )
            if (creationConfig.vectorDrawables.useSupportLibrary == false) {
                task.generatedPngsOutputDir.setDisallowChanges(
                    (creationConfig.artifacts.get(InternalArtifactType.GENERATED_PNGS_RES)
                            as DefaultFilePropertyFactory.DefaultDirectoryVar).locationOnly.map {
                        it.asFile.absolutePath
                    }
                )
            }
        }
    }
}
