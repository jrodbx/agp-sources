/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.scope.GlobalScope
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

abstract class LintFixTask : LintBaseTask() {

    private var variantInputMap: Map<String, LintBaseTask.VariantInputs>? = null

    override fun doTaskAction() {
        runLint(LintFixTaskDescriptor())
    }

    private inner class LintFixTaskDescriptor : LintBaseTask.LintBaseTaskDescriptor() {

        override val variantName: String?
            get() = null

        override var autoFix: Boolean
            get() = true
            set(value) {
                super.autoFix = value
            }

        override fun getVariantInputs(variantName: String): LintBaseTask.VariantInputs? {
            return variantInputMap!![variantName]
        }

        override fun getVariantNames(): Set<String> = variantInputMap?.keys ?: emptySet()
    }

    class GlobalCreationAction(
        globalScope: GlobalScope, private val components: Collection<VariantImpl>
    ) : BaseCreationAction<LintFixTask>(globalScope) {

        override val name: String
            get() = TaskManager.LINT_FIX
        override val type: Class<LintFixTask>
            get() = LintFixTask::class.java

        override fun configure(task: LintFixTask) {
            super.configure(task)

            task.description =
                    "Runs lint on all variants and applies any safe suggestions to the source code."
            task.group = "cleanup"

            val allInputs = globalScope.project.files()

            task.variantInputMap = components.asSequence().map { component ->
                val inputs = LintBaseTask.VariantInputs(component)
                allInputs.from(inputs.allInputs)
                inputs
            }.associateBy { it.name }
            task.dependsOn(allInputs)
        }
    }
}
