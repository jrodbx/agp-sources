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

import com.android.repository.Revision
import com.android.tools.lint.model.LintModelLintOptions
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.io.File

abstract class LintExecutionRequest {
    abstract val project: Project

    abstract val gradlePluginVersion: String

    abstract val lintOptions: LintModelLintOptions?

    open val isFatalOnly: Boolean
        get() = false

    abstract fun warn(message: String, vararg args: Any)

    abstract val reportsDir: File?

    abstract fun getKotlinSourceFolders(variantName: String, project: Project?): List<File>

    open var autoFix = false

    /**
     * Whether this request is for analyzing Android code. Some fields here only apply
     * in Android projects (variants etc are concepts from the Android Gradle plugin.)
     */
    open var android = true

    // Android specific lint request data below

    /** Version of the Android build tools to use, if specified */
    abstract val buildToolsRevision: Revision?

    /** Android SDK root directory */
    abstract val sdkHome: File?

    /** The tooling registry to use to build new builder models */
    abstract val toolingRegistry: ToolingModelBuilderRegistry?

    /** Non null if we're supposed to analyze exactly one (named) variant */
    abstract val variantName: String?

    /** All variant names, if we're analyzing across all variants */
    abstract fun getVariantNames(): Set<String>

    /** Look up additional variant data for the given variant name */
    abstract fun getVariantInputs(variantName: String): VariantInputs?
}
