/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.core

import com.android.builder.model.AndroidProject
import com.android.builder.model.ArtifactMetaData
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.GradleBuildVariant

/**
 * Type of component.
 *
 * Each plugin will produce a main component (variant) with multiple nested components within the
 * variant.
 * For example, the library plugin produces a main variant of type [ComponentTypeImpl.LIBRARY] and
 * might have attached components of type [ComponentTypeImpl.UNIT_TEST],
 * [ComponentTypeImpl.ANDROID_TEST] and [ComponentTypeImpl.TEST_FIXTURES].
 */
interface ComponentType {
    /**
     * Returns true is the variant outputs an AAR.
     */
    val isAar: Boolean

    /**
     * Returns true is the variant outputs an APK.
     */
    val isApk: Boolean

    /**
     * Returns true if the variant is a base module. This is only true if it can have features.
     * If the variant can never have feature (TEST modules for instance), then this is false.
     */
    val isBaseModule: Boolean

    /**
     * Returns true if the variant is a dynamic feature i.e. an optional apk.
     */
    val isDynamicFeature: Boolean

    /**
     * Returns true if the variant publishes artifacts to meta-data.
     */
    val publishToMetadata: Boolean

    /**
     * Returns true if the variant publishes artifacts to external repositories.
     */
    val publishToRepository: Boolean

    /**
     * Returns true if the variant publishes artifacts to other modules
     */
    val publishToOtherModules: Boolean

    /**
     * Returns true if this is a nested component in the module (either a test component or
     * a testFixtures component).
     */
    val isNestedComponent: Boolean

    /**
     * Returns true if this is the test component of the module.
     */
    val isTestComponent: Boolean

    /**
     * Returns true if this is a test fixtures component of the module.
     */
    val isTestFixturesComponent: Boolean

    /**
     * Returns true if this is a separate test module.
     */
    val isSeparateTestProject: Boolean

    /**
     * Returns true if the variant is a test variant, whether this is the test component of a module
     * (testing the prod component of the same module) or a separate test-only module.
     */
    val isForTesting: Boolean
    /**
     * Returns true if the variant has test components
     */
    val hasTestComponents: Boolean
    /**
     * Returns prefix used for naming source directories. This is an empty string in
     * case of non-testing variants and a camel case string otherwise, e.g. "androidTest".
     */
    val prefix: String
    /**
     * Returns suffix used for naming Gradle tasks. This is an empty string in
     * case of non-testing variants and a camel case string otherwise, e.g. "AndroidTest".
     */
    val suffix: String
    /**
     * Whether the artifact type supports only a single build type.
     */
    val isSingleBuildType: Boolean
    /**
     * Returns the name used in the builder model for artifacts that correspond to this variant
     * type.
     */
    val artifactName: String
    /**
     * Returns the artifact type used in the builder model.
     */
    val artifactType: Int
    /**
     * Whether the artifact type should export the data binding class list.
     */
    val isExportDataBindingClassList: Boolean
    /**
     * Returns the corresponding variant type used by the analytics system.
     */
    val analyticsVariantType: GradleBuildVariant.VariantType
    /**
     * Whether the manifest file is required to exist.
     */
    val requiresManifest: Boolean

    val name: String

    companion object {
        const val ANDROID_TEST_PREFIX = "androidTest"
        const val ANDROID_TEST_SUFFIX = "AndroidTest"
        const val UNIT_TEST_PREFIX = "test"
        const val UNIT_TEST_SUFFIX = "UnitTest"
        const val TEST_FIXTURES_PREFIX = "testFixtures"
        const val TEST_FIXTURES_SUFFIX = "TestFixtures"
        const val PREVIEW_SCREENSHOT_PREFIX = "previewScreenshot"
        const val PREVIEW_SCREENSHOT_UPDATE_PREFIX = "previewScreenshotUpdate"
        const val PREVIEW_SCREENSHOT_RENDER_PREFIX = "previewScreenshotRender"
        const val SCREENSHOT_TEST_PREFIX = "screenshotTest"
        const val SCREENSHOT_TEST_SUFFIX = "ScreenshotTest"

        val testComponents: ImmutableList<ComponentType>
            get() {
                val result = ImmutableList.builder<ComponentType>()
                for (componentType in ComponentTypeImpl.values()) {
                    if (componentType.isTestComponent) {
                        result.add(componentType)
                    }
                }
                return result.build()
            }

    }
}

enum class ComponentTypeImpl(
    override val isAar: Boolean = false,
    override val isApk: Boolean = false,
    override val isBaseModule: Boolean = false,
    override val isDynamicFeature: Boolean = false,
    override val publishToMetadata: Boolean = false,
    override val publishToRepository: Boolean = false,
    override val publishToOtherModules: Boolean = false,
    override val isTestFixturesComponent: Boolean = false,
    override val isForTesting: Boolean = false,
    override val isSeparateTestProject: Boolean = false,
    override val prefix: String,
    override val suffix: String,
    override val isSingleBuildType: Boolean = false,
    override val artifactName: String,
    override val artifactType: Int,
    override val isExportDataBindingClassList: Boolean = false,
    override val analyticsVariantType: GradleBuildVariant.VariantType,
): ComponentType {
    BASE_APK(
        isApk = true,
        isBaseModule = true,
        publishToRepository = true,
        publishToOtherModules = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.APPLICATION,
    ),
    OPTIONAL_APK(
        isApk = true,
        isDynamicFeature = true,
        publishToMetadata = true,
        publishToOtherModules = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.OPTIONAL_APK
    ),
    LIBRARY(
        isAar = true,
        publishToRepository = true,
        publishToOtherModules = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        isExportDataBindingClassList = true,
        analyticsVariantType = GradleBuildVariant.VariantType.LIBRARY
    ),
    TEST_APK(
        isApk = true,
        isForTesting = true,
        isSeparateTestProject = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.TEST_APK),
    JAVA_LIBRARY(
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_JAVA,
        analyticsVariantType = GradleBuildVariant.VariantType.JAVA_LIBRARY),
    ANDROID_TEST(
        isApk = true,
        isForTesting = true,
        prefix = ComponentType.ANDROID_TEST_PREFIX,
        suffix = ComponentType.ANDROID_TEST_SUFFIX,
        isSingleBuildType = true,
        artifactName = "_android_test_",
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.ANDROID_TEST),
    UNIT_TEST(
        isForTesting = true,
        prefix = ComponentType.UNIT_TEST_PREFIX,
        suffix = ComponentType.UNIT_TEST_SUFFIX,
        isSingleBuildType = true,
        artifactName = "_unit_test_",
        artifactType = ArtifactMetaData.TYPE_JAVA,
        analyticsVariantType = GradleBuildVariant.VariantType.UNIT_TEST),
    SCREENSHOT_TEST(
        isForTesting = true,
        prefix = ComponentType.SCREENSHOT_TEST_PREFIX,
        suffix = ComponentType.SCREENSHOT_TEST_SUFFIX,
        isSingleBuildType = true,
        artifactName = "_screenshot_test_",
        artifactType = ArtifactMetaData.TYPE_JAVA,
        analyticsVariantType = GradleBuildVariant.VariantType.SCREENSHOT_TEST),
    TEST_FIXTURES(
        isAar = true,
        prefix = ComponentType.TEST_FIXTURES_PREFIX,
        suffix = ComponentType.TEST_FIXTURES_SUFFIX,
        isTestFixturesComponent = true,
        publishToOtherModules = true,
        publishToRepository = true,
        artifactName = AndroidProject.ARTIFACT_TEST_FIXTURES,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.TEST_FIXTURES
    ), KMP_ANDROID(
        isAar = true,
        publishToRepository = true,
        publishToOtherModules = true,
        prefix = "",
        suffix = "",
        artifactName = AndroidProject.ARTIFACT_MAIN,
        artifactType = ArtifactMetaData.TYPE_ANDROID,
        analyticsVariantType = GradleBuildVariant.VariantType.KOTLIN_MULTIPLATFORM_ANDROID_LIBRARY,
    );

    override val isTestComponent: Boolean
        get() = isForTesting && this != TEST_APK

    override val hasTestComponents: Boolean
        get() = !isForTesting && !isTestFixturesComponent

    override val requiresManifest: Boolean
        get() = !isForTesting && !isTestFixturesComponent

    override val isNestedComponent: Boolean
        get() = isTestComponent || isTestFixturesComponent
}
