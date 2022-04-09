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

package com.android.build.gradle.internal.scope

import com.android.build.gradle.BaseExtension
import com.android.builder.core.BuilderConstants
import com.android.builder.model.AndroidProject
import com.google.common.base.Preconditions
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginConvention
import java.io.File

class ProjectInfo(project: Project) {
    private val project: Project = project

    fun getProjectBaseName(): String {
        val convention = Preconditions.checkNotNull(
                project.convention.findPlugin(BasePluginConvention::class.java))
        return convention!!.archivesBaseName
    }

    fun getProject(): Project {
        return project
    }

    fun getBuildDir(): File {
        return project.buildDir
    }

    fun getTestResultsFolder(): File? {
        return File(getBuildDir(), "test-results")
    }

    fun getReportsDir(): File {
        return File(getBuildDir(), BuilderConstants.FD_REPORTS)
    }

    fun getTestReportFolder(): File? {
        return File(getBuildDir(), "reports/tests")
    }

    fun getIntermediatesDir(): File {
        return File(getBuildDir(), AndroidProject.FD_INTERMEDIATES)
    }

    fun getTmpFolder(): File {
        return File(getIntermediatesDir(), "tmp")
    }

    fun getOutputsDir(): File {
        return File(getBuildDir(), AndroidProject.FD_OUTPUTS)
    }

    fun getJacocoAgentOutputDirectory(): File {
        return File(getIntermediatesDir(), "jacoco")
    }

    fun getJacocoAgent(): File {
        return File(getJacocoAgentOutputDirectory(), "jacocoagent.jar")
    }

    fun getExtension(): BaseExtension {
        return getProject().extensions.getByName("android") as BaseExtension
    }
}
