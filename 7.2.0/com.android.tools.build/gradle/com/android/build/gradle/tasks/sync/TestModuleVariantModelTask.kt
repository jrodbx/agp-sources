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

import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.model.sync.Variant
import org.gradle.work.DisableCachingByDefault

/**
 * [org.gradle.api.Task] to write variant sync model for Test only modules.
 *
 * Caching is disabled as writing the sync model file should be fast and outweighs benefits of
 * caching.
 */
@DisableCachingByDefault
abstract class TestModuleVariantModelTask: ModuleVariantModelTask() {

    override fun addVariantContent(variant: Variant.Builder) {
        super.addVariantContent(variant.testVariantModelBuilder.moduleCommonModelBuilder)
    }

    class CreationAction(private val testVariantCreationConfig: TestVariantCreationConfig):
            AbstractVariantModelTask.CreationAction<TestModuleVariantModelTask, TestVariantCreationConfig>(
                    creationConfig = testVariantCreationConfig,
            ) {

        override val type: Class<TestModuleVariantModelTask>
            get() = TestModuleVariantModelTask::class.java

        override fun configure(task: TestModuleVariantModelTask) {
            super.configure(task)
            task.manifestPlaceholders.setDisallowChanges(
                testVariantCreationConfig.manifestPlaceholders
            )
        }
    }
}
