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

package com.android.build.gradle.internal.fusedlibrary

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.publishing.getAarOrJarTypeToConsume
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.builder.model.v2.ide.ProjectType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

const val NAMESPACED_ANDROID_RESOURCES_FOR_PRIVACY_SANDBOX_ENABLED = false

internal fun createTasks(
        project: Project,
        artifacts: ArtifactsImpl,
        artifactForPublication: Artifact.Single<RegularFile>,
        tasksCreationActions: List<TaskCreationAction<out DefaultTask>>
) {
    val taskProviders = TaskFactoryImpl(project.tasks).let { taskFactory ->
        tasksCreationActions.map { creationAction ->
            taskFactory.register(creationAction)
        }
    }

    // create anchor tasks
    project.tasks.register("assemble") { assembleTask ->
        artifactForPublication?.let { artifactTypeForPublication ->
            assembleTask.dependsOn(artifacts.get(artifactTypeForPublication))
        } ?: taskProviders.forEach { assembleTask.dependsOn(it) }
    }
}

internal fun configureTransforms(project: Project, projectServices: ProjectServices) {
    DependencyConfigurator(project, projectServices).configureGeneralTransforms(
            NAMESPACED_ANDROID_RESOURCES_FOR_PRIVACY_SANDBOX_ENABLED,
            getAarOrJarTypeToConsume(
                    projectServices.projectOptions,
                    NAMESPACED_ANDROID_RESOURCES_FOR_PRIVACY_SANDBOX_ENABLED)
    )
}

internal fun getDslServices(project: Project, projectServices: ProjectServices): DslServices {
    val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
            SdkComponentsBuildService.RegistrationAction(
                    project,
                    projectServices.projectOptions
            ).execute()

    return DslServicesImpl(projectServices, sdkComponentsBuildService, ProjectType.FUSED_LIBRARIES)
}

