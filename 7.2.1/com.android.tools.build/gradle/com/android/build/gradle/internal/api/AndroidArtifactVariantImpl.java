/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.gradle.api.AndroidArtifactVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.errors.IssueReporter;
import com.android.builder.model.SigningConfig;
import java.util.Set;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * Implementation of the {@link AndroidArtifactVariant} interface around a {@link ApkVariantData}
 * object.
 */
public abstract class AndroidArtifactVariantImpl extends BaseVariantImpl
        implements AndroidArtifactVariant {

    protected AndroidArtifactVariantImpl(
            @NonNull ComponentImpl component,
            @NonNull VariantPropertiesApiServices services,
            @NonNull ReadOnlyObjectProvider immutableObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(component, services, immutableObjectProvider, outputs);
    }

    @NonNull
    @Override
    protected abstract ApkVariantData getVariantData();

    @Override
    public SigningConfig getSigningConfig() {
        if (component instanceof ApkCreationConfig) {
            return readOnlyObjectProvider.getSigningConfig(
                    ((ApkCreationConfig) component).getDslSigningConfig());
        } else return null;
    }

    @Override
    public boolean isSigningReady() {
        SigningConfig signingConfig = getSigningConfig();
        if (signingConfig != null) {
            return signingConfig.isSigningReady();
        } else return false;
    }

    private Integer _versionCode = null;
    private String _versionName = null;

    @Nullable
    @Override
    public String getVersionName() {
        if (!services.getProjectOptions().get(BooleanOption.ENABLE_LEGACY_API)) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            new RuntimeException(
                                    "Access to deprecated legacy com.android.build.gradle.api.VersionedVariant.getVersionName() requires compatibility mode for Property values in new com.android.build.api.variant.VariantOutput.versionName\n"
                                            + ComponentImpl.Companion.getENABLE_LEGACY_API()));
            // return default value during sync
            return null;
        }
        synchronized (this) {
            if (_versionName == null) {
                _versionName = component.getOutputs().getMainSplit().getVersionName().getOrNull();
            }
        }

        return _versionName;
    }

    @Override
    public int getVersionCode() {
        if (!services.getProjectOptions().get(BooleanOption.ENABLE_LEGACY_API)) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            new RuntimeException(
                                    "Access to deprecated legacy com.android.build.gradle.api.VersionedVariant.getVersionCode() requires compatibility mode for Property values in new com.android.build.api.variant.VariantOutput.versionCode\n"
                                            + ComponentImpl.Companion.getENABLE_LEGACY_API()));
            // return default value during sync
            return -1;
        }

        synchronized (this) {
            if (_versionCode == null) {
                _versionCode = component.getOutputs().getMainSplit().getVersionCode().getOrElse(-1);
            }
        }
        return _versionCode;
    }

    @NonNull
    @Override
    public Set<String> getCompatibleScreens() {
        return getVariantData().getCompatibleScreens();
    }
}
