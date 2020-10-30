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

import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.component.impl.TestComponentPropertiesImpl
import com.android.build.api.variant.impl.DynamicFeatureVariantImpl
import com.android.build.api.variant.impl.DynamicFeatureVariantPropertiesImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.AbstractAppTaskManager
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureInfoTask
import com.android.build.gradle.internal.tasks.featuresplit.FeatureNameWriterTask
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.builder.profile.Recorder

internal class DynamicFeatureTaskManager(
    variants: List<ComponentInfo<DynamicFeatureVariantImpl, DynamicFeatureVariantPropertiesImpl>>,
    testComponents: List<ComponentInfo<TestComponentImpl<out TestComponentPropertiesImpl>, TestComponentPropertiesImpl>>,
    hasFlavors: Boolean,
    globalScope: GlobalScope,
    extension: BaseExtension,
    recorder: Recorder
) : AbstractAppTaskManager<DynamicFeatureVariantImpl, DynamicFeatureVariantPropertiesImpl>(
    variants,
    testComponents,
    hasFlavors,
    globalScope,
    extension,
    recorder
) {

    override fun doCreateTasksForVariant(
        variant: ComponentInfo<DynamicFeatureVariantImpl, DynamicFeatureVariantPropertiesImpl>,
        allVariants: MutableList<ComponentInfo<DynamicFeatureVariantImpl, DynamicFeatureVariantPropertiesImpl>>
    ) {
        createCommonTasks(variant, allVariants)

        val variantProperties = variant.properties

        createDynamicBundleTask(variantProperties)

        // Non-base feature specific task.
        // Task will produce artifacts consumed by the base feature
        taskFactory.register(FeatureSplitDeclarationWriterTask.CreationAction(variantProperties))
        if (variantProperties.buildFeatures.dataBinding) {
            // Create a task that will package necessary information about the feature into a
            // file which is passed into the Data Binding annotation processor.
            taskFactory.register(DataBindingExportFeatureInfoTask.CreationAction(variantProperties))
        }
        taskFactory.register(ExportConsumerProguardFilesTask.CreationAction(variantProperties))
        taskFactory.register(FeatureNameWriterTask.CreationAction(variantProperties))

    }

    private fun createDynamicBundleTask(variantProperties: DynamicFeatureVariantPropertiesImpl) {

        // If namespaced resources are enabled, LINKED_RES_FOR_BUNDLE is not generated,
        // and the bundle can't be created. For now, just don't add the bundle task.
        // TODO(b/111168382): Remove this
        if (variantProperties.globalScope.extension.aaptOptions.namespaced) {
            return
        }

        taskFactory.register(
            PerModuleBundleTask.CreationAction(
                variantProperties,
                TaskManager.packagesCustomClassDependencies(variantProperties)
            )
        )

        if (!variantProperties.variantDslInfo.isDebuggable) {
            taskFactory.register(PerModuleReportDependenciesTask.CreationAction(variantProperties))
        }
    }

    override fun createInstallTask(creationConfig: ApkCreationConfig) {
        // no install task for Dynamic Features
    }
}
