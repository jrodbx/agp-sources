/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.databinding

import android.databinding.tool.DataBindingBuilder
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.databinding.DATA_BINDING_TRIGGER_CLASS
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import javax.inject.Inject

/**
 * Delegate to handle the list of excluded classes when data binding is on.
 *
 * An instance of this class should be a [org.gradle.api.tasks.Nested] field on the task, as well
 * as an [org.gradle.api.tasks.Optional] since this is only active if databinding or viewbinding
 * is enabled.
 *
 * Configuration of the [Property] of [DataBindingExcludeDelegate] on the task is done via
 * [configureFrom].
 */
abstract class DataBindingExcludeDelegate @Inject constructor(
    @get:Input
    val databindingEnabled: Boolean
) {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val exportClassListLocation: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyArtifactsDir: DirectoryProperty

    internal fun getExcludedClassList(packageName: String): List<String> {
        if (!databindingEnabled) {
            return listOf()
        }

        return DataBindingBuilder.getJarExcludeList(
            packageName,
            DATA_BINDING_TRIGGER_CLASS,
            exportClassListLocation.orNull?.asFile,
            dependencyArtifactsDir.get().asFile);
    }
}

fun Property<DataBindingExcludeDelegate>.configureFrom(creationConfig: ComponentCreationConfig) {
    // if databinding is not enabled. Do not set this delegate.
    // this means the delegate probably needs to be @Optional
    if (!creationConfig.buildFeatures.dataBinding) {
        return
    }

    setDisallowChanges(creationConfig.services.newInstance(
        DataBindingExcludeDelegate::class.java,
        creationConfig.buildFeatures.dataBinding
    ).also {
        it.dependencyArtifactsDir.setDisallowChanges(
            creationConfig.artifacts.get(InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS)
        )

        it.exportClassListLocation.setDisallowChanges(
            creationConfig.artifacts.get(InternalArtifactType.DATA_BINDING_EXPORT_CLASS_LIST)
        )
    })
}

