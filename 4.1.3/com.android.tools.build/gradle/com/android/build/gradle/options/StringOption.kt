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
import com.android.builder.model.AndroidProject

enum class StringOption(
    override val propertyName: String,
    stage: ApiStage
) : Option<String> {
    IDE_BUILD_TARGET_DENSITY(AndroidProject.PROPERTY_BUILD_DENSITY, ApiStage.Stable),
    IDE_BUILD_TARGET_ABI(AndroidProject.PROPERTY_BUILD_ABI, ApiStage.Stable),

    IDE_ATTRIBUTION_FILE_LOCATION(AndroidProject.PROPERTY_ATTRIBUTION_FILE_LOCATION, ApiStage.Stable),

    // Signing options
    IDE_SIGNING_STORE_TYPE(AndroidProject.PROPERTY_SIGNING_STORE_TYPE, ApiStage.Stable),
    IDE_SIGNING_STORE_FILE(AndroidProject.PROPERTY_SIGNING_STORE_FILE, ApiStage.Stable),
    IDE_SIGNING_STORE_PASSWORD(AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD, ApiStage.Stable),
    IDE_SIGNING_KEY_ALIAS(AndroidProject.PROPERTY_SIGNING_KEY_ALIAS, ApiStage.Stable),
    IDE_SIGNING_KEY_PASSWORD(AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD, ApiStage.Stable),

    // device config for ApkSelect
    IDE_APK_SELECT_CONFIG(AndroidProject.PROPERTY_APK_SELECT_CONFIG, ApiStage.Stable),

    // location where to write the APK/BUNDLE
    IDE_APK_LOCATION(AndroidProject.PROPERTY_APK_LOCATION, ApiStage.Stable),

    // Installation related options
    IDE_INSTALL_DYNAMIC_MODULES_LIST(AndroidProject.PROPERTY_INJECTED_DYNAMIC_MODULES_LIST, ApiStage.Experimental),

    // Instant run
    IDE_OPTIONAL_COMPILATION_STEPS(AndroidProject.PROPERTY_OPTIONAL_COMPILATION_STEPS, ApiStage.Stable),
    IDE_COLD_SWAP_MODE(AndroidProject.PROPERTY_SIGNING_COLDSWAP_MODE, ApiStage.Stable),
    IDE_VERSION_NAME_OVERRIDE(AndroidProject.PROPERTY_VERSION_NAME, ApiStage.Stable),

    IDE_TARGET_DEVICE_CODENAME(AndroidProject.PROPERTY_BUILD_API_CODENAME, ApiStage.Stable),

    // Profiler plugin
    IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS("android.advanced.profiling.transforms", ApiStage.Stable),

    // Testing
    DEVICE_POOL_SERIAL("com.android.test.devicepool.serial", ApiStage.Experimental),
    PROFILE_OUTPUT_DIR("android.advanced.profileOutputDir", ApiStage.Experimental),

    BUILD_ARTIFACT_REPORT_FILE("android.buildartifact.reportfile", ApiStage.Experimental),

    AAPT2_FROM_MAVEN_OVERRIDE("android.aapt2FromMavenOverride", ApiStage.Experimental),

    SUPPRESS_UNSUPPORTED_OPTION_WARNINGS("android.suppressUnsupportedOptionWarnings", ApiStage.Experimental),

    // The exact version of Android Support plugin used, e.g. 2.4.0.6
    IDE_ANDROID_STUDIO_VERSION(AndroidProject.PROPERTY_ANDROID_SUPPORT_VERSION, ApiStage.Stable),

    // User-specified path to Prefab jar to return from getPrefabFromMaven.
    PREFAB_CLASSPATH("android.prefabClassPath", ApiStage.Experimental),

    // User-specified Prefab version to pull from Maven in getPrefabFromMaven.
    PREFAB_VERSION("android.prefabVersion", ApiStage.Experimental),

    // Jetifier: List of regular expressions for libraries that should not be jetified
    JETIFIER_BLACKLIST("android.jetifier.blacklist", ApiStage.Experimental),

    @Suppress("unused")
    BUILD_CACHE_DIR(
        "android.buildCacheDir",
        ApiStage.Deprecated(DeprecationReporter.DeprecationTarget.AGP_BUILD_CACHE)
    ),

    ;

    override val status = stage.status

    override fun parse(value: Any): String {
        if (value is CharSequence || value is Number) {
            return value.toString()
        }
        throw IllegalArgumentException(
            "Cannot parse project property "
                    + this.propertyName
                    + "='"
                    + value
                    + "' of type '"
                    + value.javaClass
                    + "' as string."
        )
    }
}
