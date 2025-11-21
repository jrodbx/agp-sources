/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.buildanalyzer.common.TaskCategory

/***
 * Annotation class that is used for Category-Based Task Analyzer for Build Analyzer.
 * All AGP tasks that is an instance of Task::class.java should have this annotation.
 * Exceptions should be declared in the allow-list in [BuildAnalyzerTest]
 */
@Retention(value = AnnotationRetention.RUNTIME)
@Target(allowedTargets = [AnnotationTarget.CLASS])
annotation class BuildAnalyzer(
    /***
     * Main execution category of the task
     */
    val primaryTaskCategory: TaskCategory,
    /***
     * Other possible groupings for the task, does not have to be set
     */
    val secondaryTaskCategories: Array<TaskCategory> = []
)
