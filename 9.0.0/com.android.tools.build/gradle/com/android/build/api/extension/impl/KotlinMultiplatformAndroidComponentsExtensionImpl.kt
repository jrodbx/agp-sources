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

package com.android.build.api.extension.impl

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import com.android.build.api.variant.KotlinMultiplatformAndroidVariantBuilder
import com.android.build.api.variant.VariantSelector
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.errors.IssueReporter
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import javax.inject.Inject

open class KotlinMultiplatformAndroidComponentsExtensionImpl@Inject constructor(
    val dslServices: DslServices,
    sdkComponents: SdkComponents,
    managedDeviceRegistry: ManagedDeviceRegistry,
    private val variantApiOperations: VariantApiOperationsRegistrar<KotlinMultiplatformAndroidLibraryExtension, KotlinMultiplatformAndroidVariantBuilder, KotlinMultiplatformAndroidVariant>,
    kmpExtension: KotlinMultiplatformAndroidLibraryExtension,
    private val androidTargetProvider: () -> KotlinMultiplatformAndroidLibraryTarget
) : KotlinMultiplatformAndroidComponentsExtension,
    AndroidComponentsExtensionImpl<KotlinMultiplatformAndroidLibraryExtension, KotlinMultiplatformAndroidVariantBuilder, KotlinMultiplatformAndroidVariant>(
        dslServices,
        sdkComponents,
        managedDeviceRegistry,
        variantApiOperations,
        kmpExtension
    ) {
    override fun beforeVariants(
        selector: VariantSelector,
        callback: (KotlinMultiplatformAndroidVariantBuilder) -> Unit
    ) {
        dslServices.issueReporter.reportWarning(
            IssueReporter.Type.GENERIC,
            "beforeVariants() API is not supported yet for KMP modules and will be ignored"
        )
    }

    override fun beforeVariants(
        selector: VariantSelector,
        callback: Action<KotlinMultiplatformAndroidVariantBuilder>
    ) {
        dslServices.issueReporter.reportWarning(
            IssueReporter.Type.GENERIC,
            "beforeVariants() API is not supported yet for KMP modules and will be ignored"
        )
    }

    override fun registerConfigurations(lowercaseAffix: String, useLegacyPrefix: Boolean) {
         androidTargetProvider.invoke().compilations.forEach { compilation ->
         val configurationName =
             getConfigurationName(lowercaseAffix, useLegacyPrefix, compilation.componentName)
         dslServices.configurations
             .maybeCreate(configurationName)
             .apply {
                 isCanBeResolved = false
                 isCanBeConsumed = false
                 isVisible = false
             }
        }
     }

    override fun getOperationCallback(
        resolvableConfigurationNameMapper: (String) -> String,
        globalConfiguration: Configuration?,
        lowercaseAffix: String,
        useLegacyPrefix: Boolean
    ): (KotlinMultiplatformAndroidVariant) -> Unit {
        val callback: (KotlinMultiplatformAndroidVariant) -> Unit = { variant ->
            val variantResolvableConfiguration =
                dslServices.configurations
                    .maybeCreate(resolvableConfigurationNameMapper(variant.name))
                    .apply {
                        isCanBeResolved = true
                        isCanBeConsumed = false
                        isVisible = false
                    }

            if (globalConfiguration?.allDependencies?.isNotEmpty() == true) {
                variantResolvableConfiguration.extendsFrom(globalConfiguration)
            }

            dslServices.configurations
                .findByName(getConfigurationName(lowercaseAffix, useLegacyPrefix, variant.name))
                ?.takeIf { it.allDependencies.isNotEmpty() }
                ?.let { variantResolvableConfiguration.extendsFrom(it) }

            variant.nestedComponents.forEach { component ->
                val componentResolvableConfiguration =
                    dslServices.configurations
                        .maybeCreate(resolvableConfigurationNameMapper(component.name))
                        .apply {
                            isCanBeResolved = true
                            isCanBeConsumed = false
                            isVisible = false
                        }
                if (globalConfiguration?.allDependencies?.isNotEmpty() == true) {
                    componentResolvableConfiguration.extendsFrom(globalConfiguration)
                }

                dslServices.configurations
                    .findByName(getConfigurationName(lowercaseAffix, useLegacyPrefix, component.name))
                    ?.takeIf { it.allDependencies.isNotEmpty() }
                    ?.let { componentResolvableConfiguration.extendsFrom(it) }
            }
        }
        return callback
    }
}
