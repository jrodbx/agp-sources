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

package com.android.build.gradle.internal.variant;

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.variant.impl.ApplicationVariantImpl;
import com.android.build.api.variant.impl.ApplicationVariantPropertiesImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.api.variant.impl.VariantOutputList;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.resources.Density;
import com.android.utils.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * An implementation of VariantFactory for a project that generates APKs.
 *
 * <p>This can be an app project, or a test-only project, though the default behavior is app.
 */
public class ApplicationVariantFactory extends BaseVariantFactory {

    public ApplicationVariantFactory(@NonNull GlobalScope globalScope) {
        super(globalScope);
    }

    @NonNull
    @Override
    public VariantImpl createVariantObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantDslInfo variantDslInfo) {
        return globalScope
                .getDslScope()
                .getObjectFactory()
                .newInstance(ApplicationVariantImpl.class, variantDslInfo, componentIdentity);
    }

    @NonNull
    @Override
    public VariantPropertiesImpl createVariantPropertiesObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantScope variantScope) {
        return globalScope
                .getDslScope()
                .getObjectFactory()
                .newInstance(
                        ApplicationVariantPropertiesImpl.class,
                        globalScope.getDslScope(),
                        variantScope,
                        variantScope.getArtifacts().getOperations(),
                        componentIdentity);
    }

    @Override
    @NonNull
    public BaseVariantData createVariantData(
            @NonNull VariantScope variantScope,
            @NonNull VariantDslInfoImpl variantDslInfo,
            @NonNull VariantImpl publicVariantApi,
            @NonNull VariantPropertiesImpl publicVariantPropertiesApi,
            @NonNull VariantSources variantSources,
            @NonNull TaskManager taskManager) {
        ApplicationVariantData variant =
                new ApplicationVariantData(
                        globalScope,
                        taskManager,
                        variantScope,
                        variantDslInfo,
                        publicVariantApi,
                        publicVariantPropertiesApi,
                        variantSources);
        computeOutputs(variantDslInfo, variant, true);

        return variant;
    }

    protected void computeOutputs(
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull ApplicationVariantData variant,
            boolean includeMainApk) {
        BaseExtension extension = globalScope.getExtension();
        variant.calculateFilters(extension.getSplits());

        Set<String> densities = variant.getFilters(OutputFile.FilterType.DENSITY);
        Set<String> abis = variant.getFilters(OutputFile.FilterType.ABI);

        checkSplitsConflicts(variant, abis);

        if (!densities.isEmpty()) {
            variant.setCompatibleScreens(extension.getSplits().getDensity()
                    .getCompatibleScreens());
        }

        OutputFactory outputFactory = variant.getOutputFactory();
        populateMultiApkOutputs(abis, densities, outputFactory, includeMainApk);

        outputFactory
                .finalizeApkDataList()
                .forEach(
                        apkData ->
                                variant.getPublicVariantPropertiesApi().addVariantOutput(apkData));

        restrictEnabledOutputs(
                variantDslInfo, variant.getPublicVariantPropertiesApi().getOutputs());
    }

    private void populateMultiApkOutputs(
            Set<String> abis,
            Set<String> densities,
            OutputFactory outputFactory,
            boolean includeMainApk) {
        if (densities.isEmpty() && abis.isEmpty()) {
            // If both are empty, we will have only the main Apk.
            if (includeMainApk) {
                outputFactory.addMainApk();
            }
            return;
        }

        boolean universalApkForAbi =
                globalScope.getExtension().getSplits().getAbi().isEnable()
                        && globalScope.getExtension().getSplits().getAbi().isUniversalApk();
        if (universalApkForAbi) {
            outputFactory.addUniversalApk();
        } else {
            if (abis.isEmpty() && includeMainApk) {
                outputFactory.addUniversalApk();
            }
        }

        if (!abis.isEmpty()) {
            // TODO(b/117973371): Check if this is still needed/used, as BundleTool don't do this.
            // for each ABI, create a specific split that will contain all densities.
            abis.forEach(
                    abi ->
                            outputFactory.addFullSplit(
                                    ImmutableList.of(Pair.of(OutputFile.FilterType.ABI, abi))));
        }

        // create its outputs
        for (String density : densities) {
            if (!abis.isEmpty()) {
                for (String abi : abis) {
                    outputFactory.addFullSplit(
                            ImmutableList.of(
                                    Pair.of(OutputFile.FilterType.ABI, abi),
                                    Pair.of(OutputFile.FilterType.DENSITY, density)));
                }
            } else {
                outputFactory.addFullSplit(
                        ImmutableList.of(Pair.of(OutputFile.FilterType.DENSITY, density)));
            }
        }
    }

    private void checkSplitsConflicts(
            @NonNull ApplicationVariantData variantData, @NonNull Set<String> abiFilters) {

        // if we don't have any ABI splits, nothing is conflicting.
        if (abiFilters.isEmpty()) {
            return;
        }

        // if universalAPK is requested, abiFilter will control what goes into the universal APK.
        if (globalScope.getExtension().getSplits().getAbi().isUniversalApk()) {
            return;
        }

        // check supportedAbis in Ndk configuration versus ABI splits.
        Set<String> ndkConfigAbiFilters =
                variantData.getVariantDslInfo().getNdkConfig().getAbiFilters();
        if (ndkConfigAbiFilters == null || ndkConfigAbiFilters.isEmpty()) {
            return;
        }

        // if we have any ABI splits, whether it's a full or pure ABI splits, it's an error.
        IssueReporter issueReporter = globalScope.getDslScope().getIssueReporter();
        issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                String.format(
                        "Conflicting configuration : '%1$s' in ndk abiFilters "
                                + "cannot be present when splits abi filters are set : %2$s",
                        Joiner.on(",").join(ndkConfigAbiFilters), Joiner.on(",").join(abiFilters)));
    }

    private void restrictEnabledOutputs(
            VariantDslInfo variantDslInfo, VariantOutputList variantOutputs) {

        Set<String> supportedAbis = variantDslInfo.getSupportedAbis();
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        String buildTargetAbi =
                projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI)
                                || globalScope.getExtension().getSplits().getAbi().isEnable()
                        ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                        : null;
        if (buildTargetAbi == null) {
            return;
        }

        String buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY);
        Density density = Density.getEnum(buildTargetDensity);

        List<ApkData> apkDataList =
                variantOutputs
                        .stream()
                        .map(VariantOutputImpl::getApkData)
                        .collect(Collectors.toList());

        List<ApkData> apksToGenerate =
                SplitOutputMatcher.computeBestOutput(
                        apkDataList,
                        supportedAbis,
                        density == null ? -1 : density.getDpiValue(),
                        Arrays.asList(Strings.nullToEmpty(buildTargetAbi).split(",")));

        if (apksToGenerate.isEmpty()) {
            List<String> splits =
                    apkDataList
                            .stream()
                            .map(ApkData::getFilterName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportWarning(
                            IssueReporter.Type.GENERIC,
                            String.format(
                                    "Cannot build selected target ABI: %1$s, "
                                            + (splits.isEmpty()
                                                    ? "no suitable splits configured: %2$s;"
                                                    : "supported ABIs are: %2$s"),
                                    buildTargetAbi,
                                    supportedAbis == null
                                            ? Joiner.on(", ").join(splits)
                                            : Joiner.on(", ").join(supportedAbis)));

            // do not disable anything, build all and let the apk install figure it out.
            return;
        }

        variantOutputs.forEach(
                variantOutput -> {
                    if (!apksToGenerate.contains(variantOutput.getApkData())) {
                        variantOutput.isEnabled().set(false);
                    }
                });
    }

    @Override
    @NonNull
    public Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData) {
        return com.android.build.gradle.internal.api.ApplicationVariantImpl.class;
    }

    @NonNull
    @Override
    public VariantType getVariantType() {
        if (globalScope.getExtension().getBaseFeature()) {
            return VariantTypeImpl.BASE_APK;
        }
        return VariantTypeImpl.OPTIONAL_APK;
    }

    @Override
    public boolean hasTestScope() {
        return true;
    }

    @Override
    public void validateModel(@NonNull VariantInputModel model) {
        super.validateModel(model);

        validateVersionCodes(model);

        if (!getVariantType().isDynamicFeature()) {
            return;
        }

        // below is for dynamic-features only.

        IssueReporter issueReporter = globalScope.getDslScope().getIssueReporter();
        for (BuildTypeData buildType : model.getBuildTypes().values()) {
            if (buildType.getBuildType().isMinifyEnabled()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        "Dynamic feature modules cannot set minifyEnabled to true. "
                                + "minifyEnabled is set to true in build type '"
                                + buildType.getBuildType().getName()
                                + "'.\nTo enable minification for a dynamic feature "
                                + "module, set minifyEnabled to true in the base module.");
            }
        }

        // check if any of the build types or flavors have a signing config.
        String message =
                "Signing configuration should not be declared in build types of "
                        + "dynamic-feature. Dynamic-features use the signing configuration "
                        + "declared in the application module.";
        for (BuildTypeData buildType : model.getBuildTypes().values()) {
            if (buildType.getBuildType().getSigningConfig() != null) {
                issueReporter.reportWarning(
                        Type.SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE, message);
            }
        }

        message =
                "Signing configuration should not be declared in product flavors of "
                        + "dynamic-feature. Dynamic-features use the signing configuration "
                        + "declared in the application module.";
        for (ProductFlavorData<ProductFlavor> productFlavor : model.getProductFlavors().values()) {
            if (productFlavor.getProductFlavor().getSigningConfig() != null) {

                issueReporter.reportWarning(
                        Type.SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE, message);
            }
        }
    }

    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(DEBUG);
        buildTypes.create(DEBUG);
        buildTypes.create(RELEASE);
    }

    private void validateVersionCodes(@NonNull VariantInputModel model) {
        IssueReporter issueReporter = globalScope.getDslScope().getIssueReporter();

        Integer versionCode = model.getDefaultConfig().getProductFlavor().getVersionCode();
        if (versionCode != null && versionCode < 1) {
            issueReporter.reportError(
                    Type.GENERIC,
                    "android.defaultConfig.versionCode is set to "
                            + versionCode
                            + ", but it should be a positive integer.\n"
                            + "See https://developer.android.com/studio/publish/versioning#appversioning"
                            + " for more information.");
            return;
        }

        for (ProductFlavorData flavorData : model.getProductFlavors().values()) {
            Integer flavorVersionCode = flavorData.getProductFlavor().getVersionCode();
            if (flavorVersionCode == null || flavorVersionCode > 0) {
                return;
            }
            issueReporter.reportError(
                    Type.GENERIC,
                    "versionCode is set to "
                            + flavorVersionCode
                            + " in product flavor "
                            + flavorData.getProductFlavor().getName()
                            + ", but it should be a positive integer.\n"
                            + "See https://developer.android.com/studio/publish/versioning#appversioning"
                            + " for more information.");
        }
    }
}
