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

import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.ComponentType
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by all components.
 */
interface ComponentDslInfo {
    val componentIdentity: ComponentIdentity

    val componentType: ComponentType

    val missingDimensionStrategies: Map<String, AbstractProductFlavor.DimensionRequest>

    /**
     * Returns the application ID for this variant. This could be coming from the manifest or could
     * be overridden through the product flavors and/or the build type.
     *
     * @return the application ID
     */
    val applicationId: Property<String>

    /**
     * The namespace of the generated R and BuildConfig classes. Also, the namespace used to resolve
     * any relative class names that are declared in the AndroidManifest.xml.
     *
     * For test components, this is set to the `testNamespace` DSL value, if present, or else to the
     * DSL's `namespace` + ".test".
     */
    val namespace: Provider<String>

    /**
     * Return the minSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the minSdkVersion
     */
    val minSdkVersion: MutableAndroidVersion

    val javaCompileOptionsSetInDSL: JavaCompileOptions

    val androidResourcesDsl: AndroidResourcesDslInfo?
}
