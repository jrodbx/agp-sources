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

package com.android.build.gradle.options

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.build.gradle.options.TestRunnerArguments.Companion.TEST_RUNNER_ARGS_PREFIX
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.Locale
import javax.inject.Inject

/**
 * A build service to provide [ProjectOptions] to all projects.
 */
abstract class ProjectOptionService : BuildService<ProjectOptionService.Params> {

    /**
     * Custom test runner arguments can only be obtained with [Project] instance, so we need to
     * capture them in first non configuration cached run and reuse them in configuration cached
     * runs.
     *
     * Adding custom runner arguments from gradle.properties is not fully compatible with config
     * caching because we would miss newly added arguments in configuration cached runs. Therefore,
     * we raise warnings when finding custom test runner arguments in gradle.properties and
     * encourage users to add those arguments in gradle dsl.
     */
    interface Params : BuildServiceParameters {
        val customTestRunnerArgs: MapProperty<String, String>
    }

    @get:Inject
    abstract val providerFactory: ProviderFactory

    val projectOptions: ProjectOptions =
        ProjectOptions(parameters.customTestRunnerArgs.get().toImmutableMap(), providerFactory)

    class RegistrationAction(project: Project)
        : ServiceRegistrationAction<ProjectOptionService, Params>(
        project,
        ProjectOptionService::class.java
    ) {
        override fun configure(parameters: Params) {
            val standardArgs = TestRunnerArguments.values().map {
                it.toString().toLowerCase(Locale.US)
            }
            val customArgs = mutableMapOf<String, String>()
            project.extensions.extraProperties.properties.entries.forEach {
                if (it.key.startsWith(TEST_RUNNER_ARGS_PREFIX)) {
                   val argName = it.key.substring(TEST_RUNNER_ARGS_PREFIX.length)
                    if (standardArgs.contains(argName)) {
                        return@forEach
                    }
                    // As we would ignore new custom arguments added as gradle properties in
                    // the following configuration-cached runs, we need to encourage users to
                    // specify custom arguments through dsl.
                    project.logger.warn(
                        "Passing custom test runner argument ${it.key} from gradle.properties"
                                + " or command line is not compatible with configuration caching. "
                                + "Please specify this argument using android gradle dsl."
                    )
                    val argValue = it.value.toString();
                    customArgs[argName] = argValue
                    // Make sure we invalidate configuration cache if existing custom arguments change
                    project.providers.gradleProperty(it.key).forUseAtConfigurationTime().get();
                }
            }
            parameters.customTestRunnerArgs.set(customArgs)
        }
    }
}
