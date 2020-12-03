package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.DependencyResourcesComputer
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.resources.ANDROID_AAPT_IGNORE
import com.android.ide.common.resources.ResourceSet
import com.android.ide.common.resources.writeIdentifiedSourceSetsFile
import com.android.utils.FileUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Produces a file which lists project resource source set directories with an identifier.
 */
abstract class MapSourceSetPathsTask : NonIncrementalTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    @get:Optional
    abstract val generatedPngsOutputDir: Property<String>

    @get:Input
    @get:Optional
    abstract val mergeResourcesOutputDir: Property<String>

    @get:Input
    @get:Optional
    abstract val incrementalMergedDir: Property<String>

    @get:Internal
    abstract val aaptEnv: Property<String>

    @get:Internal
    lateinit var resourceComputer: DependencyResourcesComputer

    @get:Input
    abstract val resourcePaths: ListProperty<String>

    @get:OutputFile
    abstract val filepathMappingFile : RegularFileProperty

    override fun doTaskAction() {
        // As this task is executed before resource merging, the resourceComputer is not aware of
        // a number of source sets (such as generated vector drawable pngs). Therefore, these are
        // derived from merge resources.
        val uncreatedSourceSets = listOfNotNull(
                generatedPngsOutputDir.orNull,
                mergeResourcesOutputDir.orNull,
                getPathIfPresentOrNull(incrementalMergedDir, listOf("merged.dir")),
                getPathIfPresentOrNull(incrementalMergedDir, listOf("stripped.dir"))
        )
        val resourceSourceSetFolders: List<File> =
                resourcePaths.getOrElse(emptyList()).map(::File) + uncreatedSourceSets.map(::File)
        writeIdentifiedSourceSetsFile(
                resourceSourceSets = resourceSourceSetFolders,
                packageName = packageName.get(),
                projectName = projectName,
                output = filepathMappingFile.get().asFile
        )
    }

    internal class CreateAction(
            creationConfig: ComponentCreationConfig,
            val mergeResourcesTask: TaskProvider<MergeResources>) :
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
            val paths = creationConfig.paths
            val aapt = creationConfig
                    .services
                    .gradleEnvironmentProvider
                    .getEnvVariable(ANDROID_AAPT_IGNORE)
            task.aaptEnv.setDisallowChanges(aapt)
            task.resourceComputer = DependencyResourcesComputer()
            task.resourceComputer.initFromVariantScope(creationConfig, false)
            val resourceSourceSets = task.computedResourceSourceSetPaths(
                    task.resourceComputer,
                    true,
                    aapt.forUseAtConfigurationTime().orNull)
            task.dependsOn(creationConfig.services.fileCollection(resourceSourceSets))
            task.resourcePaths.setDisallowChanges(resourceSourceSets.map(File::getAbsolutePath))
            task.packageName.setDisallowChanges(creationConfig.packageName)
            if (mergeResourcesTask.isPresent) {
                val mergeResources = mergeResourcesTask.get()
                if (mergeResources.outputDir.isPresent) {
                    task.mergeResourcesOutputDir
                            .setDisallowChanges(paths.defaultMergeResourcesOutputDir.absolutePath)
                }
                if (!mergeResources.isVectorSupportLibraryUsed) {
                    mergeResources.generatedPngsOutputDir?.let {
                        task.generatedPngsOutputDir.setDisallowChanges(it.absolutePath)
                    }
                }
                if (mergeResources.incremental && mergeResources.incrementalFolder != null) {
                    mergeResources.incrementalFolder?.let {
                        task.incrementalMergedDir.setDisallowChanges(it.absolutePath)
                    }
                }
            }
        }
    }

    /** Returns a list of resource source set absolute file paths. */
    fun computedResourceSourceSetPaths(
            resComputer : DependencyResourcesComputer,
            precompileDependenciesResources : Boolean,
            aaptEnv : String?) : List<File> {
        return resComputer
                .compute(precompileDependenciesResources, aaptEnv)
                .flatMap(ResourceSet::getSourceFiles)
    }

    private fun getPathIfPresentOrNull(property: Property<String>, paths: List<String>) : String? {
        return if(property.isPresent && property.orNull != null) {
            FileUtils.join(listOf(property.get()) + paths)
        } else {
            null
        }
    }
}
