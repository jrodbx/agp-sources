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

package com.android.tools.lint.gradle.api

import com.android.builder.model.LintOptions
import com.android.repository.Revision
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.io.File

abstract class LintExecutionRequest {
    abstract val project: Project
    abstract val gradlePluginVersion: String

    abstract val buildToolsRevision: Revision?

    abstract val lintOptions: LintOptions?

    abstract val sdkHome: File?

    abstract val toolingRegistry: ToolingModelBuilderRegistry?

    open val isFatalOnly: Boolean
        get() = false

    abstract fun warn(message: String, vararg args: Any)

    abstract val reportsDir: File?

    abstract val variantName: String?

    abstract fun getKotlinSourceFolders(variantName: String, project: Project?): List<File>

    abstract fun getVariantInputs(variantName: String): VariantInputs?

    open var autoFix = false
}
