/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.featuresplit

import com.android.build.gradle.internal.attributes.VariantAttr
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.ide.dependencies.getIdString
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier

private val aarOrJarType = Action { container: AttributeContainer ->
    container.attribute(ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.AAR_OR_JAR.type)
}

/** Task to write the list of transitive dependencies.  */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
abstract class PackagedDependenciesWriterTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private lateinit var runtimeAarOrJarDeps: ArtifactCollection

    /**
     * Deduplication of entries is needed because Gradle resolves to two (or more)
     * ResolvedArtifactResult pointing to the same id and file but different variants
     *
     * This leads to an error in dynamic feature modules when running :app:checkDebugLibraries
     *
     * See b/247843123, b/246529493
     * See https://github.com/gradle/gradle/issues/23604
     */
    @get:Input
    val content: List<String>
       get() = runtimeAarOrJarDeps.map { it.toIdString() }.distinct().sorted()

    // the list of packaged dependencies by transitive dependencies.
    private lateinit var transitivePackagedDeps : ArtifactCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val transitivePackagedDepsFC : FileCollection
        get() = transitivePackagedDeps.artifactFiles

    @get:Input
    abstract val currentProjectIds: ListProperty<String>

    override fun doTaskAction() {
        val apkFilters = mutableSetOf<String>()
        val contentFilters = mutableSetOf<String>()
        // load the transitive information and remove from the full content.
        // We know this is correct because this information is also used in
        // FilteredArtifactCollection to remove this content from runtime-based ArtifactCollection.
        // However since we directly use the Configuration here, we have to manually remove it.
        for (transitiveDep in transitivePackagedDeps) {
            // register the APK that generated this list to remove it from our list
            apkFilters.add(transitiveDep.toIdString())
            // read its packaged content to also remove it.
            val lines = transitiveDep.file.readLines()
            contentFilters.addAll(lines)
        }

        val contentWithProject = content + currentProjectIds.get()

        // compute the overall content
        val filteredContent =
            contentWithProject.filter {
                !apkFilters.contains(it) && !contentFilters.contains(it)
            }.sorted()

        val asFile = outputFile.get().asFile
        FileUtils.mkdirs(asFile.parentFile)
        asFile.writeText(Joiner.on(System.lineSeparator()).join(filteredContent))
    }

    /**
     * Action to create the task that generates the transitive dependency list to be consumed by
     * other modules.
     *
     * This cannot depend on preBuild as it would introduce a dependency cycle.
     */
    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<PackagedDependenciesWriterTask, ComponentCreationConfig>(
            creationConfig,
            dependsOnPreBuildTask = false
        ) {

        override val name: String
            get() = computeTaskName("generate", "FeatureTransitiveDeps")
        override val type: Class<PackagedDependenciesWriterTask>
            get() = PackagedDependenciesWriterTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PackagedDependenciesWriterTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PackagedDependenciesWriterTask::outputFile
            ).withName("deps.txt").on(InternalArtifactType.PACKAGED_DEPENDENCIES)
        }

        override fun configure(
            task: PackagedDependenciesWriterTask
        ) {
            super.configure(task)
            val apiAndRuntimeConfigurations = listOfNotNull(
                creationConfig.variantDependencies.getElements(
                    PublishedConfigSpec(
                        AndroidArtifacts.PublishedConfigType.API_ELEMENTS
                    )
                ),
                creationConfig.variantDependencies.getElements(
                    PublishedConfigSpec(
                        AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
                    )
                )
            )
            task.currentProjectIds.setDisallowChanges(
                apiAndRuntimeConfigurations.let { configurations ->

                    val capabilitiesList = configurations.map { it.outgoing.capabilities }.filter {
                        it.isNotEmpty()
                    }.ifEmpty {
                        listOf(listOf(creationConfig.services.projectInfo.defaultProjectCapability))
                    }

                    val projectId = "${creationConfig.services.projectInfo.path}::${task.variantName}"
                    capabilitiesList.map { capabilities ->
                        encodeCapabilitiesInId(projectId) {
                            capabilities.joinToString(";") { it.convertToString() }
                        }
                    }.distinct()
                }
            )
            task.runtimeAarOrJarDeps =
                creationConfig.variantDependencies
                    .runtimeClasspath
                    .incoming
                    .artifactView { it.attributes(aarOrJarType) }
                    .artifacts
            task.dependsOn(task.runtimeAarOrJarDeps.artifactFiles)

            task.transitivePackagedDeps =
                creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.PROVIDED_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES)

            // When there is a local file dependency, we need to store the absolute path to that
            // file otherwise we can't do the exclusion correctly.
            task.outputs.doNotCacheIf("Local file dependencies found") {
                (it as PackagedDependenciesWriterTask).runtimeAarOrJarDeps.artifacts.any { artifact ->
                    artifact.id.componentIdentifier is OpaqueComponentArtifactIdentifier
                }
            }
        }
    }
}

fun ResolvedArtifactResult.toIdString(): String {
    return id.componentIdentifier.toIdString(
        variantProvider = { variant.attributes.getAttribute(VariantAttr.ATTRIBUTE)?.name }
    ) {
        variant.capabilities.joinToString(";") {
            it.convertToString()
        }
    }
}

/**
 * Converts the capability object to a string. We intentionally leave out the version, which is not
 * a part of the capability identity, to not repackage the same dependency if the versions don't
 * match.
 * TODO(b/185161615): We still have a problem in that case as currently the artifact that will be
 *   used to build the dynamic feature module will not be the one that ends up in the base apk.
 */
fun Capability.convertToString(): String {
    return "Capability: group='$group', name='$name'"
}

private fun ComponentIdentifier.toIdString(
    variantProvider: () -> String?,
    capabilitiesProvider: () -> String
) : String {
    val id = when (this) {
        is ProjectComponentIdentifier -> {
            val variant = variantProvider()
            if (variant == null) {
                getIdString()
            } else {
                "${getIdString()}::${variant}"
            }
        }
        is ModuleComponentIdentifier -> "$group:$module"
        is OpaqueComponentArtifactIdentifier -> file.absolutePath
        else -> toString()
    }

    return encodeCapabilitiesInId(id, capabilitiesProvider)
}

private fun encodeCapabilitiesInId(
    id: String,
    capabilitiesProvider: () -> String
): String {
    return capabilitiesProvider.invoke().takeIf { it.isNotEmpty() }?.let { "$id;$it" } ?: id
}

fun removeVariantNameFromId(
    id: String
): String {
    return if (id.contains("::")) {
        val libraryWithoutVariant = id.substringBeforeLast("::")
        val capabilities = id.substringAfter(";")
        "$libraryWithoutVariant;$capabilities"
    } else {
        id
    }
}
