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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An implementation of BaseConfig specifically for sending as part of the Android model
 * through the Gradle tooling API.
 */
@Immutable
abstract class BaseConfigImpl implements BaseConfig, Serializable {

    @Nullable
    private final String mApplicationIdSuffix;
    @Nullable
    private final String mVersionNameSuffix;
    @NonNull
    private final Map<String, Object> mManifestPlaceholders;
    @NonNull
    private final Map<String, ClassField> mBuildConfigFields;
    @NonNull
    private final Map<String, ClassField> mResValues;
    @Nullable
    private final Boolean mMultiDexEnabled;
    @Nullable
    private final File mMultiDexKeepFile;
    @Nullable
    private final File mMultiDexKeepProguard;

    protected BaseConfigImpl(@NonNull BaseConfig baseConfig) {
        mApplicationIdSuffix = baseConfig.getApplicationIdSuffix();
        mVersionNameSuffix = baseConfig.getVersionNameSuffix();
        mManifestPlaceholders = ImmutableMap.copyOf(baseConfig.getManifestPlaceholders());
        mBuildConfigFields = ImmutableMap.copyOf(baseConfig.getBuildConfigFields());
        mResValues = ImmutableMap.copyOf(baseConfig.getResValues());
        mMultiDexEnabled = baseConfig.getMultiDexEnabled();
        mMultiDexKeepFile = baseConfig.getMultiDexKeepFile();
        mMultiDexKeepProguard = baseConfig.getMultiDexKeepProguard();
    }

    @Nullable
    @Override
    public String getApplicationIdSuffix() {
        return mApplicationIdSuffix;
    }

    @Nullable
    @Override
    public String getVersionNameSuffix() {
        return mVersionNameSuffix;
    }

    @NonNull
    @Override
    public Map<String, ClassField> getBuildConfigFields() {
        return mBuildConfigFields;
    }

    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        return mResValues;
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<File> getConsumerProguardFiles() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public Collection<File> getTestProguardFiles() {
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public Map<String, Object> getManifestPlaceholders() {
        return mManifestPlaceholders;
    }

    @Override
    @Nullable
    public Boolean getMultiDexEnabled() {
        return mMultiDexEnabled;
    }

    @Nullable
    @Override
    public File getMultiDexKeepFile() {
        return mMultiDexKeepFile;
    }

    @Nullable
    @Override
    public File getMultiDexKeepProguard() {
        return mMultiDexKeepProguard;
    }

    @Override
    public String toString() {
        return "BaseConfigImpl{"
                + "applicationIdSuffix='"
                + mApplicationIdSuffix
                + '\''
                + ", versionNameSuffix='"
                + mVersionNameSuffix
                + '\''
                + ", mManifestPlaceholders="
                + mManifestPlaceholders
                + ", mBuildConfigFields="
                + mBuildConfigFields
                + ", mResValues="
                + mResValues
                + ", mMultiDexEnabled="
                + mMultiDexEnabled
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseConfigImpl that = (BaseConfigImpl) o;
        return Objects.equals(mApplicationIdSuffix, that.mApplicationIdSuffix)
                && Objects.equals(mVersionNameSuffix, that.mVersionNameSuffix)
                && Objects.equals(mManifestPlaceholders, that.mManifestPlaceholders)
                && Objects.equals(mBuildConfigFields, that.mBuildConfigFields)
                && Objects.equals(mResValues, that.mResValues)
                && Objects.equals(mMultiDexEnabled, that.mMultiDexEnabled)
                && Objects.equals(mMultiDexKeepFile, that.mMultiDexKeepFile)
                && Objects.equals(mMultiDexKeepProguard, that.mMultiDexKeepProguard);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mApplicationIdSuffix,
                mVersionNameSuffix,
                mManifestPlaceholders,
                mBuildConfigFields,
                mResValues,
                mMultiDexEnabled,
                mMultiDexKeepFile,
                mMultiDexKeepProguard);
    }
}
