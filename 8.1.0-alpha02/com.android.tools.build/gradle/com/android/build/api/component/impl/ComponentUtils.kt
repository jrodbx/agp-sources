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

@file:JvmName("ComponentUtils")

package com.android.build.api.component.impl

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidVersion
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.android.sdklib.AndroidTargetHash
import com.google.common.base.Strings

val ENABLE_LEGACY_API: String =
    "Turn on with by putting '${BooleanOption.ENABLE_LEGACY_API.propertyName}=true in gradle.properties'\n" +
            "Using this deprecated API may still fail, depending on usage of the new Variant API, like computing applicationId via a task output."

/**
 * AndroidResources block currently contains asset options, while disabling android resources
 * doesn't disable assets. To work around this, AndroidResources block is duplicated between
 * [AssetsCreationConfig] and [AndroidResourcesCreationConfig]. If android resources is disabled,
 * the value is returned from [AssetsCreationConfig].
 */
internal fun ComponentImpl<*>.getAndroidResources(): AndroidResources {
    return androidResourcesCreationConfig?.androidResources
        ?: assetsCreationConfig.androidResources
}

/**
 * Determine if the final output should be marked as testOnly to prevent uploading to Play
 * store.
 *
 * <p>Uploading to Play store is disallowed if:
 *
 * <ul>
 *   <li>An injected option is set (usually by the IDE for testing purposes).
 *   <li>compileSdkVersion, minSdkVersion or targetSdkVersion is a preview
 * </ul>
 *
 * <p>This value can be overridden by the OptionalBooleanOption.IDE_TEST_ONLY property.
 *
 * @param variant {@link VariantCreationConfig} for this variant scope.
 */
internal fun ApkCreationConfig.isTestApk(): Boolean {
    val projectOptions = services.projectOptions

    return projectOptions.get(OptionalBooleanOption.IDE_TEST_ONLY) ?: (
            !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
            || projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API) != null
            || AndroidTargetHash.getVersionFromHash(global.compileSdkHashString)?.isPreview == true
            || minSdkVersion.codename != null
            || targetSdkVersion.codename != null)
}

internal fun<T> ComponentCreationConfig.warnAboutAccessingVariantApiValueForDisabledFeature(
    featureName: String,
    apiName: String,
    value: T
): T {
    services.issueReporter.reportWarning(
        IssueReporter.Type.ACCESSING_DISABLED_FEATURE_VARIANT_API,
        "Accessing value $apiName in variant $name has no effect as the feature" +
                " $featureName is disabled."
    )
    return value
}

internal fun NestedComponentCreationConfig.getMainTargetSdkVersion(): AndroidVersion =
    when (mainVariant) {
        is ApkCreationConfig -> (mainVariant as ApkCreationConfig).targetSdkVersion
        is LibraryCreationConfig -> (mainVariant as LibraryCreationConfig).targetSdkVersion
        else -> minSdkVersion
    }
