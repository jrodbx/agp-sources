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
import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.TestFixtures
import com.android.build.api.variant.impl.KmpAndroidCompilationType
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
import com.android.build.gradle.internal.dsl.KmpOptimizationImpl
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.LibraryKeepRulesImpl
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME
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
    withJava: Boolean,
): KmpComponentDslInfoImpl(
    extension, services, withJava
), KmpVariantDslInfo {

    override val componentType = ComponentTypeImpl.KMP_ANDROID
    override val componentIdentity = ComponentIdentityImpl(
        KmpAndroidCompilationType.MAIN.defaultSourceSetName
    )

    override val aarMetadata: AarMetadata
        get() = extension.aarMetadata

    override val namespace: Provider<String> by lazy {
        extension.namespace?.let { services.provider { it } }
            ?: throw RuntimeException(
                "Namespace not specified. Specify a namespace in the module's build file like so:\n" +
                        "kotlin {\n" +
                        "    $ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME {\n" +
                        "        namespace = \"com.example.namespace\"\n" +
                        "    }\n" +
                        "}\n"
            )
    }

    override val maxSdkVersion: Int?
        get() = extension.maxSdk

    override val packaging: Packaging
        get() = extension.packaging

    override val testInstrumentationRunnerArguments: Map<String, String>
        get() = (extension as KotlinMultiplatformAndroidExtensionImpl).androidTestOnDeviceOptions
            ?.instrumentationRunnerArguments ?: emptyMap()

    override val experimentalProperties: Map<String, Any>
        get() = extension.experimentalProperties

    override val optimizationDslInfo: OptimizationDslInfo by lazy(LazyThreadSafetyMode.NONE) {
        KmpOptimizationDslInfoImpl(
            extension, services, buildDirectory
        )
    }

    override val enabledUnitTest: Boolean
        get() = (extension as KotlinMultiplatformAndroidExtensionImpl).androidTestOnJvmOptions != null
    override val enableAndroidTest: Boolean
        get() = (extension as KotlinMultiplatformAndroidExtensionImpl).androidTestOnDeviceOptions != null

    // not supported
    override val targetSdkVersion: MutableAndroidVersion? = null

    override val testFixtures: TestFixtures? = null

    override val shadersDslInfo: ShadersDslInfo? = null
    override val nativeBuildDslInfo: NativeBuildDslInfo? = null
    override val renderscriptDslInfo: RenderscriptDslInfo? = null
    override val buildConfigDslInfo: BuildConfigDslInfo? = null
    override val manifestPlaceholdersDslInfo: ManifestPlaceholdersDslInfo? = null

    class KmpOptimizationDslInfoImpl(
        private val extension: KotlinMultiplatformAndroidExtension,
        private val services: VariantServices,
        private val buildDirectory: DirectoryProperty
    ): OptimizationDslInfo {

        private val keepRules =
            (extension.optimization as KmpOptimizationImpl).keepRules as LibraryKeepRulesImpl

        override val ignoreFromInKeepRules: Set<String>
            get() = keepRules.ignoreFrom
        override val ignoreFromAllExternalDependenciesInKeepRules: Boolean
            get() = keepRules.ignoreFromAllExternalDependencies
        override val ignoreFromInBaselineProfile: Set<String>
            get() = emptySet()
        override val ignoreFromAllExternalDependenciesInBaselineProfile: Boolean
            get() = false

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

                override fun getPostprocessingFeatures(): PostprocessingFeatures? = null

                override fun codeShrinkerEnabled(): Boolean = extension.optimization.minify

                // No android resources
                override fun resourcesShrinkingEnabled(): Boolean = false
                override fun hasPostProcessingConfiguration(): Boolean = false

                override fun getProguardFiles(type: ProguardFileType): Collection<File> {
                    return when (type) {
                        ProguardFileType.EXPLICIT -> extension.optimization.keepRules.files
                        ProguardFileType.TEST -> extension.optimization.testKeepRules.files
                        ProguardFileType.CONSUMER -> extension.optimization.consumerKeepRules.files
                    }
                }

            }
        }

        override fun gatherProguardFiles(type: ProguardFileType): Collection<File> {
            return postProcessingOptions.getProguardFiles(type)
        }
    }
}
