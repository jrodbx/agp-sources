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

package com.android.build.gradle.internal.plugins

import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.gradle.internal.crash.afterEvaluate
import com.android.build.gradle.internal.dsl.InternalPrivacySandboxSdkExtension
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.configureTransformsForFusedLibrary
import com.android.build.gradle.internal.fusedlibrary.getDslServices
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScopeImpl
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.R8D8ThreadPoolBuildService
import com.android.build.gradle.internal.services.R8MaxParallelTasksBuildService
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.repository.Revision
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

class PrivacySandboxSdkPlugin @Inject constructor(
        val softwareComponentFactory: SoftwareComponentFactory,
        listenerRegistry: BuildEventsListenerRegistry,
        private val buildFeatures: BuildFeatures,
) : AndroidPluginBaseServices(listenerRegistry, buildFeatures), Plugin<Project> {

    val dslServices: DslServices by lazy(LazyThreadSafetyMode.NONE) {
        withProject("dslServices") { project ->
            getDslServices(project, projectServices)
        }
    }

    private val versionedSdkLoaderService: VersionedSdkLoaderService by lazy(LazyThreadSafetyMode.NONE) {
        withProject("versionedSdkLoaderService") { project ->
            VersionedSdkLoaderService(
                    dslServices,
                    project,
                    { variantScope.compileSdkVersion },
                    {
                        Revision.parseRevision(extension.buildToolsVersion,
                                Revision.Precision.MICRO)
                    },
            )
        }
    }

    // so far, there is only one variant.
    private val variantScope: PrivacySandboxSdkVariantScope by lazy {
        withProject("variantScope") { project ->
            PrivacySandboxSdkVariantScopeImpl(
                    project,
                    dslServices,
                    projectServices,
                    { extension },
                    {
                        BootClasspathConfigImpl(
                                project,
                                projectServices,
                                versionedSdkLoaderService,
                                libraryRequests = listOf(),
                                isJava8Compatible = { true },
                                returnDefaultValuesForMockableJar = { false },
                                forUnitTest = false
                        )
                    })
        }
    }

    private val extension: PrivacySandboxSdkExtension by lazy(LazyThreadSafetyMode.NONE)
    {
        withProject("extension") { project ->
            instantiateExtension(project)
        }
    }

    override fun configureProject(project: Project) {
        // workaround for https://github.com/gradle/gradle/issues/20145
        project.plugins.apply(JvmEcosystemPlugin::class.java)

        val projectOptions = projectServices.projectOptions
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute()
        SymbolTableBuildService.RegistrationAction(project).execute()

        R8D8ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        R8MaxParallelTasksBuildService.RegistrationAction(project, projectOptions).execute()
    }

    override fun configureExtension(project: Project) {
        extension
    }

    override fun apply(project: Project) {
        throw GradleException(
            "Privacy Sandbox SDK Plugin has been phased out.\n" +
                    "Check https://privacysandbox.com/news/update-on-plans-for-privacy-sandbox-technologies for full details"
        )
    }

    private fun instantiateExtension(project: Project): PrivacySandboxSdkExtension {

        val sdkLibraryExtensionImpl = dslServices.newDecoratedInstance(
                PrivacySandboxSdkExtensionImpl::class.java,
                dslServices,
        )

        abstract class Extension(
                val publicExtensionImpl: PrivacySandboxSdkExtensionImpl,
        ): InternalPrivacySandboxSdkExtension by publicExtensionImpl

        return project.extensions.create(
                PrivacySandboxSdkExtension::class.java,
                "android",
                Extension::class.java,
                sdkLibraryExtensionImpl
        )
    }

    override fun createTasks(project: Project) {
        project.afterEvaluate(
            afterEvaluate {
                configureTransforms(project)
            }
        )
    }
    private fun configureTransforms(project: Project) {
        configureTransformsForFusedLibrary(
            project,
            projectServices
        )
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType =
            GradleBuildProject.PluginType.PRIVACY_SANDBOX_SDK
}
