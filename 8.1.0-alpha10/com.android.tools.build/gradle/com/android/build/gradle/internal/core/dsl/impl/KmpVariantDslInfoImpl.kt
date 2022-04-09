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

import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.api.dsl.AarMetadata
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.TestFixtures
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.core.PostProcessingOptions
import com.android.build.gradle.internal.core.dsl.KmpVariantDslInfo
import com.android.build.gradle.internal.core.dsl.features.BuildConfigDslInfo
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.core.dsl.features.NativeBuildDslInfo
import com.android.build.gradle.internal.core.dsl.features.OptimizationDslInfo
import com.android.build.gradle.internal.core.dsl.features.RenderscriptDslInfo
import com.android.build.gradle.internal.core.dsl.features.ShadersDslInfo
import com.android.build.gradle.internal.dsl.KeepRulesImpl
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.gradle.internal.dsl.OptimizationImpl
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentTypeImpl
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import java.io.File

class KmpVariantDslInfoImpl(
    extension: KotlinMultiplatformAndroidExtension,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
): KmpComponentDslInfoImpl(
    extension, services
), KmpVariantDslInfo {

    override val componentType = ComponentTypeImpl.KMP_ANDROID
    override val componentIdentity = ComponentIdentityImpl("kotlinAndroid")

    override val aarMetadata: AarMetadata
        get() = extension.aarMetadata

    override val namespace: Provider<String> by lazy {
        extension.namespace?.let { services.provider { it } }
            ?: throw RuntimeException(
                "Namespace not specified. Please " +
                        "specify a namespace for the generated R and BuildConfig classes via " +
                        "android.namespace in the module's build.gradle file like so:\n\n" +
                        "android {\n" +
                        "    namespace 'com.example.namespace'\n" +
                        "}\n\n"
            )
    }

    override val maxSdkVersion: Int?
        get() = extension.maxSdkVersion

    override val packaging: Packaging
        get() = extension.packagingOptions

    override val testInstrumentationRunnerArguments: Map<String, String>
        get() = extension.testInstrumentationRunnerArguments

    override val experimentalProperties: Map<String, Any>
        get() = extension.experimentalProperties

    override val optimizationDslInfo: OptimizationDslInfo by lazy(LazyThreadSafetyMode.NONE) {
        KmpOptimizationDslInfoImpl(
            extension, services, buildDirectory
        )
    }

    override val enabledUnitTest: Boolean
        get() = extension.enableUnitTest
    override val enableAndroidTest: Boolean
        get() = extension.enableAndroidTest

    // not supported
    override val targetSdkVersion: MutableAndroidVersion? = null

    override val testFixtures: TestFixtures? = null

    override val shadersDslInfo: ShadersDslInfo? = null
    override val nativeBuildDslInfo: NativeBuildDslInfo? = null
    override val renderscriptDslInfo: RenderscriptDslInfo? = null
    override val buildConfigDslInfo: BuildConfigDslInfo? = null
    override val manifestPlaceholdersDslInfo: ManifestPlaceholdersDslInfo? = null

    // TODO(b/243387425): support lint
    override val lintOptions: Lint
        get() {
            throw IllegalAccessException("Not supported")
        }

    class KmpOptimizationDslInfoImpl(
        private val extension: KotlinMultiplatformAndroidExtension,
        private val services: VariantServices,
        private val buildDirectory: DirectoryProperty
    ): OptimizationDslInfo {

        private val keepRules =
            (extension.optimization as OptimizationImpl).keepRules as KeepRulesImpl

        override val ignoredLibraryKeepRules: Set<String>
            get() = keepRules.dependencies
        override val ignoreAllLibraryKeepRules: Boolean
            get() = keepRules.ignoreAllDependencies

        override fun getProguardFiles(into: ListProperty<RegularFile>) {
            val result: MutableList<File> = ArrayList(gatherProguardFiles(ProguardFileType.EXPLICIT))
            if (result.isEmpty()) {
                result.addAll(postProcessingOptions.getDefaultProguardFiles())
            }

            val projectDir = services.projectInfo.projectDirectory
            result.forEach { file ->
                into.add(projectDir.file(file.absolutePath))
            }
        }

        override val postProcessingOptions: PostProcessingOptions by lazy {
            object: PostProcessingOptions {
                override fun getDefaultProguardFiles(): List<File> =
                    listOf(
                        ProguardFiles.getDefaultProguardFile(
                            ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName,
                            buildDirectory
                        )
                    )

                override fun getPostprocessingFeatures(): PostprocessingFeatures {
                    return PostprocessingFeatures(
                        isRemoveUnusedCode = extension.isMinifyEnabled,
                        isObfuscate = extension.isMinifyEnabled,
                        isOptimize = extension.isMinifyEnabled
                    )
                }

                override fun codeShrinkerEnabled(): Boolean = extension.isMinifyEnabled

                // No android resources
                override fun resourcesShrinkingEnabled(): Boolean = false
                override fun hasPostProcessingConfiguration(): Boolean = false

                override fun getProguardFiles(type: ProguardFileType): Collection<File> {
                    return when (type) {
                        ProguardFileType.EXPLICIT -> extension.proguardFiles
                        ProguardFileType.TEST -> extension.testProguardFiles
                        ProguardFileType.CONSUMER -> extension.consumerProguardFiles
                    }
                }

            }
        }

        override fun gatherProguardFiles(type: ProguardFileType): Collection<File> {
            return postProcessingOptions.getProguardFiles(type)
        }
    }
}
