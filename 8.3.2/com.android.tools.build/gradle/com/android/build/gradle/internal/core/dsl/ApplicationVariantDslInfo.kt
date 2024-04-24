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

package com.android.build.gradle.internal.core.dsl

import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo
import org.gradle.api.provider.Provider

/**
 * Represents the dsl info for an application variant, initialized from the DSL object model
 * (extension, default config, build type, flavors)
 *
 * This class allows querying for the values set via the DSL model.
 *
 * Use [DslInfoBuilder] to instantiate.
 *
 * @see [com.android.build.gradle.internal.component.ApplicationCreationConfig]
 */
interface ApplicationVariantDslInfo:
    VariantDslInfo,
    ApkProducingComponentDslInfo,
    PublishableComponentDslInfo,
    TestedVariantDslInfo,
    MultiVariantComponentDslInfo {

    /**
     * Returns the version name for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest. A suffix may be specified by the build
     * type.
     *
     * @return the version name or null if none defined
     */
    val versionName: Provider<String?>

    /**
     * Returns the version code for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest.
     *
     * @return the version code or -1 if there was none defined.
     */
    val versionCode: Provider<Int?>

    val isWearAppUnbundled: Boolean?

    val isEmbedMicroApp: Boolean

    val isProfileable: Boolean

    val generateLocaleConfig: Boolean

    val includeVcsInfo: Boolean?

    override val androidResourcesDsl: AndroidResourcesDslInfo

    val compileSdk: Int?
}
