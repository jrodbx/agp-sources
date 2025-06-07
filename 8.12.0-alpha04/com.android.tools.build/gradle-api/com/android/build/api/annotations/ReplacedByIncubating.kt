/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api.annotations

/**
 * Annotation for APIs that are in the process of being replaced by an [org.gradle.api.Incubating] API.
 *
 * When replacing a stable method with a new API which is [org.gradle.api.Incubating], we cannot
 * directly deprecate the old method as it would leave the user to use either between a
 * deprecated or an incubating API.
 *
 * Therefore, when replacing a public API with a new version, the following rules apply :
 * - The new API will be annotated with [org.gradle.api.Incubating]
 * - The old API will be annotated with [ReplacedByIncubating] but will not be deprecated yet.
 * - Once the new API becomes stable, the old API [ReplacedByIncubating] will be replaced with a
 * [Deprecated] annotation.
 * - After the deprecation period has passed, the old API will be removed.
 *
 * Users can use this annotation presence to start moving their code to the new API if they do
 * not object using an incubating potentially unstable API.
 *
 * This interface is meant to document the Android Gradle Plugin APIs and should not be used by
 * third party plugins/build logic.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReplacedByIncubating(
    val message: String,
    val bugId: Long,
)
