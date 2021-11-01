/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.build.gradle.internal.variant

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.ComponentIdentity
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.builder.core.VariantType
import com.android.utils.appendCapitalized
import com.android.utils.capitalizeAndAppend
import org.gradle.api.Task
import java.io.File

/** Data about a variant that produce a Library bundle (.aar)  */
class LibraryVariantData(
    componentIdentity: ComponentIdentity,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    services: VariantPropertiesApiServices,
    globalScope: GlobalScope,
    taskContainer: MutableTaskContainer
) : BaseVariantData(
    componentIdentity,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    services,
    globalScope,
    taskContainer
), TestedVariantData {
    private val testVariants: MutableMap<VariantType, TestVariantData> = mutableMapOf()

    override val description: String
        get() = if (variantDslInfo.hasFlavors()) {
            val sb = StringBuilder(50)
            variantDslInfo.componentIdentity.buildType?.let { sb.appendCapitalized(it) }
            sb.append(" build for flavor ")
            sb.appendCapitalized(variantDslInfo.componentIdentity.flavorName)
            sb.toString()
        } else {
            variantDslInfo.componentIdentity.buildType!!.capitalizeAndAppend(" build")
        }

    override fun getTestVariantData(type: VariantType): TestVariantData? {
        return testVariants[type]
    }

    override fun setTestVariantData(testVariantData: TestVariantData, type: VariantType) {
        testVariants[type] = testVariantData
    }

    // Overridden to add source folders to a generateAnnotationsTask, if it exists.
    override fun registerJavaGeneratingTask(
        task: Task, vararg generatedSourceFolders: File
    ) {
        super.registerJavaGeneratingTask(task, *generatedSourceFolders)

        taskContainer.generateAnnotationsTask?.let { taskProvider ->
            taskProvider.configure { task ->
                for (f in generatedSourceFolders) {
                    task.source(f)
                }
            }
        }
    }

    // Overridden to add source folders to a generateAnnotationsTask, if it exists.
    override fun registerJavaGeneratingTask(
        task: Task,
        generatedSourceFolders: Collection<File>
    ) {
        super.registerJavaGeneratingTask(task, generatedSourceFolders)

        taskContainer.generateAnnotationsTask?.let { taskProvider ->
            taskProvider.configure { task ->
                for (f in generatedSourceFolders) {
                    task.source(f)
                }
            }
        }
    }
}