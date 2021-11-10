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
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File

/** Pre build task that does some checks for application variants  */
@CacheableTask
abstract class AppPreBuildTask : NonIncrementalTask() {

    // list of Android only compile and runtime classpath.
    private lateinit var compileManifests: ArtifactCollection
    private lateinit var runtimeManifests: ArtifactCollection

    @get:OutputDirectory
    lateinit var fakeOutputDirectory: File
        private set

    @get:Input
    val compileDependencies: Set<String> by lazy {
        getAndroidDependencies(compileManifests)
    }

    @get:Input
    val runtimeDependencies: Set<String> by lazy {
        getAndroidDependencies(runtimeManifests)
    }

    override fun doTaskAction() {
        val compileDeps = compileDependencies.toMutableSet()
        compileDeps.removeAll(runtimeDependencies)

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
        TaskManager.AbstractPreBuildCreationAction<AndroidVariantTask>(creationConfig) {

        override val type: Class<AndroidVariantTask>
            get() = AndroidVariantTask::class.java
    }

    private class CheckCreationAction(creationConfig: ComponentCreationConfig) :
        TaskManager.AbstractPreBuildCreationAction<AppPreBuildTask>(creationConfig) {

        override val type: Class<AppPreBuildTask>
            get() = AppPreBuildTask::class.java

        override fun configure(
            task: AppPreBuildTask
        ) {
            super.configure(task)

            task.compileManifests =
                creationConfig.variantDependencies.getArtifactCollection(COMPILE_CLASSPATH, ALL, MANIFEST)
            task.runtimeManifests =
                creationConfig.variantDependencies.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST)

            task.fakeOutputDirectory = File(
                creationConfig.services.projectInfo.getIntermediatesDir(),
                "prebuild/${creationConfig.dirName}"
            )
        }
    }

    companion object {
        @JvmStatic
        fun getCreationAction(
            creationConfig: ComponentCreationConfig
        ): TaskManager.AbstractPreBuildCreationAction<*> {
            return if (creationConfig.variantType.isBaseModule && creationConfig.globalScope.hasDynamicFeatures()) {
                CheckCreationAction(creationConfig)
            } else EmptyCreationAction(creationConfig)

        }
    }
}

private fun getAndroidDependencies(artifactView: ArtifactCollection): Set<String> {
    return artifactView.artifacts.asSequence().mapNotNull { it.toIdString() }.toSortedSet()
}

private fun ResolvedArtifactResult.toIdString(): String? {
    return when (val id = id.componentIdentifier) {
        is ProjectComponentIdentifier -> id.projectPath
        is ModuleComponentIdentifier -> id.toString()
        is OpaqueComponentArtifactIdentifier -> {
            // skip those for now.
            // These are file-based dependencies and it's unlikely to be an AAR.
            null
        }
        else -> null
    }
}
