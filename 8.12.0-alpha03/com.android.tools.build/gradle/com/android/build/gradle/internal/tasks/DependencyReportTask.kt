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
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.work.DisableCachingByDefault
import java.io.IOException

@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.HELP)
abstract class DependencyReportTask : DefaultTask() {

    @get:Internal
    abstract val mavenCoordinateCache: Property<MavenCoordinatesCacheBuildService>

    @get:Internal
    abstract val variants: ListProperty<VariantCreationConfig>
    @get:Internal
    abstract val nestedComponents: ListProperty<NestedComponentCreationConfig>

    @TaskAction
    @Throws(IOException::class)
    fun generate() {
        val renderer = AndroidDependenciesRenderer(mavenCoordinateCache.get())

        renderer.setOutput(services.get(StyledTextOutputFactory::class.java).create(javaClass))
        val sortedVariants = variants.get().sortedWith(compareBy { it.name })

        for (variant in sortedVariants) {
            renderer.startComponent(variant)
            renderer.render(variant)
        }

        val sortedNestedComponents = nestedComponents.get().sortedWith(compareBy { it.name })

        for (component in sortedNestedComponents) {
            renderer.startComponent(component)
            renderer.render(component)
        }
        logger.warn("DependencyReportTask has been deprecated and will be removed in AGP 9.0")
    }
}
