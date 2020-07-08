/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.Report
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.options.StringOption
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileWriter

/**
 * Task to report information about build artifacts transformations.
 */
open class BuildArtifactReportTask : DefaultTask() {
    private lateinit var reportSupplier: () -> Report

    /**
     * Output file is optional.  If one is specified, the task will output to the file in JSON
     * format.  Otherwise, it will output to stdout in a more human-readable format.
     */
    private var outputFile : File? = null

    internal fun init(report: () -> Report, outputFile : File? = null) {
        this.reportSupplier = report
        this.outputFile = outputFile
    }

    @TaskAction
    fun report() {
        val report = reportSupplier()
        if (outputFile != null) {
            val gson = GsonBuilder().setPrettyPrinting().create()
            FileWriter(outputFile).use { writer ->
                val reportType = object : TypeToken<Report>() {}.type
                gson.toJson(report, reportType, writer)
            }
        }
        for ((type, producersData) in report.entries) {
            println(type)
            println("-".repeat(type.length))
            producersData.producers.forEach { producerData: BuildArtifactsHolder.ProducerData ->
                println("files: ${producerData.files}")
                println("builtBy: ${producerData.builtBy}")
                println("")
            }
        }
    }

    class BuildArtifactReportCreationAction(val scope: VariantScope) :
        TaskCreationAction<BuildArtifactReportTask>() {

        override val name: String
            get() = scope.getTaskName("reportBuildArtifacts")
        override val type: Class<BuildArtifactReportTask>
            get() = BuildArtifactReportTask::class.java

        override fun configure(task: BuildArtifactReportTask) {
            val outputFileName =
                    scope.globalScope.projectOptions.get(StringOption.BUILD_ARTIFACT_REPORT_FILE)
            val outputFile : File? =
                    if (outputFileName == null) null
                    else scope.globalScope.project.file(outputFileName)

            task.init(
                    report = scope.artifacts::createReport,
                    outputFile = outputFile)
        }
    }
}

