/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.BaseConfigImpl;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builder-level implementation of ProductFlavor.
 *
 * <p>This is also used to describe the default configuration of all builds, even those that do not
 * contain any flavors.
 *
 * @deprecated This is deprecated, use DSL objects directly.
 */
@Deprecated
public abstract class AbstractProductFlavor extends BaseConfigImpl implements ProductFlavor {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String mName;
    @Nullable
    private String mDimension;
    @Nullable
    private ApiVersion mMinSdkVersion;
    @Nullable
    private ApiVersion mTargetSdkVersion;
    @Nullable
    private Integer mMaxSdkVersion;
    @Nullable
    private Integer mRenderscriptTargetApi;
    @Nullable
    private Boolean mRenderscriptSupportModeEnabled;
    @Nullable
    private Boolean mRenderscriptSupportModeBlasEnabled;
    @Nullable
    private Boolean mRenderscriptNdkModeEnabled;
    @Nullable
    private Integer mVersionCode;
    @Nullable
    private String mVersionName;
    @Nullable
    private String mApplicationId;
    @Nullable
    private String mTestApplicationId;
    @Nullable
    private String mTestInstrumentationRunner;
    @NonNull
    private Map<String, String> mTestInstrumentationRunnerArguments = Maps.newHashMap();
    @Nullable
    private Boolean mTestHandleProfiling;
    @Nullable
    private Boolean mTestFunctionalTest;
    @Nullable
    private SigningConfig mSigningConfig;
    @Nullable
    private Set<String> mResourceConfiguration;
    @NonNull
    private DefaultVectorDrawablesOptions mVectorDrawablesOptions;
    @Nullable
    private Boolean mWearAppUnbundled;

    /**
     * Creates a ProductFlavor with a given name.
     *
     * <p>Names can be important when dealing with flavor groups.
     *
     * @param name the name of the flavor.
     * @see BuilderConstants#MAIN
     */
    public AbstractProductFlavor(@NonNull String name) {
        mName = name;
        mVectorDrawablesOptions = new DefaultVectorDrawablesOptions();
    }

    public AbstractProductFlavor(
            @NonNull String name, @NonNull DefaultVectorDrawablesOptions vectorDrawablesOptions) {
        mName = name;
        mVectorDrawablesOptions = vectorDrawablesOptions;
    }

    @Override
    @NonNull
    public String getName() {
        return mName;
    }

    public void setDimension(@NonNull String dimension) {
        mDimension = dimension;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public String getDimension() {
        return mDimension;
    }

    /**
     * Sets the application id.
     */
    @NonNull
    public ProductFlavor setApplicationId(String applicationId) {
        mApplicationId = applicationId;
        return this;
    }

    /**
     * Returns the application ID.
     *
     * <p>See <a href="https://developer.android.com/studio/build/application-id.html">Set the Application ID</a>
     */
    @Override
    @Nullable
    public String getApplicationId() {
        return mApplicationId;
    }

    /**
     * Sets the version code.
     *
     * @param versionCode the version code
     * @return the flavor object
     */
    @NonNull
    public ProductFlavor setVersionCode(Integer versionCode) {
        mVersionCode = versionCode;
        return this;
    }

    /**
     * Version code.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/versioning.html">Versioning Your Application</a>
     */
    @Override
    @Nullable
    public Integer getVersionCode() {
        return mVersionCode;
    }

    /**
     * Sets the version name.
     *
     * @param versionName the version name
     * @return the flavor object
     */
    @NonNull
    public ProductFlavor setVersionName(String versionName) {
        mVersionName = versionName;
        return this;
    }

    /**
     * Version name.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/versioning.html">Versioning Your Application</a>
     */
    @Override
    @Nullable
    public String getVersionName() {
        return mVersionName;
    }

    /**
     * Sets the minSdkVersion to the given value.
     */
    @NonNull
    public ProductFlavor setMinSdkVersion(ApiVersion minSdkVersion) {
        mMinSdkVersion = minSdkVersion;
        return this;
    }

    /**
     * Min SDK version.
     */
    @Nullable
    @Override
    public ApiVersion getMinSdkVersion() {
        return mMinSdkVersion;
    }

    /** Sets the targetSdkVersion to the given value. */
    @NonNull
    public ProductFlavor setTargetSdkVersion(@Nullable ApiVersion targetSdkVersion) {
        mTargetSdkVersion = targetSdkVersion;
        return this;
    }

    /**
     * Target SDK version.
     */
    @Nullable
    @Override
    public ApiVersion getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    @NonNull
    public ProductFlavor setMaxSdkVersion(Integer maxSdkVersion) {
        mMaxSdkVersion = maxSdkVersion;
        return this;
    }

    @Nullable
    @Override
    public Integer getMaxSdkVersion() {
        return mMaxSdkVersion;
    }

    @Override
    @Nullable
    public Integer getRenderscriptTargetApi() {
        return mRenderscriptTargetApi;
    }

    /** Sets the renderscript target API to the given value. */
    public void setRenderscriptTargetApi(@Nullable Integer renderscriptTargetApi) {
        mRenderscriptTargetApi = renderscriptTargetApi;
    }

    @Override
    @Nullable
    public Boolean getRenderscriptSupportModeEnabled() {
        return mRenderscriptSupportModeEnabled;
    }

    @Override
    @Nullable
    public Boolean getRenderscriptSupportModeBlasEnabled() {
        return mRenderscriptSupportModeBlasEnabled;
    }

    /**
     * Sets whether the renderscript code should be compiled in support mode to make it compatible
     * with older versions of Android.
     */
    public ProductFlavor setRenderscriptSupportModeEnabled(Boolean renderscriptSupportMode) {
        mRenderscriptSupportModeEnabled = renderscriptSupportMode;
        return this;
    }

    /**
     * Sets whether RenderScript BLAS support lib should be used to make it compatible
     * with older versions of Android.
     */
    public ProductFlavor setRenderscriptSupportModeBlasEnabled(Boolean renderscriptSupportModeBlas) {
        mRenderscriptSupportModeBlasEnabled = renderscriptSupportModeBlas;
        return this;
    }

    @Override
    @Nullable
    public Boolean getRenderscriptNdkModeEnabled() {
        return mRenderscriptNdkModeEnabled;
    }


    /** Sets whether the renderscript code should be compiled to generate C/C++ bindings. */
    public ProductFlavor setRenderscriptNdkModeEnabled(Boolean renderscriptNdkMode) {
        mRenderscriptNdkModeEnabled = renderscriptNdkMode;
        return this;
    }

    /** Sets the test application ID. */
    @NonNull
    public ProductFlavor setTestApplicationId(String applicationId) {
        mTestApplicationId = applicationId;
        return this;
    }

    /**
     * Test application ID.
     *
     * <p>See <a href="https://developer.android.com/studio/build/application-id.html">Set the Application ID</a>
     */
    @Override
    @Nullable
    public String getTestApplicationId() {
        return mTestApplicationId;
    }

    /** Sets the test instrumentation runner to the given value. */
    @NonNull
    public ProductFlavor setTestInstrumentationRunner(String testInstrumentationRunner) {
        mTestInstrumentationRunner = testInstrumentationRunner;
        return this;
    }

    /**
     * Test instrumentation runner class name.
     *
     * <p>This is a fully qualified class name of the runner, e.g.
     * <code>android.test.InstrumentationTestRunner</code>
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public String getTestInstrumentationRunner() {
        return mTestInstrumentationRunner;
    }

    /** Sets the test instrumentation runner custom arguments. */
    @NonNull
    public ProductFlavor setTestInstrumentationRunnerArguments(
            @NonNull Map<String, String> testInstrumentationRunnerArguments) {
        mTestInstrumentationRunnerArguments = checkNotNull(testInstrumentationRunnerArguments);
        return this;
    }

    /**
     * Test instrumentation runner custom arguments.
     *
     * <p>e.g. <code>[key: "value"]</code> will give <code>
     * adb shell am instrument -w <b>-e key value</b> com.example</code>...".
     *
     * <p>See <a
     * href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     *
     * <p>Test runner arguments can also be specified from the command line:
     *
     * <p>
     *
     * <pre>
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * </pre>
     */
    @SuppressWarnings("SpellCheckingInspection")
    @Override
    @NonNull
    public Map<String, String> getTestInstrumentationRunnerArguments() {
        return mTestInstrumentationRunnerArguments;
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public Boolean getTestHandleProfiling() {
        return mTestHandleProfiling;
    }

    @NonNull
    public ProductFlavor setTestHandleProfiling(boolean handleProfiling) {
        mTestHandleProfiling = handleProfiling;
        return this;
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public Boolean getTestFunctionalTest() {
        return mTestFunctionalTest;
    }

    @NonNull
    public ProductFlavor setTestFunctionalTest(boolean functionalTest) {
        mTestFunctionalTest = functionalTest;
        return this;
    }

    /**
     * Signing config used by this product flavor.
     */
    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    /** Sets the signing configuration. e.g.: {@code signingConfig signingConfigs.myConfig} */
    @NonNull
    public ProductFlavor setSigningConfig(SigningConfig signingConfig) {
        mSigningConfig = signingConfig;
        return this;
    }

    /**
     * Options to configure the build-time support for {@code vector} drawables.
     */
    @NonNull
    @Override
    public DefaultVectorDrawablesOptions getVectorDrawables() {
        return mVectorDrawablesOptions;
    }

    /**
     * Returns whether to enable unbundling mode for embedded wear app.
     *
     * If true, this enables the app to transition from an embedded wear app to one
     * distributed by the play store directly.
     */
    @Nullable
    @Override
    public Boolean getWearAppUnbundled() {
        return mWearAppUnbundled;
    }

    /**
     * Sets whether to enable unbundling mode for embedded wear app.
     *
     * If true, this enables the app to transition from an embedded wear app to one
     * distributed by the play store directly.
     */
    public void setWearAppUnbundled(@Nullable Boolean wearAppUnbundled) {
        mWearAppUnbundled = wearAppUnbundled;
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    public void addResourceConfiguration(@NonNull String configuration) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.add(configuration);
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    public void addResourceConfigurations(@NonNull String... configurations) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.addAll(Arrays.asList(configurations));
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    public void addResourceConfigurations(@NonNull Collection<String> configurations) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.addAll(configurations);
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    @NonNull
    @Override
    public Collection<String> getResourceConfigurations() {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        return mResourceConfiguration;
    }

    /** Class representing a request with fallbacks. */
    public static class DimensionRequest {
        @NonNull private final String requested;
        @NonNull private final ImmutableList<String> fallbacks;

        public DimensionRequest(
                @NonNull String requested, @NonNull ImmutableList<String> fallbacks) {
            this.requested = requested;
            this.fallbacks = fallbacks;
        }

        @NonNull
        public String getRequested() {
            return requested;
        }

        @NonNull
        public List<String> getFallbacks() {
            return fallbacks;
        }
    }

    /** map of dimension -> request */
    private Map<String, DimensionRequest> missingDimensionSelections;

    /**
     * Specifies a flavor that the plugin should try to use from a given dimension in a dependency.
     *
     * <p>Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, consider if both your app and its dependencies include a
     * "tier" <a href="/studio/build/build-variants.html#flavor-dimensions">flavor dimension</a>,
     * with flavors "free" and "paid". When you build a "freeDebug" version of your app, the plugin
     * tries to match it with "freeDebug" versions of the local library modules the app depends on.
     *
     * <p>However, there may be situations in which <b>a library dependency includes a flavor
     * dimension that your app does not</b>. For example, consider if a library dependency includes
     * flavors for a "minApi" dimension, but your app includes flavors for only the "tier"
     * dimension. So, when you want to build the "freeDebug" version of your app, the plugin doesn't
     * know whether to use the "minApi23Debug" or "minApi18Debug" version of the dependency, and
     * you'll see an error message similar to the following:
     *
     * <pre>
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     *     project :app
     * </pre>
     *
     * <p>In this type of situation, use <code>missingDimensionStrategy</code> in the <a
     * href="com.android.build.gradle.internal.dsl.DefaultConfig.html"><code>defaultConfig</code>
     * </a> block to specify the default flavor the plugin should select from each missing
     * dimension, as shown in the sample below. You can also override your selection in the <a
     * href="com.android.build.gradle.internal.dsl.ProductFlavor.html"><code>productFlavors</code>
     * </a> block, so each flavor can specify a different matching strategy for a missing dimension.
     * (Tip: you can also use this property if you simply want to change the matching strategy for a
     * dimension that exists in both the app and its dependencies.)
     *
     * <pre>
     * // In the app's build.gradle file.
     * android {
     *     defaultConfig{
     *     // Specifies a flavor that the plugin should try to use from
     *     // a given dimension. The following tells the plugin that, when encountering
     *     // a dependency that includes a "minApi" dimension, it should select the
     *     // "minApi18" flavor.
     *     missingDimensionStrategy 'minApi', 'minApi18'
     *     // You should specify a missingDimensionStrategy property for each
     *     // dimension that exists in a local dependency but not in your app.
     *     missingDimensionStrategy 'abi', 'x86'
     *     }
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         free {
     *             dimension 'tier'
     *             // You can override the default selection at the product flavor
     *             // level by configuring another missingDimensionStrategy property
     *             // for the "minApi" dimension.
     *             missingDimensionStrategy 'minApi', 'minApi23'
     *         }
     *         paid {}
     *     }
     * }
     * </pre>
     *
     * @param dimension
     * @param requestedValue
     */
    public void missingDimensionStrategy(String dimension, String requestedValue) {
        missingDimensionStrategy(dimension, ImmutableList.of(requestedValue));
    }

    /**
     * Specifies a sorted list of flavors that the plugin should try to use from a given dimension
     * in a dependency.
     *
     * <p>Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, consider if both your app and its dependencies include a
     * "tier" <a href="/studio/build/build-variants.html#flavor-dimensions">flavor dimension</a>,
     * with flavors "free" and "paid". When you build a "freeDebug" version of your app, the plugin
     * tries to match it with "freeDebug" versions of the local library modules the app depends on.
     *
     * <p>However, there may be situations in which <b>a library dependency includes a flavor
     * dimension that your app does not</b>. For example, consider if a library dependency includes
     * flavors for a "minApi" dimension, but your app includes flavors for only the "tier"
     * dimension. So, when you want to build the "freeDebug" version of your app, the plugin doesn't
     * know whether to use the "minApi23Debug" or "minApi18Debug" version of the dependency, and
     * you'll see an error message similar to the following:
     *
     * <pre>
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     *     project :app
     * </pre>
     *
     * <p>In this type of situation, use <code>missingDimensionStrategy</code> in the <a
     * href="com.android.build.gradle.internal.dsl.DefaultConfig.html"><code>defaultConfig</code>
     * </a> block to specify the default flavor the plugin should select from each missing
     * dimension, as shown in the sample below. You can also override your selection in the <a
     * href="com.android.build.gradle.internal.dsl.ProductFlavor.html"><code>productFlavors</code>
     * </a> block, so each flavor can specify a different matching strategy for a missing dimension.
     * (Tip: you can also use this property if you simply want to change the matching strategy for a
     * dimension that exists in both the app and its dependencies.)
     *
     * <pre>
     * // In the app's build.gradle file.
     * android {
     *     defaultConfig{
     *     // Specifies a sorted list of flavors that the plugin should try to use from
     *     // a given dimension. The following tells the plugin that, when encountering
     *     // a dependency that includes a "minApi" dimension, it should select the
     *     // "minApi18" flavor. You can include additional flavor names to provide a
     *     // sorted list of fallbacks for the dimension.
     *     missingDimensionStrategy 'minApi', 'minApi18', 'minApi23'
     *     // You should specify a missingDimensionStrategy property for each
     *     // dimension that exists in a local dependency but not in your app.
     *     missingDimensionStrategy 'abi', 'x86', 'arm64'
     *     }
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         free {
     *             dimension 'tier'
     *             // You can override the default selection at the product flavor
     *             // level by configuring another missingDimensionStrategy property
     *             // for the "minApi" dimension.
     *             missingDimensionStrategy 'minApi', 'minApi23', 'minApi18'
     *         }
     *         paid {}
     *     }
     * }
     * </pre>
     *
     * @param dimension
     * @param requestedValues
     */
    public void missingDimensionStrategy(String dimension, String... requestedValues) {
        missingDimensionStrategy(dimension, ImmutableList.copyOf(requestedValues));
    }

    /**
     * Specifies a sorted list of flavors that the plugin should try to use from a given dimension
     * in a dependency.
     *
     * <p>Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, consider if both your app and its dependencies include a
     * "tier" <a href="/studio/build/build-variants.html#flavor-dimensions">flavor dimension</a>,
     * with flavors "free" and "paid". When you build a "freeDebug" version of your app, the plugin
     * tries to match it with "freeDebug" versions of the local library modules the app depends on.
     *
     * <p>However, there may be situations in which <b>a library dependency includes a flavor
     * dimension that your app does not</b>. For example, consider if a library dependency includes
     * flavors for a "minApi" dimension, but your app includes flavors for only the "tier"
     * dimension. So, when you want to build the "freeDebug" version of your app, the plugin doesn't
     * know whether to use the "minApi23Debug" or "minApi18Debug" version of the dependency, and
     * you'll see an error message similar to the following:
     *
     * <pre>
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     *     project :app
     * </pre>
     *
     * <p>In this type of situation, use <code>missingDimensionStrategy</code> in the <a
     * href="com.android.build.gradle.internal.dsl.DefaultConfig.html"><code>defaultConfig</code>
     * </a> block to specify the default flavor the plugin should select from each missing
     * dimension, as shown in the sample below. You can also override your selection in the <a
     * href="com.android.build.gradle.internal.dsl.ProductFlavor.html"><code>productFlavors</code>
     * </a> block, so each flavor can specify a different matching strategy for a missing dimension.
     * (Tip: you can also use this property if you simply want to change the matching strategy for a
     * dimension that exists in both the app and its dependencies.)
     *
     * <pre>
     * // In the app's build.gradle file.
     * android {
     *     defaultConfig{
     *     // Specifies a sorted list of flavors that the plugin should try to use from
     *     // a given dimension. The following tells the plugin that, when encountering
     *     // a dependency that includes a "minApi" dimension, it should select the
     *     // "minApi18" flavor. You can include additional flavor names to provide a
     *     // sorted list of fallbacks for the dimension.
     *     missingDimensionStrategy 'minApi', 'minApi18', 'minApi23'
     *     // You should specify a missingDimensionStrategy property for each
     *     // dimension that exists in a local dependency but not in your app.
     *     missingDimensionStrategy 'abi', 'x86', 'arm64'
     *     }
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         free {
     *             dimension 'tier'
     *             // You can override the default selection at the product flavor
     *             // level by configuring another missingDimensionStrategy property
     *             // for the "minApi" dimension.
     *             missingDimensionStrategy 'minApi', 'minApi23', 'minApi18'
     *         }
     *         paid {}
     *     }
     * }
     * </pre>
     *
     * @param dimension
     * @param requestedValues
     */
    public void missingDimensionStrategy(String dimension, List<String> requestedValues) {
        if (requestedValues.isEmpty()) {
            throw new RuntimeException("List of requested values cannot be empty");
        }

        final DimensionRequest selection = computeRequestedAndFallBacks(requestedValues);

        if (missingDimensionSelections == null) {
            missingDimensionSelections = Maps.newHashMap();
        }

        missingDimensionSelections.put(dimension, selection);
    }

    /**
     * Computes the requested value and the fallback list from the list of values provided in the
     * DSL
     *
     * @param requestedValues the values provided in the DSL
     * @return a DimensionRequest with the main requested value and the fallbacks.
     */
    @NonNull
    protected DimensionRequest computeRequestedAndFallBacks(@NonNull List<String> requestedValues) {
        // default implementation is that the fallback's first item is the requested item.
        return new DimensionRequest(
                requestedValues.get(0),
                ImmutableList.copyOf(requestedValues.subList(1, requestedValues.size())));
    }

    @NonNull
    public Map<String, DimensionRequest> getMissingDimensionStrategies() {
        if (missingDimensionSelections == null) {
            return ImmutableMap.of();
        }

        return missingDimensionSelections;
    }

    /**
     * Merges a higher-priority flavor (overlay) on top of this one.
     *
     * <p>The behavior is that if a value is present in the overlay, then it is used, otherwise we
     * use the existing value.
     *
     * @param overlay the higher-priority flavor to apply to this flavor
     */
    protected void mergeWithHigherPriorityFlavor(@NonNull ProductFlavor overlay) {
        mMinSdkVersion = chooseNotNull(overlay.getMinSdkVersion(), mMinSdkVersion);
        mTargetSdkVersion = chooseNotNull(overlay.getTargetSdkVersion(), mTargetSdkVersion);
        mMaxSdkVersion = chooseNotNull(overlay.getMaxSdkVersion(), mMaxSdkVersion);

        mRenderscriptTargetApi =
                chooseNotNull(overlay.getRenderscriptTargetApi(), mRenderscriptTargetApi);
        mRenderscriptSupportModeEnabled =
                chooseNotNull(
                        overlay.getRenderscriptSupportModeEnabled(),
                        mRenderscriptSupportModeEnabled);
        mRenderscriptSupportModeBlasEnabled =
                chooseNotNull(
                        overlay.getRenderscriptSupportModeBlasEnabled(),
                        mRenderscriptSupportModeBlasEnabled);
        mRenderscriptNdkModeEnabled =
                chooseNotNull(overlay.getRenderscriptNdkModeEnabled(), mRenderscriptNdkModeEnabled);

        mVersionCode = chooseNotNull(overlay.getVersionCode(), mVersionCode);
        mVersionName = chooseNotNull(overlay.getVersionName(), mVersionName);

        setVersionNameSuffix(
                mergeVersionNameSuffix(overlay.getVersionNameSuffix(), getVersionNameSuffix()));

        mApplicationId = chooseNotNull(overlay.getApplicationId(), mApplicationId);

        setApplicationIdSuffix(
                mergeApplicationIdSuffix(
                        overlay.getApplicationIdSuffix(), getApplicationIdSuffix()));

        mTestApplicationId = chooseNotNull(overlay.getTestApplicationId(), mTestApplicationId);
        mTestInstrumentationRunner =
                chooseNotNull(overlay.getTestInstrumentationRunner(), mTestInstrumentationRunner);

        mTestInstrumentationRunnerArguments.putAll(overlay.getTestInstrumentationRunnerArguments());

        mTestHandleProfiling =
                chooseNotNull(overlay.getTestHandleProfiling(), mTestHandleProfiling);

        mTestFunctionalTest = chooseNotNull(overlay.getTestFunctionalTest(), mTestFunctionalTest);

        // should this be a copy instead?
        mSigningConfig = chooseNotNull(overlay.getSigningConfig(), mSigningConfig);

        mWearAppUnbundled = chooseNotNull(overlay.getWearAppUnbundled(), mWearAppUnbundled);

        addResourceConfigurations(overlay.getResourceConfigurations());

        addManifestPlaceholders(overlay.getManifestPlaceholders());

        addResValues(overlay.getResValues());

        addBuildConfigFields(overlay.getBuildConfigFields());

        setMultiDexEnabled(chooseNotNull(overlay.getMultiDexEnabled(), getMultiDexEnabled()));

        setMultiDexKeepFile(chooseNotNull(overlay.getMultiDexKeepFile(), getMultiDexKeepFile()));

        setMultiDexKeepProguard(
                chooseNotNull(overlay.getMultiDexKeepProguard(), getMultiDexKeepProguard()));

        getVectorDrawables()
                .setGeneratedDensities(
                        chooseNotNull(
                                overlay.getVectorDrawables().getGeneratedDensities(),
                                getVectorDrawables().getGeneratedDensities()));

        getVectorDrawables()
                .setUseSupportLibrary(
                        chooseNotNull(
                                overlay.getVectorDrawables().getUseSupportLibrary(),
                                getVectorDrawables().getUseSupportLibrary()));

        if (overlay instanceof AbstractProductFlavor) {
            if (missingDimensionSelections == null) {
                missingDimensionSelections = Maps.newHashMap();
            }
            missingDimensionSelections.putAll(
                    ((AbstractProductFlavor) overlay).getMissingDimensionStrategies());
        }

        // no need to merge missingDimensionStrategies, it's not queried from the merged flavor.
        // TODO this should all be clean up with the new variant DSL/API in 3.1
    }

    protected void cloneFrom(@NonNull ProductFlavor flavor) {
        // nothing to do
    }

    @Override
    protected void _initWith(@NonNull BaseConfig that) {
        super._initWith(that);
        if (that instanceof ProductFlavor) {
            ProductFlavor thatProductFlavor = (ProductFlavor) that;
            mDimension = thatProductFlavor.getDimension();
            mMinSdkVersion = thatProductFlavor.getMinSdkVersion();
            mTargetSdkVersion = thatProductFlavor.getTargetSdkVersion();
            mMaxSdkVersion = thatProductFlavor.getMaxSdkVersion();
            mRenderscriptTargetApi = thatProductFlavor.getRenderscriptTargetApi();
            mRenderscriptSupportModeEnabled = thatProductFlavor.getRenderscriptSupportModeEnabled();
            mRenderscriptSupportModeBlasEnabled =
                    thatProductFlavor.getRenderscriptSupportModeBlasEnabled();
            mRenderscriptNdkModeEnabled = thatProductFlavor.getRenderscriptNdkModeEnabled();

            mVersionCode = thatProductFlavor.getVersionCode();
            mVersionName = thatProductFlavor.getVersionName();
            setVersionNameSuffix(thatProductFlavor.getVersionNameSuffix());

            mApplicationId = thatProductFlavor.getApplicationId();

            mTestApplicationId = thatProductFlavor.getTestApplicationId();
            mTestInstrumentationRunner = thatProductFlavor.getTestInstrumentationRunner();
            mTestInstrumentationRunnerArguments =
                    Maps.newHashMap(thatProductFlavor.getTestInstrumentationRunnerArguments());
            mTestHandleProfiling = thatProductFlavor.getTestHandleProfiling();
            mTestFunctionalTest = thatProductFlavor.getTestFunctionalTest();

            // should this be a copy instead?
            mSigningConfig = thatProductFlavor.getSigningConfig();

            mVectorDrawablesOptions =
                    DefaultVectorDrawablesOptions.copyOf(thatProductFlavor.getVectorDrawables());
            mWearAppUnbundled = thatProductFlavor.getWearAppUnbundled();

            addResourceConfigurations(thatProductFlavor.getResourceConfigurations());
            addManifestPlaceholders(thatProductFlavor.getManifestPlaceholders());

            addResValues(thatProductFlavor.getResValues());
            addBuildConfigFields(thatProductFlavor.getBuildConfigFields());

            setMultiDexEnabled(thatProductFlavor.getMultiDexEnabled());

            setMultiDexKeepFile(thatProductFlavor.getMultiDexKeepFile());
            setMultiDexKeepProguard(thatProductFlavor.getMultiDexKeepProguard());
        }
        if (that instanceof AbstractProductFlavor) {
            AbstractProductFlavor thatAbstractProductFlavor = (AbstractProductFlavor) that;
            // the objects inside the map are immutable, so it's fine to keep them.
            missingDimensionSelections =
                    Maps.newHashMap(thatAbstractProductFlavor.getMissingDimensionStrategies());
        }
    }

    protected static <T> T chooseNotNull(T overlay, T base) {
        return overlay != null ? overlay : base;
    }

    public static String mergeApplicationIdSuffix(@Nullable String overlay, @Nullable String base){
        return Strings.nullToEmpty(joinWithSeparator(overlay, base, '.'));
    }

    public static String mergeVersionNameSuffix(@Nullable String overlay, @Nullable String base){
        return Strings.nullToEmpty(joinWithSeparator(overlay, base, null));
    }

    @Nullable
    private static String joinWithSeparator(@Nullable String overlay, @Nullable String base,
            @Nullable Character separator){
        if (!Strings.isNullOrEmpty(overlay)) {
            String baseSuffix = chooseNotNull(base, "");
            if (separator == null || overlay.charAt(0) == separator) {
                return baseSuffix + overlay;
            } else {
                return baseSuffix + separator + overlay;
            }
        }
        else{
            return base;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", mName)
                .add("dimension", mDimension)
                .add("minSdkVersion", mMinSdkVersion)
                .add("targetSdkVersion", mTargetSdkVersion)
                .add("renderscriptTargetApi", mRenderscriptTargetApi)
                .add("renderscriptSupportModeEnabled", mRenderscriptSupportModeEnabled)
                .add("renderscriptSupportModeBlasEnabled", mRenderscriptSupportModeBlasEnabled)
                .add("renderscriptNdkModeEnabled", mRenderscriptNdkModeEnabled)
                .add("versionCode", mVersionCode)
                .add("versionName", mVersionName)
                .add("applicationId", mApplicationId)
                .add("testApplicationId", mTestApplicationId)
                .add("testInstrumentationRunner", mTestInstrumentationRunner)
                .add("testInstrumentationRunnerArguments", mTestInstrumentationRunnerArguments)
                .add("testHandleProfiling", mTestHandleProfiling)
                .add("testFunctionalTest", mTestFunctionalTest)
                .add("signingConfig", mSigningConfig)
                .add("resConfig", mResourceConfiguration)
                .add("mBuildConfigFields", getBuildConfigFields())
                .add("mResValues", getResValues())
                .add("mProguardFiles", getProguardFiles())
                .add("mConsumerProguardFiles", getConsumerProguardFiles())
                .add("mManifestPlaceholders", getManifestPlaceholders())
                .add("mWearAppUnbundled", mWearAppUnbundled)
                .toString();
    }
}
