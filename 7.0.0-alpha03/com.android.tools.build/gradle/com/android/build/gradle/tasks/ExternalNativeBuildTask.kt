/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.cxx.build.CxxBuilder
import com.android.build.gradle.internal.cxx.build.CxxNopBuilder
import com.android.build.gradle.internal.cxx.build.CxxRegularBuilder
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.errors.DefaultIssueReporter
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Task that performs a C/C++ build action or refers to a build from a different task.
 */
abstract class ExternalNativeBuildTask @Inject constructor(@get:Internal val ops: ExecOperations) :
        UnsafeOutputsTask("External Native Build task is always run as incrementality is left to the external build system.") {

    @get:Internal
    internal lateinit var builder : CxxBuilder

    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>

    @get:OutputDirectory
    val objFolder get() = builder.objFolder

    @get:OutputDirectory
    val soFolder get() = builder.soFolder

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(DefaultIssueReporter(LoggerWrapper(logger))).use {
            builder.build(ops)
        }
    }
}

/**
 * Create a C/C++ build task that just refers to another set of tasks (by way of Gradle
 * dependencies). It doesn't do any work but it does expose 'objFolder' and 'soFolder'
 * for consumers of variant API.
 */
fun createReferringCxxBuildTask(
        configurationModel : CxxConfigurationModel,
        creationConfig: VariantCreationConfig,
        name : String
) = object : VariantTaskCreationAction<ExternalNativeBuildTask, VariantCreationConfig>(creationConfig) {
    override val name = name
    override val type = ExternalNativeBuildTask::class.java
    override fun configure(task: ExternalNativeBuildTask) {
        super.configure(task)
        task.builder = CxxNopBuilder(configurationModel)
        task.sdkComponents.setDisallowChanges(getBuildService(creationConfig.services.buildServiceRegistry))
    }
}

/**
 * Create a C/C++ build task does actual build work. It may be referred to by build tasks created
 * by [createReferringCxxBuildTask].
 */
fun createWorkingCxxBuildTask(
        globalScope: GlobalScope,
        configurationModel : CxxConfigurationModel,
        name : String
) = object : GlobalTaskCreationAction<ExternalNativeBuildTask>(globalScope) {
    override val name = name
    override val type = ExternalNativeBuildTask::class.java
    override fun configure(task: ExternalNativeBuildTask) {
        super.configure(task)
        task.builder = CxxRegularBuilder(configurationModel)
        task.variantName = configurationModel.variant.variantName
    }
}
