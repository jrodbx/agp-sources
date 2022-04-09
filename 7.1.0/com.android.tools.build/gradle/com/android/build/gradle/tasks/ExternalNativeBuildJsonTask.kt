/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.gradle.generator.createCxxMetadataGenerator
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.builder.errors.DefaultIssueReporter
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/** Task wrapper around [CxxMetadataGenerator].  */
@DisableCachingByDefault
abstract class ExternalNativeBuildJsonTask @Inject constructor(
        @get:Internal val ops: ExecOperations) :
        UnsafeOutputsTask("C/C++ Configuration is always run.") {

    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>

    @get:Internal
    internal lateinit var abi: CxxAbiModel

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    abstract val renderscriptSources: DirectoryProperty

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(
            DefaultIssueReporter(LoggerWrapper(logger)),
            analyticsService.get(),
            abi.variant
        ).use {
            val generator: CxxMetadataGenerator =
                createCxxMetadataGenerator(
                    abi,
                    analyticsService = analyticsService.get()
                )
            generator.generate(ops, false)
        }
    }
}

/**
 * Create a C/C++ configure task.
 */
fun createCxxConfigureTask(
        globalScope: GlobalScope,
        abi : CxxAbiModel,
        name : String
) = object : GlobalTaskCreationAction<ExternalNativeBuildJsonTask>(globalScope) {
    override val name = name
    override val type = ExternalNativeBuildJsonTask::class.java
    override fun configure(task: ExternalNativeBuildJsonTask) {
        super.configure(task)
        task.abi = abi
        task.variantName = abi.variant.variantName
    }
}
