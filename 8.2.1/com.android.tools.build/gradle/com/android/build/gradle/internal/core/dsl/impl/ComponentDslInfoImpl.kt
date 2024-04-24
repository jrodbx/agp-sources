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

package com.android.build.gradle.internal.core.dsl.impl

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.MergedJavaCompileOptions
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.MultiVariantComponentDslInfo
import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.AndroidResourcesDslInfoImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.ComponentType
import com.google.common.collect.ImmutableMap
import org.gradle.api.file.DirectoryProperty

internal abstract class ComponentDslInfoImpl internal constructor(
    override val componentIdentity: ComponentIdentity,
    final override val componentType: ComponentType,
    protected val defaultConfig: DefaultConfig,
    /**
     * Public because this is needed by the old Variant API. Nothing else should touch this.
     */
    val buildTypeObj: BuildType,
    final override val productFlavorList: List<ProductFlavor>,
    protected val services: VariantServices,
    private val buildDirectory: DirectoryProperty,
    protected val extension: CommonExtension<*, *, *, *, *>
): ComponentDslInfo, MultiVariantComponentDslInfo {

    /**
     * This should be mostly private and not used outside this class, but is still public for legacy
     * variant API and model v1 support.
     *
     * At some point we should remove this and rely on each property to combine dsl values in the
     * manner that it is meaningful for the property. Take a look at
     * [VariantDslInfoImpl.initApplicationId] for guidance on how will that look like.
     *
     * DO NOT USE. You should mostly use the interfaces which does not give access to this.
     */
    val mergedFlavor: MergedFlavor by lazy {
        MergedFlavor.mergeFlavors(
            defaultConfig,
            productFlavorList.map { it as com.android.build.gradle.internal.dsl.ProductFlavor },
            applicationId,
            services
        )
    }

    final override val javaCompileOptionsSetInDSL = MergedJavaCompileOptions()

    init {
        computeMergedOptions(
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            javaCompileOptionsSetInDSL,
            { javaCompileOptions as JavaCompileOptions },
            { javaCompileOptions as JavaCompileOptions }
        )
    }

    // merged flavor delegates

    override val missingDimensionStrategies: ImmutableMap<String, AbstractProductFlavor.DimensionRequest>
        get() = ImmutableMap.copyOf(mergedFlavor.missingDimensionStrategies)

    // helper methods

    override val androidResourcesDsl: AndroidResourcesDslInfo by lazy {
        AndroidResourcesDslInfoImpl(
            defaultConfig, buildTypeObj, productFlavorList, mergedFlavor, extension
        )
    }

    /**
     * Combines all the appId suffixes into a single one.
     *
     * The suffixes are separated by '.' whether their first char is a '.' or not.
     */
    protected fun computeApplicationIdSuffix(): String {
        // for the suffix we combine the suffix from all the flavors. However, we're going to
        // want the higher priority one to be last.
        val suffixes = mutableListOf<String>()
        defaultConfig.applicationIdSuffix?.let {
            suffixes.add(it)
        }

        suffixes.addAll(
            productFlavorList
                .asSequence()
                .filterIsInstance(ApplicationProductFlavor::class.java)
                .mapNotNull { it.applicationIdSuffix })

        // then we add the build type after.
        (buildTypeObj as? ApplicationBuildType)?.applicationIdSuffix?.let {
            suffixes.add(it)
        }
        val nonEmptySuffixes = suffixes.filter { it.isNotEmpty() }
        return if (nonEmptySuffixes.isNotEmpty()) {
            ".${nonEmptySuffixes.joinToString(separator = ".", transform = { it.removePrefix(".") })}"
        } else {
            ""
        }
    }
}
