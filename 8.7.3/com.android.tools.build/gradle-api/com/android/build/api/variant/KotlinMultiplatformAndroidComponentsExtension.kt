/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.variant

import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * Components extension for KMP Android Gradle Plugin related components.
 */
@Incubating
interface KotlinMultiplatformAndroidComponentsExtension:
    DslLifecycle<KotlinMultiplatformAndroidExtension>, AndroidComponents {

    /**
     * Allow for registration of a [callback] to be called with variant instances of type [KotlinMultiplatformAndroidExtension]
     * once the list of [com.android.build.api.artifact.Artifact] has been determined.
     *
     * At this stage, access to the DSL objects is disallowed
     *
     * Because the list of artifacts (including private ones) is final, one cannot change the build
     * flow anymore as [org.gradle.api.Task]s are now expecting those artifacts as inputs. However
     * users can modify such artifacts by replacing or transforming them, see [com.android.build.api.artifact.Artifacts]
     * for details.
     *
     * Code executing in the [callback] also has access to the [KotlinMultiplatformAndroidExtension] information which is used
     * to configure [org.gradle.api.Task] inputs (for example, the buildConfigFields). Such
     * information represented as [org.gradle.api.provider.Property] can still be modified ensuring
     * that all [org.gradle.api.Task]s created by the Android Gradle Plugin use the updated value.
     */
    fun onVariant(
        callback: (KotlinMultiplatformAndroidVariant) -> Unit
    )

    /**
     * [Action] based version of [onVariant] above.
     */
    fun onVariant(
        callback: Action<KotlinMultiplatformAndroidVariant>
    )
}
