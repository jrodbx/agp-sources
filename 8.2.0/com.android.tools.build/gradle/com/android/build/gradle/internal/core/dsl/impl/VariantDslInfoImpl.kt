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

import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.NativeBuildDslInfoImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentType
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

internal abstract class VariantDslInfoImpl internal constructor(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    protected val dataProvider: ManifestDataProvider,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    extension: CommonExtension<*, *, *, *, *>
) : ConsumableComponentDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    services,
    buildDirectory,
    extension
), VariantDslInfo {

    // merged flavor delegates

    override val minSdkVersion: MutableAndroidVersion
        // if there's a testedVariant, return its value, otherwise return the merged flavor
        // value. If there's no value set, then the default is just the first API Level: 1
        get() = mergedFlavor.minSdkVersion?.let { MutableAndroidVersion(it.apiLevel, it.codename) }
            ?: MutableAndroidVersion(1)
    override val maxSdkVersion: Int?
        get() = mergedFlavor.maxSdkVersion
    override val targetSdkVersion: MutableAndroidVersion?
        // if there's a testedVariant, return its value, otherwise return the merged flavor
        // value. If there's no value set, then return null
        get() =  mergedFlavor.targetSdkVersion?.let { MutableAndroidVersion(it.apiLevel, it.codename) }

    // extension delegates

    // For main variants we get the namespace from the DSL.
    override val namespace: Provider<String>
        get() = extension.namespace?.let { services.provider { it } }
            ?: throw RuntimeException(
                "Namespace not specified. Specify a namespace in the module's build file. " +
                        "See https://d.android.com/r/tools/upgrade-assistant/set-namespace for " +
                        "information about setting the namespace.\n\n" +
                        "If you've specified the package attribute in the source " +
                        "AndroidManifest.xml, you can use the AGP Upgrade Assistant to migrate " +
                        "to the namespace value in the build file. Refer to " +
                        "https://d.android.com/r/tools/upgrade-assistant/agp-upgrade-assistant " +
                        "for general information about using the AGP Upgrade Assistant."
            )

    override val applicationId: Property<String> by lazy {
        services.newPropertyBackingDeprecatedApi(
            String::class.java,
            initApplicationId()
        )
    }
    override val packaging: Packaging
        get() = extension.packagingOptions
    override val experimentalProperties: Map<String, Any>
        get() = extension.experimentalProperties
    override val nativeBuildDslInfo by lazy(LazyThreadSafetyMode.NONE) {
        NativeBuildDslInfoImpl(
            componentType,
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            extension
        )
    }

    // helper methods

    private fun initApplicationId(): Provider<String> {
        // get first non null appId from flavors/default config
        val appIdFromFlavors =
            productFlavorList
                .asSequence()
                .filterIsInstance(ApplicationProductFlavor::class.java)
                .map { it.applicationId }
                .firstOrNull { it != null }
                ?: defaultConfig.applicationId

        return if (appIdFromFlavors == null) {
            // No appId value set from DSL; use the namespace value from the DSL.
            namespace.map { "$it${computeApplicationIdSuffix()}" }
        } else {
            // use value from flavors/defaultConfig
            // needed to make nullability work in kotlinc
            val finalAppIdFromFlavors: String = appIdFromFlavors
            services.provider { "$finalAppIdFromFlavors${computeApplicationIdSuffix()}" }
        }
    }
}
