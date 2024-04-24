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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.transforms.LayoutlibFromMaven
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Task
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ValueSourceSpec
import org.gradle.api.tasks.TaskProvider
import java.io.File

class TaskCreationServicesImpl(projectServices: ProjectServices) : BaseServicesImpl(projectServices), TaskCreationServices {

    override fun fileProvider(provider: Provider<File>): Provider<RegularFile> {
        return projectServices.projectLayout.file(provider)
    }

    override fun files(vararg files: Any?): FileCollection {
        return projectServices.projectLayout.files(files)
    }
    override fun directoryProperty(): DirectoryProperty =
        projectServices.objectFactory.directoryProperty()

    override fun <T> listProperty(type: Class<T>): ListProperty<T> =
        projectServices.objectFactory.listProperty(type)

    override fun fileCollection(): ConfigurableFileCollection =
        projectServices.objectFactory.fileCollection()

    override fun fileCollection(vararg files: Any): ConfigurableFileCollection =
        projectServices.objectFactory.fileCollection().from(*files)

    override fun initializeAapt2Input(aapt2Input: Aapt2Input) {
        projectServices.initializeAapt2Input(aapt2Input)
    }

    override fun createEmptyTask(name: String): TaskProvider<*> =
        projectServices.emptyTaskCreator(name)

    override fun <T> provider(callable: () -> T?): Provider<T> {
        return projectServices.providerFactory.provider(callable)
    }

    @Suppress("UnstableApiUsage")
    override fun <T, P : ValueSourceParameters> providerOf(
        valueSourceType: Class<out ValueSource<T, P>>,
        configuration: Action<in ValueSourceSpec<P>>
    ): Provider<T> {
        return projectServices.providerFactory.of(valueSourceType, configuration)
    }

    override fun <T : Named> named(type: Class<T>, name: String): T =
        projectServices.objectFactory.named(type, name)

    override val lintFromMaven: LintFromMaven get() = projectServices.lintFromMaven

    override val layoutlibFromMaven: LayoutlibFromMaven get() = projectServices.layoutlibFromMaven!!

    override val configurations: ConfigurationContainer
        get() = projectServices.configurationContainer

    override val dependencies: DependencyHandler
        get() = projectServices.dependencyHandler

    override val extraProperties: ExtraPropertiesExtension
        get() = projectServices.extraProperties
}
