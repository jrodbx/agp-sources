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

import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.TaskCategory
import com.android.ide.common.build.filebasedproperties.variant.VariantProperties
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.SYNC)
abstract class UnitTestVariantModelTask: AbstractVariantModelTask() {

    override fun addVariantContent(variant: VariantProperties.Builder) {}

    class CreationAction(creationConfig: UnitTestCreationConfig):
        AbstractVariantModelTask.CreationAction<UnitTestVariantModelTask, UnitTestCreationConfig>(
            creationConfig = creationConfig,
        ) {

        override val type: Class<UnitTestVariantModelTask>
            get() = UnitTestVariantModelTask::class.java
    }
}
