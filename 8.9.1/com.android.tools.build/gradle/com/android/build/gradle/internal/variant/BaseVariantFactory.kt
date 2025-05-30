/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.build.gradle.internal.variant

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.DeviceTestImpl
import com.android.build.api.component.impl.HostTestImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.DeviceTestBuilderImpl
import com.android.build.api.variant.impl.HostTestBuilderImpl
import com.android.build.gradle.internal.api.BaseVariantImpl
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.HostTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.TestFixturesComponentDslInfo
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.UnitTestBuildFeatureValuesImpl
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import org.gradle.api.Project

/** Common superclass for all [VariantFactory] implementations.  */
abstract class BaseVariantFactory<VariantBuilderT : VariantBuilder, VariantDslInfoT : VariantDslInfo, VariantT : VariantCreationConfig>
    (@JvmField protected val dslServices: DslServices) : VariantFactory<VariantBuilderT, VariantDslInfoT, VariantT> {
    override fun createTestFixtures(
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        dslInfo: TestFixturesComponentDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        taskContainer: MutableTaskContainer,
        mainVariant: VariantCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig
    ): TestFixturesCreationConfig {
        return dslServices.newInstance(
            TestFixturesImpl::class.java,
            componentIdentity,
            buildFeatures,
            dslInfo,
            variantDependencies,
            variantSources,
            paths,
            artifacts,
            taskContainer,
            mainVariant,
            variantServices,
            taskCreationServices,
            globalConfig
        )
    }

    override fun createUnitTest(
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        dslInfo: HostTestComponentDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantData: TestVariantData,
        taskContainer: MutableTaskContainer,
        testedVariantProperties: VariantCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig,
        hostTestBuilder: HostTestBuilderImpl
    ): HostTestCreationConfig {
        return dslServices.newInstance(
            HostTestImpl::class.java,
            componentIdentity,
            createUnitTestBuildFeatures(buildFeatures),
            dslInfo,
            variantDependencies,
            variantSources,
            paths,
            artifacts,
            variantData,
            taskContainer,
            testedVariantProperties,
            variantServices,
            taskCreationServices,
            globalConfig,
            hostTestBuilder
        )
    }

    override fun createHostTest(
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        dslInfo: HostTestComponentDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantData: TestVariantData,
        taskContainer: MutableTaskContainer,
        testedVariantProperties: VariantCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig,
        hostTestBuilder: HostTestBuilderImpl
    ): HostTestCreationConfig {
        return dslServices.newInstance(
            HostTestImpl::class.java,
            componentIdentity,
            createUnitTestBuildFeatures(buildFeatures),
            dslInfo,
            variantDependencies,
            variantSources,
            paths,
            artifacts,
            variantData,
            taskContainer,
            testedVariantProperties,
            variantServices,
            taskCreationServices,
            globalConfig,
            hostTestBuilder
        )
    }

    override fun createAndroidTest(
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        dslInfo: AndroidTestComponentDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantData: TestVariantData,
        taskContainer: MutableTaskContainer,
        testedVariantProperties: VariantCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig,
        deviceTestBuilder: DeviceTestBuilderImpl
    ): DeviceTestCreationConfig {
        return dslServices.newInstance(
            DeviceTestImpl::class.java,
            componentIdentity,
            buildFeatures,
            dslInfo,
            variantDependencies,
            variantSources,
            paths,
            artifacts,
            variantData,
            taskContainer,
            testedVariantProperties,
            variantServices,
            taskCreationServices,
            globalConfig,
            deviceTestBuilder
        )
    }

    override fun createVariantApi(
        component: ComponentCreationConfig,
        variantData: BaseVariantData,
        readOnlyObjectProvider: ReadOnlyObjectProvider
    ): BaseVariantImpl? {
        val implementationClass =
            variantImplementationClass

        @Suppress("DEPRECATION")
        return dslServices.newInstance(
            implementationClass,
            variantData,
            component,
            dslServices,
            readOnlyObjectProvider,
            dslServices.domainObjectContainer(com.android.build.VariantOutput::class.java)
        )
    }

    override fun preVariantCallback(
        project: Project,
        dslExtension: CommonExtension<*, *, *, *, *, *>,
        model: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
    ) {
        if (project.pluginManager.hasPlugin(ANDROID_APT_PLUGIN_NAME)) {
            dslServices
                .issueReporter
                .reportError(
                    IssueReporter.Type.INCOMPATIBLE_PLUGIN,
                    "android-apt plugin is incompatible with the Android Gradle plugin.  "
                            + "Please use 'annotationProcessor' configuration "
                            + "instead.",
                    "android-apt"
                )
        }

        validateBuildConfig(model, dslExtension.buildFeatures.buildConfig)
        validateResValues(model, dslExtension.buildFeatures.resValues)
    }

    private fun validateBuildConfig(
        model: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
        buildConfig: Boolean?
    ) {
        val finalResValues = buildConfig ?:
            dslServices.projectOptions[BooleanOption.BUILD_FEATURE_BUILDCONFIG]

        if (!finalResValues) {
            val issueReporter = dslServices.issueReporter

            val suggestion = """
                To enable the feature, add the following to your module-level build.gradle:
                `android.buildFeatures.buildConfig true`
                """.trimIndent()
            if (model.defaultConfigData.defaultConfig.buildConfigFields.isNotEmpty()) {
                issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    """
                    defaultConfig contains custom BuildConfig fields, but the feature is disabled.
                    $suggestion
                    """.trimIndent()
                )
            }

            for (buildType in model.buildTypes.values) {
                if (buildType.buildType.buildConfigFields.isNotEmpty()) {
                    issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                    """
                        Build Type '${buildType.buildType.name}' contains custom BuildConfig fields, but the feature is disabled.
                        $suggestion
                    """.trimIndent(),
                    )
                }
            }

            for (productFlavor in model.productFlavors.values) {
                if (productFlavor.productFlavor.buildConfigFields.isNotEmpty()) {
                    issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        """
                        Product Flavor '${productFlavor.productFlavor.name}' contains custom BuildConfig fields, but the feature is disabled.
                        $suggestion
                        """.trimIndent(),
                    )
                }
            }
        }
    }

    private fun validateResValues(
        model: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
        resValues: Boolean?
    ) {
        val finalResValues = resValues
            ?: dslServices.projectOptions[BooleanOption.BUILD_FEATURE_RESVALUES]

        if (!finalResValues) {
            val issueReporter = dslServices.issueReporter

            if (model.defaultConfigData.defaultConfig.resValues.isNotEmpty()) {
                issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    "defaultConfig contains custom resource values, but the feature is disabled."
                )
            }

            for (buildType in model.buildTypes.values) {
                if (buildType.buildType.resValues.isNotEmpty()) {
                    issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "Build Type ${buildType.buildType.name} contains custom resource values, but the feature is disabled.",
                    )
                }
            }

            for (productFlavor in model.productFlavors.values) {
                if (productFlavor.productFlavor.resValues.isNotEmpty()) {
                    issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "Product Flavor ${productFlavor.productFlavor.name} contains custom resource values, but the feature is disabled.",
                    )
                }
            }
        }
    }

    private fun createUnitTestBuildFeatures(
        testedVariantBuildFeatures: BuildFeatureValues
    ): BuildFeatureValues {
        return UnitTestBuildFeatureValuesImpl(testedVariantBuildFeatures)
    }

    companion object {
        private const val ANDROID_APT_PLUGIN_NAME = "com.neenbedankt.android-apt"
    }
}
