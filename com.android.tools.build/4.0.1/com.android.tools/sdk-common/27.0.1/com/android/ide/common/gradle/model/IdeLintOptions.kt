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
package com.android.ide.common.gradle.model

import com.android.builder.model.LintOptions
import com.android.ide.common.repository.GradleVersion
import com.google.common.collect.ImmutableMap
import java.io.File
import java.io.Serializable

data class IdeLintOptions(
    val baselineFile: File? = null,
    val lintConfig: File? = null,
    val severityOverrides: Map<String, Int>? = null,
    val isCheckTestSources: Boolean = false,
    val isCheckDependencies: Boolean = false
) : Serializable {

    /** Creates a deep copy of the Gradle tooling API [LintOptions].  */
    constructor(
        options: LintOptions,
        modelVersion: GradleVersion?
    ) : this(
      baselineFile = if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0, "beta", 2, true))
            options.baselineFile
        else
            null,
      lintConfig = IdeModel.copyNewProperty<File>({ options.lintConfig }, null),
      severityOverrides = options.severityOverrides?.let { ImmutableMap.copyOf(it) },
      isCheckTestSources = modelVersion != null &&
                modelVersion.isAtLeast(2, 4, 0) &&
                options.isCheckTestSources,
      isCheckDependencies =
        IdeModel.copyNewProperty({ options.isCheckDependencies }, false)!!

    )

    companion object {
        private const val serialVersionUID = -3962121770706550687L // From serialver
    }
}
