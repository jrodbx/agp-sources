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

package com.android.build.gradle.internal.api;

import static com.android.build.gradle.internal.api.BaseVariantImpl.TASK_ACCESS_DEPRECATION_URL;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.VariantOutput;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.api.ApkVariantOutput;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.scope.TaskContainer;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.builder.core.VariantType;
import com.android.builder.errors.IssueReporter;
import com.google.common.base.MoreObjects;
import java.io.File;
import javax.inject.Inject;
import org.gradle.api.Task;

/**
 * Implementation of variant output for apk-generating variants.
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public class ApkVariantOutputImpl extends BaseVariantOutputImpl implements ApkVariantOutput {

    @NonNull private VariantType variantType;

    @Inject
    public ApkVariantOutputImpl(
            @NonNull TaskContainer taskContainer,
            @NonNull BaseServices services,
            @NonNull VariantOutputImpl variantOutput,
            @NonNull VariantType variantType) {
        super(taskContainer, services, variantOutput);
        this.variantType = variantType;
    }

    @Nullable
    @Override
    public PackageAndroidArtifact getPackageApplication() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getPackageApplicationProvider()",
                        "variantOutput.getPackageApplication()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return taskContainer.getPackageAndroidTask().getOrNull();
    }

    @NonNull
    @Override
    public File getOutputFile() {
        PackageAndroidArtifact packageAndroidArtifact =
                taskContainer.getPackageAndroidTask().getOrNull();
        if (packageAndroidArtifact != null) {
            return new File(
                    packageAndroidArtifact.getOutputDirectory().get().getAsFile(),
                    variantOutput.getOutputFileName().get());
        } else {
            return super.getOutputFile();
        }
    }

    @Nullable
    @Override
    public Task getZipAlign() {
        return getPackageApplication();
    }

    @Override
    public void setVersionCodeOverride(int versionCodeOverride) {
        // only these modules can configure their versionCode
        if (variantType.isBaseModule()) {
            variantOutput.getVersionCode().set(versionCodeOverride);
        }
    }

    @Override
    public int getVersionCodeOverride() {
        // consider throwing an exception instead, as this is not reliable.
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "VariantOutput.versionCode()",
                        "ApkVariantOutput.getVersionCodeOverride()",
                        BaseVariantImpl.USE_PROPERTIES_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.USE_PROPERTIES);

        if (!services.getProjectOptions().get(BooleanOption.ENABLE_LEGACY_API)) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            new RuntimeException(
                                    "Access to deprecated legacy com.android.build.gradle.api.ApkVariantOutput.getVersionCodeOverride() requires compatibility mode for Property values in new com.android.build.api.variant.VariantOutput.versionCode\n"
                                            + ComponentImpl.Companion.getENABLE_LEGACY_API()));
            // return default value during sync
            return -1;
        }

        return variantOutput.getVersionCode().getOrElse(-1);
    }

    @Override
    public void setVersionNameOverride(String versionNameOverride) {
        // only these modules can configure their versionName
        if (variantType.isBaseModule()) {
            variantOutput.getVersionName().set(versionNameOverride);
        }
    }

    @Nullable
    @Override
    public String getVersionNameOverride() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "VariantOutput.versionName()",
                        "ApkVariantOutput.getVersionNameOverride()",
                        BaseVariantImpl.USE_PROPERTIES_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.USE_PROPERTIES);

        if (!services.getProjectOptions().get(BooleanOption.ENABLE_LEGACY_API)) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            new RuntimeException(
                                    "Access to deprecated legacy com.android.build.gradle.api.ApkVariantOutput.getVersionNameOverride() requires compatibility mode for Property values in new com.android.build.api.variant.VariantOutput.versionName\n"
                                            + ComponentImpl.Companion.getENABLE_LEGACY_API()));
            // return default value during sync
            return null;
        }

        return variantOutput.getVersionName().getOrNull();
    }

    @Override
    public int getVersionCode() {
        if (!services.getProjectOptions().get(BooleanOption.ENABLE_LEGACY_API)) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            new RuntimeException(
                                    "Access to deprecated legacy com.android.build.gradle.api.ApkVariantOutput.versionCode requires compatibility mode for Property values in new com.android.build.api.variant.VariantOutput.versionCode\n"
                                            + ComponentImpl.Companion.getENABLE_LEGACY_API()));
            // return default value during sync
            return -1;
        }

        return variantOutput.getVersionCode().getOrElse(-1);
    }

    @Override
    public String getFilter(VariantOutput.FilterType filterType) {
        FilterConfiguration filterConfiguration =
                variantOutput.getFilter(FilterConfiguration.FilterType.valueOf(filterType.name()));
        return filterConfiguration != null ? filterConfiguration.getIdentifier() : null;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("variantOutput", variantOutput).toString();
    }
}
