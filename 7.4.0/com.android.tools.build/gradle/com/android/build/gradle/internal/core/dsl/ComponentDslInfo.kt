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

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.transform.Transform
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.core.PostProcessingOptions
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.ComponentType
import com.android.builder.model.VectorDrawablesOptions
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by all components.
 */
interface ComponentDslInfo: DimensionCombination {
    val componentIdentity: ComponentIdentity

    val componentType: ComponentType

    /** The list of product flavors. Items earlier in the list override later items.  */
    val productFlavorList: List<ProductFlavor>

    val missingDimensionStrategies: ImmutableMap<String, AbstractProductFlavor.DimensionRequest>

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
     * DSL's `namespace` + ".test", if present, or else to the `package` attribute in the test
     * AndroidManifest.xml, if present, or else to the `package` attribute in the main
     * AndroidManifest.xml with ".test" appended.
     *
     * For non-test components, this value comes from the namespace DSL element, if present, or from
     * the `package` attribute in the source AndroidManifest.xml if not specified in the DSL.
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

    val maxSdkVersion: Int?

    /**
     * Return the targetSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the targetSdkVersion
     */
    val targetSdkVersion: MutableAndroidVersion?

    val javaCompileOptionsSetInDSL: JavaCompileOptions

    val compileOptions: CompileOptions

    val transforms: List<Transform>

    val androidResources: AndroidResources

    val resourceConfigurations: ImmutableSet<String>

    val vectorDrawables: VectorDrawablesOptions

    val isPseudoLocalesEnabled: Boolean

    val isCrunchPngs: Boolean?

    @Deprecated("Can be removed once the AaptOptions crunch method is removed.")
    val isCrunchPngsDefault: Boolean

    /**
     * Returns a list of generated resource values.
     *
     *
     * Items can be either fields (instance of [com.android.builder.model.ClassField]) or
     * comments (instance of String).
     *
     * @return a list of items.
     */
    fun getResValues(): Map<ResValue.Key, ResValue>

    val postProcessingOptions: PostProcessingOptions

    /**
     * TODO(b/242515559): Clean this up
     */
    val isAndroidTestCoverageEnabled: Boolean

    fun gatherProguardFiles(type: ProguardFileType): Collection<File>
}
