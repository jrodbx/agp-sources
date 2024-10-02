/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.featuresplit

import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.apache.commons.io.FileUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.IOException

/**
 * Task that writes the feature module's name to a file and publishes it.
 *
 *
 * NOTE: This task depends on FeatureSetMetadata to assign feature names. Because
 * FeatureSetMetadata depends on FeatureDeclarations, including the name in the existing
 * FeatureDeclarationWriterTask would create a circular dependency.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
abstract class FeatureNameWriterTask : NonIncrementalTask() {

    @get:Input
    abstract val featureName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @Throws(IOException::class)
    public override fun doTaskAction() {
        FileUtils.write(outputFile.asFile.get(), featureName.get())
    }

    class CreationAction(creationConfig: DynamicFeatureCreationConfig) :
        VariantTaskCreationAction<FeatureNameWriterTask, DynamicFeatureCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("write", "FeatureName")

        override val type: Class<FeatureNameWriterTask>
            get() = FeatureNameWriterTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<FeatureNameWriterTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FeatureNameWriterTask::outputFile
            ).withName("feature-name.txt").on(InternalArtifactType.FEATURE_NAME)
        }

        override fun configure(
            task: FeatureNameWriterTask
        ) {
            super.configure(task)
            task.featureName.setDisallowChanges(creationConfig.featureName)
        }
    }
}
