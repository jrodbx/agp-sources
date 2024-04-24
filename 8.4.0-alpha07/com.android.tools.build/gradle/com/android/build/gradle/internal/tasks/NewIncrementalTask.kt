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

package com.android.build.gradle.internal.tasks

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.InputChanges

/**
 * Base incremental task using the new input details APIs.
 */
@DisableCachingByDefault
abstract class NewIncrementalTask: AndroidVariantTask() {

    abstract fun doTaskAction(inputChanges: InputChanges)

    @TaskAction
    fun taskAction(inputChanges: InputChanges) {
        recordTaskAction(analyticsService.get()) {
            if (!inputChanges.isIncremental) {
                // manually remove all outputs (b/169701279)
                cleanUpTaskOutputs()
            }
            doTaskAction(inputChanges)
        }
    }
}
