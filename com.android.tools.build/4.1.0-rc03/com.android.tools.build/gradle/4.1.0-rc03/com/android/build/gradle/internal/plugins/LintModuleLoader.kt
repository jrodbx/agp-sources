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

package com.android.build.gradle.internal.plugins

import com.android.builder.model.AndroidProject
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeAndroidProjectImpl
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelFactory
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleLoader
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

class LintModuleLoader(
    private val plugin: BasePlugin<*, *>,
    private val registry: ToolingModelBuilderRegistry
) : LintModelModuleLoader {
    override fun getModule(library: LintModelDependency): LintModelModule? {
        return null
    }

    override fun getModule(path: String, factory: LintModelFactory?): LintModelModule? {
        val pluginProject = plugin.project
        val project = if (pluginProject.path == path)
            pluginProject
        else
            pluginProject?.findProject(path) ?: return null
        return createLintBuildModel(project, factory)
    }

    private fun createLintBuildModel(
        gradleProject: Project,
        defaultFactory: LintModelFactory?
    ): LintModelModule? {
        val project = createAndroidProject(gradleProject)
        val factory = defaultFactory ?: LintModelFactory()
        return factory.create(project, gradleProject.rootDir)
    }

    private fun createAndroidProject(
        project: Project
    ): IdeAndroidProject {
        val modelName = AndroidProject::class.java.name
        val modelBuilder = registry.getBuilder(modelName)
        val ext = project.extensions.extraProperties
        // setup the level 3 sync.
        // Ensure that projects are constructed serially since otherwise
        // it's possible for a race condition on the below property
        // to trigger occasional NPE's like the one in b.android.com/38117575
        synchronized(ext) {
            ext[AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED] =
                AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD.toString()
            return try {
                val model = modelBuilder.buildAll(modelName, project) as AndroidProject
                val factory = IdeDependenciesFactory()
                // Sync issues are not used in lint.
                IdeAndroidProjectImpl.create(model, factory, model.variants, emptyList())
            } finally {
                ext[AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED] = null
            }
        }
    }
}
