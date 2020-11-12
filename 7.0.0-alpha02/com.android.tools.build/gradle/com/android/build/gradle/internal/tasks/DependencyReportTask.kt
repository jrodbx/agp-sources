/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.AndroidDependenciesRenderer
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.text.StyledTextOutputFactory
import java.io.IOException

open class DependencyReportTask : DefaultTask() {

    private val renderer = AndroidDependenciesRenderer()

    @get:Internal
    lateinit var variants: List<VariantImpl>
    @get:Internal
    lateinit var testComponents: List<TestComponentImpl>

    @TaskAction
    @Throws(IOException::class)
    fun generate() {
        renderer.setOutput(services.get(StyledTextOutputFactory::class.java).create(javaClass))
        val sortedVariants = variants.sortedWith(compareBy { it.name })

        for (variant in sortedVariants) {
            renderer.startComponent(variant)
            renderer.render(variant)
        }

        val sortedTestComponents = testComponents.sortedWith(compareBy { it.name })

        for (testComponent in sortedTestComponents) {
            renderer.startComponent(testComponent)
            renderer.render(testComponent)
        }
    }
}
