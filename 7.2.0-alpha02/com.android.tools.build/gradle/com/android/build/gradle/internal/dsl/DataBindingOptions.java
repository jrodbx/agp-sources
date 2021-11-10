/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.build.api.dsl.ApplicationBuildFeatures;
import com.android.build.api.dsl.BuildFeatures;
import com.android.build.api.dsl.DynamicFeatureBuildFeatures;
import com.android.build.api.dsl.LibraryBuildFeatures;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.options.BooleanOption;

import java.util.function.Supplier;
import javax.inject.Inject;

/** DSL object for configuring databinding options. */
public class DataBindingOptions
        implements com.android.builder.model.DataBindingOptions,
                com.android.build.api.dsl.DataBinding {
    @NonNull private final Supplier<BuildFeatures> featuresProvider;
    @NonNull private final DslServices dslServices;
    private String version;
    private boolean addDefaultAdapters = true;
    private Boolean addKtx = null;
    private boolean enabledForTests = false;

    @Inject
    public DataBindingOptions(
            @NonNull Supplier<BuildFeatures> featuresProvider, @NonNull DslServices dslServices) {
        this.featuresProvider = featuresProvider;
        this.dslServices = dslServices;
    }

    /**
     * The version of data binding to use.
     */
    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }


    /** Whether to enable data binding. */
    @Override
    public boolean isEnabled() {
        final BuildFeatures buildFeatures = featuresProvider.get();
        Boolean bool = false;
        if (buildFeatures instanceof ApplicationBuildFeatures) {
            bool = ((ApplicationBuildFeatures) buildFeatures).getDataBinding();
        } else if (buildFeatures instanceof LibraryBuildFeatures) {
            bool = ((LibraryBuildFeatures) buildFeatures).getDataBinding();
        } else if (buildFeatures instanceof DynamicFeatureBuildFeatures) {
            bool = ((DynamicFeatureBuildFeatures) buildFeatures).getDataBinding();
        }

        if (bool != null) {
            return bool;
        }
        return dslServices.getProjectOptions().get(BooleanOption.BUILD_FEATURE_DATABINDING);
    }

    @Override
    public void setEnabled(boolean enabled) {
        final BuildFeatures buildFeatures = featuresProvider.get();
        if (buildFeatures instanceof ApplicationBuildFeatures) {
            ((ApplicationBuildFeatures) buildFeatures).setDataBinding(enabled);
        } else if (buildFeatures instanceof LibraryBuildFeatures) {
            ((LibraryBuildFeatures) buildFeatures).setDataBinding(enabled);
        } else if (buildFeatures instanceof DynamicFeatureBuildFeatures) {
            ((DynamicFeatureBuildFeatures) buildFeatures).setDataBinding(enabled);
        } else {
            dslServices
                    .getLogger()
                    .warn("dataBinding.setEnabled has no impact on this sub-project type");
        }
    }

    /** Whether to add the default data binding adapters. */
    @Override
    public boolean getAddDefaultAdapters() {
        return addDefaultAdapters;
    }

    @Override
    public void setAddDefaultAdapters(boolean addDefaultAdapters) {
        this.addDefaultAdapters = addDefaultAdapters;
    }

    /** Whether to add the data binding KTX features. */
    @Override
    @Nullable
    public Boolean getAddKtx() {
        return addKtx;
    }

    @Override
    public void setAddKtx(@Nullable Boolean addKtx) {
        this.addKtx = addKtx;
    }

    /**
     * Whether to run data binding code generation for test projects
     */
    @Override
    public boolean isEnabledForTests() {
        return enabledForTests;
    }

    @Override
    public void setEnabledForTests(boolean enabledForTests) {
        this.enabledForTests = enabledForTests;
    }
}
