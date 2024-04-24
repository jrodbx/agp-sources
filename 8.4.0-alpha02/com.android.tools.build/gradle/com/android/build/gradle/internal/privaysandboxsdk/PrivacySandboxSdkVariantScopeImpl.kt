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

package com.android.build.gradle.internal.privaysandboxsdk

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.api.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkBundleImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConfigurations
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryDependencies
import com.android.build.gradle.internal.fusedlibrary.NAMESPACED_ANDROID_RESOURCES_FOR_PRIVACY_SANDBOX_ENABLED
import com.android.build.gradle.internal.publishing.AarOrJarTypeToConsume
import com.android.build.gradle.internal.publishing.getAarOrJarTypeToConsume
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VariantServicesImpl
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.build.gradle.internal.utils.validatePreviewTargetValue
import com.android.builder.core.DefaultApiVersion
import com.android.builder.model.ApiVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec

class PrivacySandboxSdkVariantScopeImpl(
        project: Project,
        val dslServices: DslServices,
        private val projectServices: ProjectServices,
        private val extensionProvider: () -> PrivacySandboxSdkExtension,
        private val bootClasspathConfigProvider: () -> BootClasspathConfig
): PrivacySandboxSdkVariantScope{

    override val services: TaskCreationServices
        get() = TaskCreationServicesImpl(projectServices)
    private val internalServices = VariantServicesImpl(projectServices)

    override val layout: ProjectLayout = project.layout
    override val artifacts: ArtifactsImpl = ArtifactsImpl(project, "single")
    override val incomingConfigurations: FusedLibraryConfigurations = FusedLibraryConfigurations()
    override val outgoingConfigurations: FusedLibraryConfigurations = FusedLibraryConfigurations()
    override val dependencies: FusedLibraryDependencies = FusedLibraryDependencies(incomingConfigurations)

    override val extension: PrivacySandboxSdkExtension by lazy {
        extensionProvider.invoke()
    }

    override val mergeSpec = Spec { componentIdentifier: ComponentIdentifier ->
        true // so far, all dependencies are consumed by the sdk library plugin.
    }

    override val compileSdkVersion: String by lazy {
        extension.compileSdkPreview?.let { validatePreviewTargetValue(it) }?.let { "android-$it" } ?:
        extension.compileSdkExtension?.let { "android-${extension.compileSdk}-ext$it" } ?:
        extension.compileSdk?.let {"android-$it"} ?: throw RuntimeException(
            "compileSdk version is not set"
        )
    }
    override val minSdkVersion: ApiVersion by lazy {
        extension.minSdkPreview?.let { DefaultApiVersion(it) } ?:
        extension.minSdk?.let { DefaultApiVersion(it) } ?:
        DefaultApiVersion("TiramisuPrivacySandbox")
    }
    override val bootClasspath: Provider<List<RegularFile>>
            get() = bootClasspathConfigProvider.invoke().bootClasspath
    override val bundle: PrivacySandboxSdkBundleImpl
        get() = extension.bundle as PrivacySandboxSdkBundleImpl
    override val signingConfig: SigningConfig
        get() = extension.signingConfig
    override val experimentalProperties: MapProperty<String, Any>
        get() = internalServices.mapPropertyOf(
                String::class.java,
                Any::class.java,
                extension.experimentalProperties
        )
    override val aarOrJarTypeToConsume: AarOrJarTypeToConsume
        get() = getAarOrJarTypeToConsume(
                projectServices.projectOptions,
                namespacedAndroidResources = NAMESPACED_ANDROID_RESOURCES_FOR_PRIVACY_SANDBOX_ENABLED
        )
}
