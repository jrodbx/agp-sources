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

package com.android.build.gradle.internal.tasks

import com.android.build.api.variant.DynamicFeatureVariantBuilder
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.AbstractAppTaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureInfoTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig
import com.android.build.gradle.internal.tasks.featuresplit.FeatureNameWriterTask
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask
import com.android.build.gradle.internal.variant.ComponentInfo
import org.gradle.api.Project

internal class DynamicFeatureTaskManager(
    project: Project,
    variants: Collection<ComponentInfo<DynamicFeatureVariantBuilder, DynamicFeatureCreationConfig>>,
    testComponents: Collection<TestComponentCreationConfig>,
    testFixturesComponents: Collection<TestFixturesCreationConfig>,
    globalConfig: GlobalTaskCreationConfig,
    localConfig: TaskManagerConfig,
    extension: BaseExtension,
) : AbstractAppTaskManager<DynamicFeatureVariantBuilder, DynamicFeatureCreationConfig>(
    project,
    variants,
    testComponents,
    testFixturesComponents,
    globalConfig,
    localConfig,
    extension,
) {

    override fun doCreateTasksForVariant(
            variantInfo: ComponentInfo<DynamicFeatureVariantBuilder, DynamicFeatureCreationConfig>
    ) {
        createCommonTasks(variantInfo)

        val variant = variantInfo.variant

        createDynamicBundleTask(variant)

        // Non-base feature specific task.
        // Task will produce artifacts consumed by the base feature
        taskFactory.register(FeatureSplitDeclarationWriterTask.CreationAction(variant))
        if (variant.buildFeatures.dataBinding) {
            // Create a task that will package necessary information about the feature into a
            // file which is passed into the Data Binding annotation processor.
            taskFactory.register(DataBindingExportFeatureInfoTask.CreationAction(variant))
        }
        taskFactory.register(ExportConsumerProguardFilesTask.CreationAction(variant))
        taskFactory.register(FeatureNameWriterTask.CreationAction(variant))

    }

    private fun createDynamicBundleTask(variantProperties: DynamicFeatureCreationConfig) {

        // If namespaced resources are enabled, LINKED_RES_FOR_BUNDLE is not generated,
        // and the bundle can't be created. For now, just don't add the bundle task.
        // TODO(b/111168382): Remove this
        if (globalConfig.namespacedAndroidResources) {
            return
        }

        taskFactory.register(PerModuleBundleTask.CreationAction(variantProperties))

        if (!variantProperties.debuggable) {
            taskFactory.register(PerModuleReportDependenciesTask.CreationAction(variantProperties))
        }
    }

    override fun createInstallTask(creationConfig: ApkCreationConfig) {
        // no install task for Dynamic Features
    }
}
