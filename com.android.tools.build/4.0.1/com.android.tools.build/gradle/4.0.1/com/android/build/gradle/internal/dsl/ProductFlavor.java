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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.builder.model.BaseConfig;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.provider.Property;

/**
 * Encapsulates all product flavors properties for this project.
 *
 * <p>Product flavors represent different versions of your project that you expect to co-exist on a
 * single device, the Google Play store, or repository. For example, you can configure 'demo' and
 * 'full' product flavors for your app, and each of those flavors can specify different features,
 * device requirements, resources, and application ID's--while sharing common source code and
 * resources. So, product flavors allow you to output different versions of your project by simply
 * changing only the components and settings that are different between them.
 *
 * <p>Configuring product flavors is similar to <a
 * href="https://developer.android.com/studio/build/build-variants.html#build-types">configuring
 * build types</a>: add them to the <code>productFlavors</code> block of your module's <code>
 * build.gradle</code> file and configure the settings you want. Product flavors support the same
 * properties as the {@link com.android.build.gradle.BaseExtension#getDefaultConfig} block—this is
 * because <code>defaultConfig</code> defines a {@link ProductFlavor} object that the plugin uses as
 * the base configuration for all other flavors. Each flavor you configure can then override any of
 * the default values in <code>defaultConfig</code>, such as the <a
 * href="https://d.android.com/studio/build/application-id.html"><code>applicationId</code></a>.
 *
 * <p>When using Android plugin 3.0.0 and higher, <a
 * href="com.android.build.gradle.internal.dsl.ProductFlavor.html#com.android.build.gradle.internal.dsl.ProductFlavor:dimension"><em>each
 * flavor must belong to a <code>dimension</code></a></em>.
 *
 * <p>When you configure product flavors, the Android plugin automatically combines them with your
 * {@link com.android.build.gradle.internal.dsl.BuildType} configurations to <a
 * href="https://developer.android.com/studio/build/build-variants.html">create build variants</a>.
 * If the plugin creates certain build variants that you don't want, you can <a
 * href="https://developer.android.com/studio/build/build-variants.html#filter-variants">filter
 * variants using <code>android.variantFilter</code></a>.
 */
public class ProductFlavor extends BaseFlavor implements com.android.build.api.dsl.ProductFlavor {

    @Inject
    public ProductFlavor(@NonNull String name, @NonNull DslScope dslScope) {
        super(name, dslScope);
        isDefault = dslScope.getObjectFactory().property(Boolean.class).convention(false);
    }

    private final Property<Boolean> isDefault;

    private ImmutableList<String> matchingFallbacks;

    public void setMatchingFallbacks(String... fallbacks) {
        this.matchingFallbacks = ImmutableList.copyOf(fallbacks);
    }

    public void setMatchingFallbacks(String fallback) {
        this.matchingFallbacks = ImmutableList.of(fallback);
    }

    public void setMatchingFallbacks(List<String> fallbacks) {
        this.matchingFallbacks = ImmutableList.copyOf(fallbacks);
    }

    /**
     * Specifies a sorted list of product flavors that the plugin should try to use when a direct
     * variant match with a local module dependency is not possible.
     *
     * <p>Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, when you build a "freeDebug" version of your app, the
     * plugin tries to match it with "freeDebug" versions of the local library modules the app
     * depends on.
     *
     * <p>However, there may be situations in which, for a given flavor dimension that exists in
     * both the app and its library dependencies, <b>your app includes flavors that a dependency
     * does not</b>. For example, consider if both your app and its library dependencies include a
     * "tier" flavor dimension. However, the "tier" dimension in the app includes "free" and "paid"
     * flavors, but one of its dependencies includes only "demo" and "paid" flavors for the same
     * dimension. When the plugin tries to build the "free" version of your app, it won't know which
     * version of the dependency to use, and you'll see an error message similar to the following:
     *
     * <pre>
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     *     project :app
     * </pre>
     *
     * <p>In this situation, you should use <code>matchingFallbacks</code> to specify alternative
     * matches for the app's "free" product flavor, as shown below:
     *
     * <pre>
     * // In the app's build.gradle file.
     * android {
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         paid {
     *             dimension 'tier'
     *             // Because the dependency already includes a "paid" flavor in its
     *             // "tier" dimension, you don't need to provide a list of fallbacks
     *             // for the "paid" flavor.
     *         }
     *         free {
     *             dimension 'tier'
     *             // Specifies a sorted list of fallback flavors that the plugin
     *             // should try to use when a dependency's matching dimension does
     *             // not include a "free" flavor. You may specify as many
     *             // fallbacks as you like, and the plugin selects the first flavor
     *             // that's available in the dependency's "tier" dimension.
     *             matchingFallbacks = ['demo', 'trial']
     *         }
     *     }
     * }
     * </pre>
     *
     * <p>Note that, for a given flavor dimension that exists in both the app and its library
     * dependencies, there is no issue when a library includes a product flavor that your app does
     * not. That's because the plugin simply never requests that flavor from the dependency.
     *
     * <p>If instead you are trying to resolve an issue in which <b>a library dependency includes a
     * flavor dimension that your app does not</b>, use <a
     * href="com.android.build.gradle.internal.dsl.DefaultConfig.html#com.android.build.gradle.internal.dsl.DefaultConfig:missingDimensionStrategy(java.lang.String,
     * java.lang.String)"> <code>missingDimensionStrategy</code></a>.
     *
     * @return the names of product flavors to use, in descending priority order
     */
    public List<String> getMatchingFallbacks() {
        if (matchingFallbacks == null) {
            return ImmutableList.of();
        }
        return matchingFallbacks;
    }

    /** Whether this product flavor should be selected in Studio by default */
    public Property<Boolean> getIsDefault() {
        return isDefault;
    }

    // Temp HACK. we need a way to access the Property<Boolean> from Kotlin
    // DO NOT USE
    @Deprecated
    public Property<Boolean> getIsDefaultProp() {
        return isDefault;
    }

    @Override
    public boolean isDefault() {
        return isDefault.get();
    }

    @Override
    public void setDefault(boolean isDefault) {
        this.isDefault.set(isDefault);
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault.set(isDefault);
    }

    @Override
    @NonNull
    protected DimensionRequest computeRequestedAndFallBacks(@NonNull List<String> requestedValues) {
        // in order to have different fallbacks per variant for missing dimensions, we are
        // going to actually have the flavor request itself (in the other dimension), with
        // a modified name (in order to not have collision in case 2 dimensions have the same
        // flavor names). So we will always fail to find the actual request and try for
        // the fallbacks.
        return new DimensionRequest(
                VariantManager.getModifiedName(getName()), ImmutableList.copyOf(requestedValues));
    }

    @Override
    protected void _initWith(@NonNull BaseConfig that) {
        // we need to avoid doing this because of Property objects that cannot
        // be set from themselves
        if (this == that) {
            return;
        }

        super._initWith(that);

        if (that instanceof ProductFlavor) {
            matchingFallbacks = ((ProductFlavor) that).matchingFallbacks;
        }
    }
}
