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
package com.android.ide.common.gradle.model;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Creates a deep copy of a {@link BaseConfig}. */
public abstract class IdeBaseConfigImpl implements IdeBaseConfig, Serializable {
    // Increase the value when adding/removing fields or when changing the
    // serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myName;
    @NonNull private final Map<String, ClassField> myResValues;
    @NonNull private final Collection<File> myProguardFiles;
    @NonNull private final Collection<File> myConsumerProguardFiles;
    @NonNull private final Map<String, Object> myManifestPlaceholders;
    @Nullable private final String myApplicationIdSuffix;
    @Nullable private final String myVersionNameSuffix;
    @Nullable private final Boolean myMultiDexEnabled;
    private final int hashCode;

    // Used for serialization by the IDE.
    IdeBaseConfigImpl() {
        myName = "";
        myResValues = Collections.emptyMap();
        myProguardFiles = Collections.emptyList();
        myConsumerProguardFiles = Collections.emptyList();
        myManifestPlaceholders = Collections.emptyMap();
        myApplicationIdSuffix = null;
        myVersionNameSuffix = null;
        myMultiDexEnabled = null;

        hashCode = 0;
    }

    protected IdeBaseConfigImpl(@NonNull BaseConfig config, @NonNull ModelCache modelCache) {
        myName = config.getName();
        myResValues =
                IdeModel.copy(
                        config.getResValues(),
                        modelCache,
                        classField -> new IdeClassFieldImpl(classField));
        myProguardFiles = ImmutableList.copyOf(config.getProguardFiles());
        myConsumerProguardFiles = ImmutableList.copyOf(config.getConsumerProguardFiles());
        // AGP may return internal Groovy GString implementation as a value in manifestPlaceholders
        // map. It cannot be serialized
        // with IDEA's external system serialization. We convert values to String to make them
        // usable as they are converted to String by
        // the manifest merger anyway.
        myManifestPlaceholders =
                config.getManifestPlaceholders().entrySet().stream()
                        .collect(toImmutableMap(it -> it.getKey(), it -> it.getValue().toString()));
        myApplicationIdSuffix = config.getApplicationIdSuffix();
        myVersionNameSuffix = IdeModel.copyNewProperty(config::getVersionNameSuffix, null);
        myMultiDexEnabled = IdeModel.copyNewProperty(config::getMultiDexEnabled, null);

        hashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public Map<String, ClassField> getBuildConfigFields() {
        throw new UnusedModelMethodException("getBuildConfigFields");
    }

    @Override
    @NonNull
    public Map<String, ClassField> getResValues() {
        return myResValues;
    }

    @Override
    @NonNull
    public Collection<File> getProguardFiles() {
        return myProguardFiles;
    }

    @Override
    @NonNull
    public Collection<File> getConsumerProguardFiles() {
        return myConsumerProguardFiles;
    }

    @Override
    @NonNull
    public Collection<File> getTestProguardFiles() {
        throw new UnusedModelMethodException("getTestProguardFiles");
    }

    @Override
    @NonNull
    public Map<String, Object> getManifestPlaceholders() {
        return myManifestPlaceholders;
    }

    @Override
    @Nullable
    public String getApplicationIdSuffix() {
        return myApplicationIdSuffix;
    }

    @Override
    @Nullable
    public String getVersionNameSuffix() {
        return myVersionNameSuffix;
    }

    @Override
    @Nullable
    public Boolean getMultiDexEnabled() {
        return myMultiDexEnabled;
    }

    @Override
    @Nullable
    public File getMultiDexKeepFile() {
        throw new UnusedModelMethodException("getMultiDexKeepFile");
    }

    @Override
    @Nullable
    public File getMultiDexKeepProguard() {
        throw new UnusedModelMethodException("getMultiDexKeepProguard");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeBaseConfigImpl)) {
            return false;
        }
        IdeBaseConfigImpl config = (IdeBaseConfigImpl) o;
        return config.canEqual(this)
                && Objects.equals(myName, config.myName)
                && Objects.deepEquals(myResValues, config.myResValues)
                && Objects.deepEquals(myProguardFiles, config.myProguardFiles)
                && Objects.deepEquals(myConsumerProguardFiles, config.myConsumerProguardFiles)
                && Objects.deepEquals(myManifestPlaceholders, config.myManifestPlaceholders)
                && Objects.equals(myApplicationIdSuffix, config.myApplicationIdSuffix)
                && Objects.equals(myVersionNameSuffix, config.myVersionNameSuffix)
                && Objects.equals(myMultiDexEnabled, config.myMultiDexEnabled);
    }

    public boolean canEqual(Object other) {
        // See: http://www.artima.com/lejava/articles/equality.html
        return other instanceof IdeBaseConfigImpl;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    protected int calculateHashCode() {
        return Objects.hash(
                myName,
                myResValues,
                myProguardFiles,
                myConsumerProguardFiles,
                myManifestPlaceholders,
                myApplicationIdSuffix,
                myVersionNameSuffix,
                myMultiDexEnabled);
    }

    @Override
    public String toString() {
        return "myName='"
                + myName
                + '\''
                + ", myResValues="
                + myResValues
                + ", myProguardFiles="
                + myProguardFiles
                + ", myConsumerProguardFiles="
                + myConsumerProguardFiles
                + ", myManifestPlaceholders="
                + myManifestPlaceholders
                + ", myApplicationIdSuffix='"
                + myApplicationIdSuffix
                + '\''
                + ", myVersionNameSuffix='"
                + myVersionNameSuffix
                + '\''
                + ", myMultiDexEnabled="
                + myMultiDexEnabled;
    }
}
