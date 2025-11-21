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

import com.android.SdkConstants.EXT_JAR
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.FusedLibraryExtension
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.AarMetadataImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout

class FusedLibraryGlobalScopeImpl(
    project: Project,
    private val projectServices: ProjectServices,
    extensionProvider: () -> FusedLibraryExtension
) : FusedLibraryGlobalScope {

    override val aarMetadata: AarMetadataImpl
        get() = extension.aarMetadata as AarMetadataImpl
    override val artifacts= ArtifactsImpl(project, "single")
    override val dependencies = FusedLibraryDependencies()
    override val incomingConfigurations = dependencies.configurations

    override val extension: FusedLibraryExtension by lazy {
        extensionProvider.invoke()
    }

    override val projectLayout: ProjectLayout = project.layout
    override val services: TaskCreationServices
        get() = TaskCreationServicesImpl(projectServices)

    override fun getLocalJars(): FileCollection {
        return VariantDependencies.computeLocalFileDependencies(
            incomingConfigurations.getByConfigType(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH),
            services::fileCollection,
            { file -> file.extension == EXT_JAR }
        )
    }
}
