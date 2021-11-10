package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.resources.writeIdentifiedSourceSetsFile
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Produces a file which lists project resource source set directories with an identifier.
 */
@DisableCachingByDefault
abstract class MapSourceSetPathsTask : NonIncrementalTask() {

    @get:Input
    abstract val namespace: Property<String>

    @get:Nested
    abstract val sourceSetInputs: SourceSetInputs

    @get:Input
    @get:Optional
    abstract val generatedPngsOutputDir: Property<String>

    @get:Input
    @get:Optional
    val generatedResDir: Property<String>
        get() = sourceSetInputs.generatedResDir

    @get:Input
    @get:Optional
    val mergeResourcesOutputDir: Property<String>
        get() = sourceSetInputs.mergeResourcesOutputDir

    @get:Input
    @get:Optional
    val renderscriptResOutputDir: Property<String>
        get() = sourceSetInputs.renderscriptResOutputDir

    @get:Input
    @get:Optional
    val incrementalMergeDir: Property<String>
        get() = sourceSetInputs.incrementalMergedDir

    @get:Input
    val localResources: MapProperty<String, FileCollection>
        get() = sourceSetInputs.localResources

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val librarySourceSets: ConfigurableFileCollection
        get() = sourceSetInputs.librarySourceSets

    @get:OutputFile
    abstract val filepathMappingFile: RegularFileProperty

    override fun doTaskAction() {
        val uncreatedSourceSets = listOfNotNull(
            generatedPngsOutputDir.orNull,
            generatedResDir.orNull,
            renderscriptResOutputDir.orNull,
            mergeResourcesOutputDir.orNull,
        )

        writeIdentifiedSourceSetsFile(
            resourceSourceSets = sourceSetInputs.listConfigurationSourceSets(uncreatedSourceSets),
            namespace = namespace.get(),
            projectPath = projectPath.get(),
            output = filepathMappingFile.get().asFile
        )
    }

    internal class CreateAction(
        creationConfig: ComponentCreationConfig,
        val mergeResourcesTask: TaskProvider<MergeResources>,
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
            task.namespace.setDisallowChanges(creationConfig.namespace)
            task.sourceSetInputs.initialise(
                creationConfig, mergeResourcesTask.get(), includeDependencies
            )
            if (!mergeResourcesTask.get().isVectorSupportLibraryUsed) {
                task.generatedPngsOutputDir.setDisallowChanges(
                        creationConfig.paths.generatedPngsOutputDir.map { it.asFile.absolutePath }
                )
            }
        }
    }
}
