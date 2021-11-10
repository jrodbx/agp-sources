/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.tasks.sync

import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.model.sync.Variant
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.work.DisableCachingByDefault

/**
 * [org.gradle.api.Task] to create the sync model file for
 * [com.android.build.api.variant.ApplicationVariant].
 *
 * The task is not incremental and not cacheable as execution should be so fast, that it outweighs
 * the benefits in performance.
 */
@DisableCachingByDefault
abstract class ApplicationVariantModelTask: ModuleVariantModelTask() {
    @get:Input
    abstract val applicationId: Property<String>

    override fun addVariantContent(variant: Variant.Builder) {
        super.addVariantContent(variant.applicationVariantModelBuilder.moduleCommonModelBuilder)
        variant.applicationVariantModelBuilder.applicationId = applicationId.get()
    }

    class CreationAction(private val applicationCreationConfig: ApplicationCreationConfig) :
        AbstractVariantModelTask.CreationAction<ApplicationVariantModelTask, VariantCreationConfig>(
            creationConfig = applicationCreationConfig,
        ) {

        override val type: Class<ApplicationVariantModelTask>
            get() = ApplicationVariantModelTask::class.java

        override fun configure(task: ApplicationVariantModelTask) {
            super.configure(task)
            task.applicationId.setDisallowChanges(applicationCreationConfig.applicationId)
            task.manifestPlaceholders.setDisallowChanges(applicationCreationConfig.manifestPlaceholders)
        }
    }
}
