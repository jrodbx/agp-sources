/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.options

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.options.Version.VERSION_7_0
import com.android.build.gradle.options.Version.VERSION_BEFORE_4_0
import com.android.builder.model.AndroidProject

enum class OptionalBooleanOption(
    override val propertyName: String,
    val stage: Stage) : Option<Boolean> {
    SIGNING_V1_ENABLED(AndroidProject.PROPERTY_SIGNING_V1_ENABLED, ApiStage.Stable),
    SIGNING_V2_ENABLED(AndroidProject.PROPERTY_SIGNING_V2_ENABLED, ApiStage.Stable),
    IDE_TEST_ONLY(AndroidProject.PROPERTY_TEST_ONLY, ApiStage.Stable),

    /**
     * This project property is read by the firebase plugin, and has no direct impact on AGP behavior.
     *
     * It is included as an OptionalBooleanOption in order that its value, if set, is recorded in the AGP analytics.
     */
    FIREBASE_PERF_PLUGIN_ENABLE_FLAG("firebasePerformanceInstrumentationEnabled", ApiStage.Stable),

    // Flags for Android Test Retention
    ENABLE_TEST_FAILURE_RETENTION_COMPRESS_SNAPSHOT("android.experimental.testOptions.failureRetention.compressSnapshots", ApiStage.Experimental),

    /* ----------------
     * ENFORCED FEATURES
     */
    @Suppress("unused")
    ENABLE_R8(
        "android.enableR8",
        FeatureStage.Enforced(VERSION_7_0, "Please remove it from `gradle.properties`.")
    ),
    // This option is added only to allow AGP to continue running Proguard tests, until the code
    // is fully removed.
    INTERNAL_ONLY_ENABLE_R8("unsafe.android.enableR8", FeatureStage.SoftlyEnforced(DeprecationReporter.DeprecationTarget.ENABLE_R8)),

    /* ----------------
     * REMOVED FEATURES
     */

    @Suppress("unused")
    SERIAL_AAPT2(
        "android.injected.aapt2.serial",
        FeatureStage.Removed(
            VERSION_BEFORE_4_0,
            "Invoking AAPT2 serially is no longer supported."
        )
    ),

    ;

    override val status = stage.status

    override fun parse(value: Any): Boolean {
        return parseBoolean(propertyName, value)
    }
}
