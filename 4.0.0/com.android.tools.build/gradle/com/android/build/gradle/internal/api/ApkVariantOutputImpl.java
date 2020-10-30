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
import com.android.build.FilterData;
import com.android.build.VariantOutput;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.api.ApkVariantOutput;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.scope.TaskContainer;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
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

    @Inject
    public ApkVariantOutputImpl(
            @NonNull TaskContainer taskContainer,
            @NonNull DeprecationReporter deprecationReporter,
            @NonNull VariantOutputImpl variantOutput) {
        super(taskContainer, deprecationReporter, variantOutput);
    }

    @Nullable
    @Override
    public PackageAndroidArtifact getPackageApplication() {
        deprecationReporter.reportDeprecatedApi(
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
                    apkData.getOutputFileName());
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
        variantOutput.getVersionCode().set(versionCodeOverride);
    }

    @Override
    public int getVersionCodeOverride() {
        // consider throwing an exception instead, as this is not reliable.
        deprecationReporter.reportDeprecatedApi(
                "VariantOutput.versionCode()",
                "ApkVariantOutput.getVersionCodeOverride()",
                BaseVariantImpl.USE_PROPERTIES_DEPRECATION_URL,
                DeprecationReporter.DeprecationTarget.USE_PROPERTIES);
        return variantOutput.getVersionCode().get();
    }

    @Override
    public void setVersionNameOverride(String versionNameOverride) {
        variantOutput.getVersionName().set(versionNameOverride);
    }

    @Nullable
    @Override
    public String getVersionNameOverride() {
        deprecationReporter.reportDeprecatedApi(
                "VariantOutput.versionName()",
                "ApkVariantOutput.getVersionNameOverride()",
                BaseVariantImpl.USE_PROPERTIES_DEPRECATION_URL,
                DeprecationReporter.DeprecationTarget.USE_PROPERTIES);
        return variantOutput.getVersionName().getOrNull();
    }

    @Override
    public int getVersionCode() {
        return variantOutput.getVersionCode().get();
    }

    @Override
    public String getFilter(VariantOutput.FilterType filterType) {
        FilterData filterData = apkData.getFilter(filterType);
        return filterData != null ? filterData.getIdentifier() : null;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("apkData", apkData).toString();
    }
}
