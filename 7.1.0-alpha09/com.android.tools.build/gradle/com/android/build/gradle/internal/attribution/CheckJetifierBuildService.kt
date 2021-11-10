/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.attribution

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.tasks.CheckJetifierTask
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.utils.SynchronizedFile
import com.android.ide.common.attribution.CheckJetifierResult
import com.android.utils.FileUtils
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

/**
 * [BuildService] to aggregate [CheckJetifierResult]s from [CheckJetifierTask]s and write the final
 * result to a file.
 */
@Suppress("UnstableApiUsage")
abstract class CheckJetifierBuildService
    : BuildService<CheckJetifierBuildService.Parameters>, AutoCloseable {

    interface Parameters : BuildServiceParameters {

        val resultFile: RegularFileProperty
    }

    class RegistrationAction(project: Project, private val projectOptions: ProjectOptions) :
        ServiceRegistrationAction<CheckJetifierBuildService, Parameters>(
            project,
            CheckJetifierBuildService::class.java
        ) {

        override fun execute(): Provider<CheckJetifierBuildService> {
            val resultFilePath = projectOptions.get(StringOption.IDE_CHECK_JETIFIER_RESULT_FILE)
            if (resultFilePath != null) {
                val resultFile = File(resultFilePath)
                check(resultFile.isAbsolute) {
                    "${StringOption.IDE_CHECK_JETIFIER_RESULT_FILE.propertyName} must be an absolute path." +
                            " Current value is: $resultFilePath"
                }
                // Delete the file early at configuration time because there may be multiple
                // `CheckJetifierBuildService`s running at execution time (if subprojects are loaded
                // by different classloader), and we don't want a BuildService to delete the result
                // written by another BuildService.
                FileUtils.deleteIfExists(resultFile)
            }

            return super.execute()
        }

        override fun configure(parameters: Parameters) {
            parameters.resultFile.fileValue(
                projectOptions.get(StringOption.IDE_CHECK_JETIFIER_RESULT_FILE)?.let { File(it) }
            )
        }
    }

    private var aggregatedResult: CheckJetifierResult? = null

    @Synchronized
    fun addResult(result: CheckJetifierResult) {
        if (parameters.resultFile.isPresent) {
            aggregatedResult = if (aggregatedResult != null) {
                CheckJetifierResult.aggregateResults(aggregatedResult!!, result)
            } else {
                result
            }
        }
    }

    override fun close() {
        parameters.resultFile.orNull?.let {
            if (aggregatedResult != null) {
                writeResult(aggregatedResult!!, it.asFile)
            }
        }
    }

    private fun writeResult(result: CheckJetifierResult, resultFile: File) {
        // Typically, there should be only one CheckJetifierBuildService in a build, and this method
        // will be called only once at the end of the build. However, if subprojects are loaded by
        // different classloaders, there will be one CheckJetifierBuildService per classloader. In
        // that case, multiple `CheckJetifierBuildService`s may write results to the same file
        // (sequentially or concurrently).
        // To safeguard concurrent access to this file, we'll need to use a global (JVM-scoped)
        // lock.
        SynchronizedFile.getInstanceWithSingleProcessLocking(resultFile).write {
            if (it.exists()) {
                val existingResult = CheckJetifierResult.load(it)
                val combinedResult = CheckJetifierResult.aggregateResults(existingResult, result)
                CheckJetifierResult.save(combinedResult, it)
            } else {
                FileUtils.mkdirs(resultFile.parentFile)
                CheckJetifierResult.save(result, it)
            }
        }
    }
}
