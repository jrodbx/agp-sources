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

import com.android.build.gradle.internal.crash.afterEvaluate
import com.android.build.gradle.internal.fusedlibrary.configureTransformsForFusedLibrary
import com.android.build.gradle.internal.fusedlibrary.getDslServices
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.R8D8ThreadPoolBuildService
import com.android.build.gradle.internal.services.R8MaxParallelTasksBuildService
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.google.wireless.android.sdk.stats.GradleBuildProject
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.build.event.BuildEventsListenerRegistry

class PrivacySandboxSdkPlugin
@Inject
constructor(
  val softwareComponentFactory: SoftwareComponentFactory,
  listenerRegistry: BuildEventsListenerRegistry,
  private val buildFeatures: BuildFeatures,
) : AndroidPluginBaseServices(listenerRegistry, buildFeatures), Plugin<Project> {

  val dslServices: DslServices by
    lazy(LazyThreadSafetyMode.NONE) { withProject("dslServices") { project -> getDslServices(project, projectServices) } }

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

  override fun configureExtension(project: Project) {}

  override fun apply(project: Project) {
    throw GradleException(
      "Privacy Sandbox SDK Plugin has been phased out.\n" +
        "Check https://privacysandbox.com/news/update-on-plans-for-privacy-sandbox-technologies for full details"
    )
  }

  override fun createTasks(project: Project) {
    project.afterEvaluate(afterEvaluate { configureTransforms(project) })
  }

  private fun configureTransforms(project: Project) {
    configureTransformsForFusedLibrary(project, projectServices)
  }

  override fun getAnalyticsPluginType(): GradleBuildProject.PluginType = GradleBuildProject.PluginType.PRIVACY_SANDBOX_SDK
}
