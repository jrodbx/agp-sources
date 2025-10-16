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

package com.android.buildanalyzer.common

/**
 * Enum class for the different possible warnings / informations that
 * can be attached to a task category in Build Analyzer.
 */
enum class TaskCategoryIssue(val taskCategory: TaskCategory, val severity: Severity) {

    /**
     * Warning for when Resource-Ids are final.
     * BooleanOption android.nonFinalResIds = false.
     */
    NON_FINAL_RES_IDS_DISABLED(TaskCategory.ANDROID_RESOURCES, Severity.WARNING),

    /**
     * Warning for when non-transitive R classes are disabled.
     * BooleanOption android.nonTransitiveRClass = false.
     */
    NON_TRANSITIVE_R_CLASS_DISABLED(TaskCategory.ANDROID_RESOURCES, Severity.WARNING),

    @Deprecated("We no longer warn about this flag in the build analyzer.")
    RESOURCE_VALIDATION_ENABLED(TaskCategory.ANDROID_RESOURCES, Severity.WARNING),

    /**
     * Non-incremental Java annotation processor warning.
     */
    JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR(TaskCategory.JAVA, Severity.WARNING),

    /**
     * Performance warning for enabling minification in debug builds.
     */
    MINIFICATION_ENABLED_IN_DEBUG_BUILD(TaskCategory.OPTIMIZATION, Severity.WARNING);

    enum class Severity {
        WARNING,
        INFO
    }
}
