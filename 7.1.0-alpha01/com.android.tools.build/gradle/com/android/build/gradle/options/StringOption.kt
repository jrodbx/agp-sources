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

    /* -----------
     * STABLE APIs
     */

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

    // Instant run
    IDE_OPTIONAL_COMPILATION_STEPS(AndroidProject.PROPERTY_OPTIONAL_COMPILATION_STEPS, ApiStage.Stable),
    IDE_COLD_SWAP_MODE(AndroidProject.PROPERTY_SIGNING_COLDSWAP_MODE, ApiStage.Stable),
    IDE_VERSION_NAME_OVERRIDE(AndroidProject.PROPERTY_VERSION_NAME, ApiStage.Stable),

    IDE_TARGET_DEVICE_CODENAME(AndroidProject.PROPERTY_BUILD_API_CODENAME, ApiStage.Stable),

    // Profiler plugin
    IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS("android.advanced.profiling.transforms", ApiStage.Stable),

    // The exact version of Android Support plugin used, e.g. 2.4.0.6
    IDE_ANDROID_STUDIO_VERSION(AndroidProject.PROPERTY_ANDROID_SUPPORT_VERSION, ApiStage.Stable),

    // Native
    NATIVE_BUILD_OUTPUT_LEVEL(AndroidProject.PROPERTY_NATIVE_BUILD_OUTPUT_LEVEL, ApiStage.Stable),

    // AGP suggests it should be upgraded if the compile sdk is newer than the version it was tested
    // with. This option allows developers to suppress that warning.
    // e.g. android.suppressUnsupportedCompileSdk=S,31,32
    SUPPRESS_UNSUPPORTED_COMPILE_SDK("android.suppressUnsupportedCompileSdk", ApiStage.Stable),

    /* -----------------
     * EXPERIMENTAL APIs
     */

    // Installation related options
    IDE_INSTALL_DYNAMIC_MODULES_LIST(AndroidProject.PROPERTY_INJECTED_DYNAMIC_MODULES_LIST, ApiStage.Experimental),

    // Testing
    DEVICE_POOL_SERIAL("com.android.test.devicepool.serial", ApiStage.Experimental),
    PROFILE_OUTPUT_DIR("android.advanced.profileOutputDir", ApiStage.Experimental),

    BUILD_ARTIFACT_REPORT_FILE("android.buildartifact.reportfile", ApiStage.Experimental),

    AAPT2_FROM_MAVEN_OVERRIDE("android.aapt2FromMavenOverride", ApiStage.Experimental),

    AAPT2_FROM_MAVEN_VERSION_OVERRIDE("android.aapt2Version", ApiStage.Experimental),

    SUPPRESS_UNSUPPORTED_OPTION_WARNINGS("android.suppressUnsupportedOptionWarnings", ApiStage.Experimental),

    // User-specified path to Prefab jar to return from getPrefabFromMaven.
    PREFAB_CLASSPATH("android.prefabClassPath", ApiStage.Experimental),

    // User-specified Prefab version to pull from Maven in getPrefabFromMaven.
    PREFAB_VERSION("android.prefabVersion", ApiStage.Experimental),

    // Jetifier: List of regular expressions for libraries that should not be jetified
    JETIFIER_IGNORE_LIST("android.jetifier.ignorelist", ApiStage.Experimental),

    // Lint: Allow customization of the heap size of the process started to run lint
    LINT_HEAP_SIZE("android.experimental.lint.heapSize", ApiStage.Experimental),

    /* ---------------
     * DEPRECATED APIs
     */

    /* ------------
     * REMOVED APIs
     */

    @Suppress("unused")
    BUILD_CACHE_DIR(
        "android.buildCacheDir",
        ApiStage.Removed(Version.VERSION_7_0, "The Android-specific build caches were superseded by the Gradle build cache (https://docs.gradle.org/current/userguide/build_cache.html).")
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
