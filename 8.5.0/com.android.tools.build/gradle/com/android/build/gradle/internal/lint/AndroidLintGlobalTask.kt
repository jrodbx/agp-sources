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

package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.work.DisableCachingByDefault

/**
 * Anchor task for the new lint integration.
 *
 * Eventually will do report aggregation, but for now, just depends on the variant-specific task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.LINT)
abstract class AndroidLintGlobalTask: BaseTask() {

    class GlobalCreationAction(creationConfig: GlobalTaskCreationConfig) : BaseGlobalCreationAction(
        creationConfig
    ) {
        override val name: String get() = Companion.name
        override val description: String get() = "Runs lint on the default variant."
        companion object {
            const val name = "lint"
        }
    }

    class LintFixCreationAction(creationConfig: GlobalTaskCreationConfig) : BaseGlobalCreationAction(
        creationConfig
    ) {
        override val name: String get() = Companion.name
        override val description: String get() = "Runs lint on the default variant and applies any safe suggestions to the source code."
        companion object {
            const val name = "lintFix"
        }
    }

    class UpdateBaselineCreationAction(
        creationConfig: GlobalTaskCreationConfig
    ) : BaseGlobalCreationAction(creationConfig) {
        override val name: String get() = Companion.name
        override val description: String
            get() = "Updates the lint baseline using the default variant."
        companion object {
            const val name = "updateLintBaseline"
        }
    }

    abstract class BaseGlobalCreationAction(creationConfig: GlobalTaskCreationConfig) :
        GlobalTaskCreationAction<AndroidLintGlobalTask>(creationConfig) {

        final override val type: Class<AndroidLintGlobalTask> get() = AndroidLintGlobalTask::class.java
        protected abstract val description: String

        override fun configure(task: AndroidLintGlobalTask) {
            super.configure(task)
            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = description
        }
    }
}
