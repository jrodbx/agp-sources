/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.VariantOutput
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.DataBinding
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.ApplicationVariantBuilderImpl
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.android.build.api.variant.impl.FilterConfigurationImpl
import com.android.build.api.variant.impl.GlobalVariantBuilderConfig
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.AndroidTestBuildFeatureValuesImpl
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.TestFixturesBuildFeaturesValuesImpl
import com.android.build.gradle.internal.scope.UnitTestBuildFeaturesValuesImpl
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.ide.common.build.GenericBuiltArtifact
import com.android.ide.common.build.GenericBuiltArtifactsSplitOutputMatcher
import com.android.ide.common.build.GenericFilterConfiguration
import com.google.common.base.Joiner
import com.google.common.base.Strings
import java.util.Arrays
import java.util.function.Consumer

class ApplicationVariantFactory(
    dslServices: DslServices,
) : AbstractAppVariantFactory<ApplicationVariantBuilder, ApplicationVariantDslInfo, ApplicationCreationConfig>(
    dslServices,
) {

    override fun createVariantBuilder(
        globalVariantBuilderConfig: GlobalVariantBuilderConfig,
        componentIdentity: ComponentIdentity,
        variantDslInfo: ApplicationVariantDslInfo,
        variantBuilderServices: VariantBuilderServices
    ): ApplicationVariantBuilderImpl {

        return dslServices
            .newInstance(
                ApplicationVariantBuilderImpl::class.java,
                globalVariantBuilderConfig,
                variantDslInfo,
                componentIdentity,
                variantBuilderServices
            )
    }

    override fun createVariant(
        variantBuilder: ApplicationVariantBuilder,
        componentIdentity: ComponentIdentity,
        buildFeatures: BuildFeatureValues,
        variantDslInfo: ApplicationVariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantData: BaseVariantData,
        taskContainer: MutableTaskContainer,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        globalConfig: GlobalTaskCreationConfig,
        ): ApplicationCreationConfig {
        val appVariant = dslServices
            .newInstance(
                ApplicationVariantImpl::class.java,
                variantBuilder,
                buildFeatures,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                variantData,
                taskContainer,
                variantBuilder.dependenciesInfo,
                variantServices,
                taskCreationServices,
                globalConfig,
            )

        computeOutputs(appVariant, (variantData as ApplicationVariantData), globalConfig)

        return appVariant
    }

    override fun createBuildFeatureValues(
        buildFeatures: BuildFeatures,
        projectOptions: ProjectOptions
    ): BuildFeatureValues {
        buildFeatures as? ApplicationBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type ApplicationBuildFeatures")

        return BuildFeatureValuesImpl(buildFeatures, projectOptions)
    }

    override fun createTestFixturesBuildFeatureValues(
        buildFeatures: BuildFeatures,
        projectOptions: ProjectOptions,
        androidResourcesEnabled: Boolean
    ): BuildFeatureValues {
        buildFeatures as? ApplicationBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type ApplicationBuildFeatures")

        return TestFixturesBuildFeaturesValuesImpl(
            buildFeatures,
            projectOptions,
            androidResourcesEnabled
        )
    }

    override fun createUnitTestBuildFeatureValues(
        buildFeatures: BuildFeatures,
        dataBinding: DataBinding,
        projectOptions: ProjectOptions,
        includeAndroidResources: Boolean
    ): BuildFeatureValues {
        buildFeatures as? ApplicationBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type ApplicationBuildFeatures")

        return UnitTestBuildFeaturesValuesImpl(
            buildFeatures,
            projectOptions,
            dataBindingOverride = if (!dataBinding.enableForTests) {
                false
            } else {
                null // means whatever is default.
            },
            mlModelBindingOverride = false,
            includeAndroidResources = includeAndroidResources,
            testedComponent = componentType
        )
    }

    override fun createAndroidTestBuildFeatureValues(
        buildFeatures: BuildFeatures,
        dataBinding: DataBinding,
        projectOptions: ProjectOptions
    ): BuildFeatureValues {
        buildFeatures as? ApplicationBuildFeatures
            ?: throw RuntimeException("buildFeatures not of type ApplicationBuildFeatures")

        return AndroidTestBuildFeatureValuesImpl(
            buildFeatures,
            projectOptions,
            dataBindingOverride = if (!dataBinding.enableForTests) {
                false
            } else {
                null // means whatever is default.
            },
            mlModelBindingOverride = false
        )
    }

    override val componentType
        get() = ComponentTypeImpl.BASE_APK

    private fun computeOutputs(
        appVariant: ApplicationCreationConfig,
        variant: ApplicationVariantData,
        globalConfig: GlobalTaskCreationConfig,
    ) {
        variant.calculateFilters(globalConfig.splits)
        val densities = variant.getFilters(VariantOutput.FilterType.DENSITY)
        val abis = variant.getFilters(VariantOutput.FilterType.ABI)
        val nativeBuildCreationConfig = appVariant.nativeBuildCreationConfig!!
        checkSplitsConflicts(nativeBuildCreationConfig, abis, globalConfig)
        if (densities.isNotEmpty()) {
            variant.compatibleScreens = globalConfig.splits.density
                .compatibleScreens
        }
        val variantOutputs =
            populateMultiApkOutputs(abis, densities, globalConfig)
        variantOutputs.forEach { appVariant.addVariantOutput(it) }
        restrictEnabledOutputs(
            nativeBuildCreationConfig,
            appVariant.outputs,
            globalConfig
        )
    }

    private fun populateMultiApkOutputs(
        abis: Set<String>,
        densities: Set<String>,
        globalConfig: GlobalTaskCreationConfig
    ): List<VariantOutputConfigurationImpl> {

        if (densities.isEmpty() && abis.isEmpty()) {
            // If both are empty, we will have only the main Apk.
            return listOf(VariantOutputConfigurationImpl())
        }
        val variantOutputs = mutableListOf<VariantOutputConfigurationImpl>()
        val universalApkForAbi =
            (globalConfig.splits.abi.isEnable && globalConfig.splits.abi.isUniversalApk)
        if (universalApkForAbi) {
            variantOutputs.add(
                VariantOutputConfigurationImpl(isUniversal = true)
            )
        } else {
            if (abis.isEmpty()) {
                variantOutputs.add(
                    VariantOutputConfigurationImpl(isUniversal = true)
                )
            }
        }
        if (abis.isNotEmpty()) { // TODO(b/117973371): Check if this is still needed/used, as BundleTool don't do this.
            // for each ABI, create a specific split that will contain all densities.
            abis.forEach(
                Consumer { abi: String ->
                    variantOutputs.add(
                        VariantOutputConfigurationImpl(
                            filters = listOf(
                                FilterConfigurationImpl(
                                    filterType = FilterConfiguration.FilterType.ABI,
                                    identifier = abi
                                )
                            )
                        )
                    )
                }
            )
        }
        // create its outputs
        for (density in densities) {
            if (abis.isNotEmpty()) {
                for (abi in abis) {
                    variantOutputs.add(
                        VariantOutputConfigurationImpl(
                            filters = listOf(
                                FilterConfigurationImpl(
                                    filterType = FilterConfiguration.FilterType.ABI,
                                    identifier = abi
                                ),
                                FilterConfigurationImpl(
                                    filterType = FilterConfiguration.FilterType.DENSITY,
                                    identifier = density
                                )
                            )
                        )
                    )
                }
            } else {
                variantOutputs.add(
                    VariantOutputConfigurationImpl(
                        filters = listOf(
                            FilterConfigurationImpl(
                                filterType = FilterConfiguration.FilterType.DENSITY,
                                identifier = density
                            )
                        )
                    )
                )
            }
        }
        return variantOutputs
    }

    private fun checkSplitsConflicts(
        component: NativeBuildCreationConfig,
        abiFilters: Set<String?>,
        globalConfig: GlobalTaskCreationConfig,
    ) { // if we don't have any ABI splits, nothing is conflicting.
        if (abiFilters.isEmpty()) {
            return
        }
        // if universalAPK is requested, abiFilter will control what goes into the universal APK.
        if (globalConfig.splits.abi.isUniversalApk) {
            return
        }
        // check supportedAbis in Ndk configuration versus ABI splits.
        val ndkConfigAbiFilters = component.ndkConfig.abiFilters
        if (ndkConfigAbiFilters.isEmpty()) {
            return
        }
        // if we have any ABI splits, whether it's a full or pure ABI splits, it's an error.
        val issueReporter = dslServices.issueReporter
        issueReporter.reportError(
            IssueReporter.Type.GENERIC, String.format(
                "Conflicting configuration : '%1\$s' in ndk abiFilters "
                        + "cannot be present when splits abi filters are set : %2\$s",
                Joiner.on(",").join(ndkConfigAbiFilters),
                Joiner.on(",").join(abiFilters)
            )
        )
    }

    private fun restrictEnabledOutputs(
        component: NativeBuildCreationConfig,
        variantOutputs: VariantOutputList,
        globalConfig: GlobalTaskCreationConfig
    ) {
        val supportedAbis: Set<String> = component.supportedAbis
        val projectOptions = dslServices.projectOptions
        val buildTargetAbi =
            (if (projectOptions[BooleanOption.BUILD_ONLY_TARGET_ABI]
                || globalConfig.splits.abi.isEnable
            ) projectOptions[StringOption.IDE_BUILD_TARGET_ABI] else null)
                ?: return
        val genericBuiltArtifacts = variantOutputs
            .map { variantOutput ->
                GenericBuiltArtifact(
                    outputType = variantOutput.outputType.name,
                    filters = variantOutput.filters.map { filter -> GenericFilterConfiguration(filter.filterType.name, filter.identifier) },
                    // this is wrong, talk to xav@, we cannot continue supporting this.
                    versionCode = variantOutput.versionCode.orNull,
                    versionName = variantOutput.versionName.orNull,
                    outputFile = "not_provided"
                ) to variantOutput
            }
            .toMap()
        val computedBestArtifact = GenericBuiltArtifactsSplitOutputMatcher.computeBestArtifact(
            genericBuiltArtifacts.keys,
            supportedAbis,
            Arrays.asList(
                *Strings.nullToEmpty(
                    buildTargetAbi
                ).split(",".toRegex()).toTypedArray()
            )
        )
        if (computedBestArtifact == null) {
            val splits = variantOutputs
                .map { obj: com.android.build.api.variant.VariantOutput -> obj.filters }
                .map { filters: Collection<FilterConfiguration> ->
                    filters.joinToString(",")
                }
            dslServices
                .issueReporter
                .reportWarning(
                    IssueReporter.Type.GENERIC, String.format(
                        "Cannot build selected target ABI: %1\$s, "
                                + if (splits.isEmpty()) "no suitable splits configured: %2\$s;" else "supported ABIs are: %2\$s",
                        buildTargetAbi,
                        if (supportedAbis.isEmpty()) Joiner.on(", ").join(splits)
                        else Joiner.on(", ").join(supportedAbis)
                    )
                )
            // do not disable anything, build all and let the apk install figure it out.
            return
        }
        genericBuiltArtifacts.forEach { key: GenericBuiltArtifact, value: VariantOutputImpl ->
            if (key != computedBestArtifact) value.enabled.set(false)
        }
    }
}
