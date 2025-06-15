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
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.core.dsl.features.DexingDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.DexingDslInfoImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalApplicationExtension
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.StringOption
import com.android.builder.core.ComponentType
import com.android.builder.errors.IssueReporter
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider

internal class ApplicationVariantDslInfoImpl(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    dataProvider: ManifestDataProvider,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    override val publishInfo: VariantPublishingInfo,
    private val signingConfigOverride: SigningConfig?,
    extension: InternalApplicationExtension
) : TestedVariantDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    dataProvider,
    services,
    buildDirectory,
    extension
), ApplicationVariantDslInfo {

    private val applicationBuildType = buildTypeObj as ApplicationBuildType

    override val isDebuggable: Boolean
        get() = ProfilingMode.getProfilingModeType(
            services.projectOptions[StringOption.PROFILING_MODE]
        ).isDebuggable ?: applicationBuildType.isDebuggable

    override val isProfileable: Boolean
        get() {
            val fromProfilingModeOption = ProfilingMode.getProfilingModeType(
                services.projectOptions[StringOption.PROFILING_MODE]
            ).isProfileable
            return when {
                fromProfilingModeOption != null -> {
                    fromProfilingModeOption
                }

                applicationBuildType.isProfileable && isDebuggable -> {
                    val projectName = services.projectInfo.name
                    val message =
                        ":$projectName build type '${buildType}' can only have debuggable or profileable enabled.\n" +
                                "Only one of these options can be used at a time.\n" +
                                "Recommended action: Only set one of debuggable=true and profileable=true.\n"
                    services.issueReporter.reportWarning(IssueReporter.Type.GENERIC, message)
                    // Disable profileable when profileable and debuggable are both enabled.
                    false
                }
                else -> applicationBuildType.isProfileable
            }
        }

    override val signingConfigResolver: SigningConfigResolver? by lazy {
        SigningConfigResolver.create(buildTypeObj, mergedFlavor, signingConfigOverride, extension, services)
    }

    override val versionName: Provider<String> by lazy {
        // If the version name from the flavors is null, then we read from the manifest and combine
        // with suffixes, unless it's a test at which point we just return.
        // If the name is not-null, we just combine it with suffixes
        val versionNameFromFlavors =
            productFlavorList
                .asSequence()
                .filterIsInstance(ApplicationProductFlavor::class.java)
                .map { it.versionName }
                .firstOrNull { it != null }
                ?: defaultConfig.versionName
        val versionNameSuffix = computeVersionNameSuffix()
        if (versionNameFromFlavors == null) {
            // rely on manifest value
            // using map will allow us to keep task dependency should the manifest be generated or
            // transformed via a task.
            dataProvider.manifestData.map {
                it.versionName?.let { versionName ->
                    "$versionName$versionNameSuffix"
                } ?: ""
            }
        } else {
            // use value from flavors
            services.provider { "$versionNameFromFlavors$versionNameSuffix" }
        }
    }

    override val versionCode: Provider<Int> by lazy {
        // If the version code from the flavors is null, then we read from the manifest and combine
        // with suffixes, unless it's a test at which point we just return.
        // If the name is not-null, we just combine it with suffixes
        val versionCodeFromFlavors =
            productFlavorList
                .asSequence()
                .filterIsInstance(ApplicationProductFlavor::class.java)
                .map { it.versionCode }
                .firstOrNull { it != null }
                ?: defaultConfig.versionCode

        if (versionCodeFromFlavors == null) {
            // rely on manifest value
            // using map will allow us to keep task dependency should the manifest be generated or
            // transformed via a task.
            dataProvider.manifestData.map {
                it.versionCode ?: -1
            }
        } else {
            // use value from flavors
            services.provider { versionCodeFromFlavors }
        }
    }
    override val isWearAppUnbundled: Boolean?
        get() = mergedFlavor.wearAppUnbundled
    override val isEmbedMicroApp: Boolean
        get() = applicationBuildType.isEmbedMicroApp

    override val dexingDslInfo: DexingDslInfo by lazy {
        DexingDslInfoImpl(
            buildTypeObj, mergedFlavor
        )
    }

    override val generateLocaleConfig: Boolean by lazy {
        extension.androidResources.generateLocaleConfig
    }

    override val includeVcsInfo: Boolean?
        get() = applicationBuildType.vcsInfo.include

    override val compileSdk: Int?
        get() = extension.compileSdk

    private fun computeVersionNameSuffix(): String {
        // for the suffix we combine the suffix from all the flavors. However, we're going to
        // want the higher priority one to be last.
        val suffixes = mutableListOf<String>()
        defaultConfig.versionNameSuffix?.let {
            suffixes.add(it)
        }

        suffixes.addAll(
            productFlavorList
                .asSequence()
                .filterIsInstance(ApplicationProductFlavor::class.java)
                .mapNotNull { it.versionNameSuffix })

        // then we add the build type after.
        applicationBuildType.versionNameSuffix?.let {
            suffixes.add(it)
        }

        return if (suffixes.isNotEmpty()) {
            suffixes.joinToString(separator = "")
        } else {
            ""
        }
    }
}
