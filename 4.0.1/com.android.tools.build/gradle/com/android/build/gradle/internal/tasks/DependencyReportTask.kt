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

import com.android.build.gradle.internal.AndroidDependenciesRenderer
import com.android.build.gradle.internal.scope.VariantScope
import java.io.IOException
import java.util.HashSet
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.text.StyledTextOutputFactory

open class DependencyReportTask : DefaultTask() {

    private val renderer = AndroidDependenciesRenderer()

    private val variants = HashSet<VariantScope>()

    @TaskAction
    @Throws(IOException::class)
    fun generate() {
        renderer.setOutput(services.get(StyledTextOutputFactory::class.java).create(javaClass))
        val sortedVariants = variants.sortedWith(compareBy { it.name })

        for (variant in sortedVariants) {
            renderer.startVariant(variant)
            renderer.render(variant)
        }
    }

    /** Sets the variants to generate the report for.  */
    fun setVariants(variantScopes: Collection<VariantScope>) {
        this.variants.addAll(variantScopes)
    }
}
