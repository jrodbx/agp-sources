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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.ide.dependencies.getIdString
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Pre build task that does some checks for application variants
 *
 * Caching disabled by default for this task because the task does very little work.
 * The task performs no disk I/O and has no real Output.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
abstract class AppPreBuildTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val fakeOutputDirectory: DirectoryProperty

    @get:Input
    abstract val compileDependencies: SetProperty<String>

    @get:Input
    abstract val runtimeDependencies: SetProperty<String>

    override fun doTaskAction() {
        val compileDeps = compileDependencies.get().toMutableSet()
        compileDeps.removeAll(runtimeDependencies.get())

        if (compileDeps.isNotEmpty()) {
            val formattedDependencies = compileDeps.joinToString(
                prefix = "-> ",
                separator = "\n-> ",
                limit = 5,
                truncated = "... (Total: ${compileDeps.size})"
            )
            throw RuntimeException(
                "The following Android dependencies are set to compileOnly which is not supported:\n$formattedDependencies"
            )
        }
    }

    private class EmptyCreationAction(creationConfig: ComponentCreationConfig) :
        TaskManager.AbstractPreBuildCreationAction<AndroidVariantTask, ComponentCreationConfig>(creationConfig) {

        override val type: Class<AndroidVariantTask>
            get() = AndroidVariantTask::class.java
    }

    private class CheckCreationAction(creationConfig: ComponentCreationConfig) :
        TaskManager.AbstractPreBuildCreationAction<AppPreBuildTask, ComponentCreationConfig>(creationConfig) {

        override val type: Class<AppPreBuildTask>
            get() = AppPreBuildTask::class.java

        override fun configure(
            task: AppPreBuildTask
        ) {
            super.configure(task)
            task.compileDependencies.setDisallowChanges(
                    creationConfig.variantDependencies.compileClasspath.toIdStrings()
            )
            task.runtimeDependencies.setDisallowChanges(
                    creationConfig.variantDependencies.runtimeClasspath.toIdStrings()
            )
            task.fakeOutputDirectory.set(File(
                creationConfig.services.projectInfo.getIntermediatesDir(),
                "prebuild/${creationConfig.dirName}"
            ))
            task.fakeOutputDirectory.disallowChanges()
        }
    }

    companion object {
        @JvmStatic
        fun getCreationAction(
            creationConfig: ComponentCreationConfig
        ): TaskManager.AbstractPreBuildCreationAction<*, *> {
            return if (creationConfig.componentType.isBaseModule && creationConfig.global.hasDynamicFeatures) {
                CheckCreationAction(creationConfig)
            } else EmptyCreationAction(creationConfig)

        }
    }
}

private fun ResolvedComponentResult.flatten(): Set<ResolvedComponentResult> {
    val allComponents = mutableSetOf<ResolvedComponentResult>()
    fun collectAll(node: ResolvedComponentResult) {
        if (allComponents.add(node)) {
            for (dependency in node.dependencies) {
                if (dependency is ResolvedDependencyResult) {
                    collectAll(dependency.selected)
                }
            }
        }
    }
    collectAll(this)
    return allComponents
}

private fun Configuration.toIdStrings(): Provider<Set<String>> {
    return this.incoming.resolutionResult.rootComponent.map { root -> root.flatten().mapNotNull { it.toIdString() }.toSortedSet() }
}

private fun ResolvedComponentResult.toIdString(): String? {
    return when (val id = id) {
        is ProjectComponentIdentifier -> id.getIdString()
        is ModuleComponentIdentifier -> id.toString()
        is OpaqueComponentArtifactIdentifier -> {
            // skip those for now.
            // These are file-based dependencies and it's unlikely to be an AAR.
            null
        }
        else -> null
    }
}
