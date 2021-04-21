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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SigningConfig;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;

/** Creates a deep copy of a {@link SigningConfig}. */
public final class IdeSigningConfig implements SigningConfig, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final String myName;
    @Nullable private final File myStoreFile;
    @Nullable private final String myStorePassword;
    @Nullable private final String myKeyAlias;
    @Nullable private final Boolean myV1SigningEnabled;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeSigningConfig() {
        myName = "";
        myStoreFile = null;
        myStorePassword = null;
        myKeyAlias = null;
        myV1SigningEnabled = null;

        myHashCode = 0;
    }

    public IdeSigningConfig(@NonNull SigningConfig config) {
        myName = config.getName();
        myStoreFile = config.getStoreFile();
        myStorePassword = config.getStorePassword();
        myKeyAlias = config.getKeyAlias();
        myV1SigningEnabled = IdeModel.copyNewProperty(config::isV1SigningEnabled, null);

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @Nullable
    public File getStoreFile() {
        return myStoreFile;
    }

    @Override
    @Nullable
    public String getStorePassword() {
        return myStorePassword;
    }

    @Override
    @Nullable
    public String getKeyAlias() {
        return myKeyAlias;
    }

    @Override
    @Nullable
    public String getKeyPassword() {
        throw new UnusedModelMethodException("getKeyPassword");
    }

    @Override
    @Nullable
    public String getStoreType() {
        throw new UnusedModelMethodException("getStoreType");
    }

    @Override
    public boolean isV1SigningEnabled() {
        if (myV1SigningEnabled != null) {
            return myV1SigningEnabled;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: SigningConfig.isV1SigningEnabled()");
    }

    @Override
    public boolean isV2SigningEnabled() {
        throw new UnusedModelMethodException("isV2SigningEnabled");
    }

    @Override
    public boolean isSigningReady() {
        throw new UnusedModelMethodException("isSigningReady");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeSigningConfig)) {
            return false;
        }
        IdeSigningConfig config = (IdeSigningConfig) o;
        return Objects.equals(myV1SigningEnabled, config.myV1SigningEnabled)
                && Objects.equals(myName, config.myName)
                && Objects.equals(myStoreFile, config.myStoreFile)
                && Objects.equals(myStorePassword, config.myStorePassword)
                && Objects.equals(myKeyAlias, config.myKeyAlias);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myName, myStoreFile, myStorePassword, myKeyAlias, myV1SigningEnabled);
    }

    @Override
    public String toString() {
        return "IdeSigningConfig{"
                + "myName='"
                + myName
                + '\''
                + ", myStoreFile="
                + myStoreFile
                + ", myStorePassword='"
                + myStorePassword
                + '\''
                + ", myKeyAlias='"
                + myKeyAlias
                + '\''
                + ", myV1SigningEnabled="
                + myV1SigningEnabled
                + '}';
    }
}
