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

package com.android.builder.signing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.SigningConfig;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.security.KeyStore;

/**
 * SigningConfig encapsulates the information necessary to access certificates in a keystore file
 * that can be used to sign APKs.
 */
public class DefaultSigningConfig implements SigningConfig {

    public static final String DEFAULT_PASSWORD = "android";
    public static final String DEFAULT_ALIAS = "AndroidDebugKey";

    @NonNull
    protected final String mName;
    private File mStoreFile = null;
    private String mStorePassword = null;
    private String mKeyAlias = null;
    private String mKeyPassword = null;
    private String mStoreType = KeyStore.getDefaultType();
    private boolean mV1SigningEnabled = true;
    private boolean mV2SigningEnabled = true;
    private boolean mV1SigningConfigured = false;
    private boolean mV2SigningConfigured = false;

    /**
     * Creates a {@link DefaultSigningConfig} that uses the default debug alias and passwords.
     */
    @NonNull
    public static DefaultSigningConfig debugSigningConfig(File storeFile) {
        DefaultSigningConfig result = new DefaultSigningConfig(BuilderConstants.DEBUG);
        result.mStoreFile = storeFile;
        result.mStorePassword = DEFAULT_PASSWORD;
        result.mKeyAlias = DEFAULT_ALIAS;
        result.mKeyPassword = DEFAULT_PASSWORD;
        return result;
    }

    /**
     * Creates a {@link DefaultSigningConfig}.
     */
    public DefaultSigningConfig(@NonNull String name) {
        mName = name;
    }

    @Override
    @NonNull
    public String getName() {
        return mName;
    }

    @Override
    @Nullable
    public File getStoreFile() {
        return mStoreFile;
    }

    @NonNull
    public DefaultSigningConfig setStoreFile(File storeFile) {
        mStoreFile = storeFile;
        return this;
    }

    @Override
    @Nullable
    public String getStorePassword() {
        return mStorePassword;
    }

    @NonNull
    public DefaultSigningConfig setStorePassword(String storePassword) {
        mStorePassword = storePassword;
        return this;
    }

    @Override
    @Nullable
    public String getKeyAlias() {
        return mKeyAlias;
    }

    @NonNull
    public DefaultSigningConfig setKeyAlias(String keyAlias) {
        mKeyAlias = keyAlias;
        return this;
    }

    @Override
    @Nullable
    public String getKeyPassword() {
        return mKeyPassword;
    }

    @NonNull
    public DefaultSigningConfig setKeyPassword(String keyPassword) {
        mKeyPassword = keyPassword;
        return this;
    }

    @Override
    @Nullable
    public String getStoreType() {
        return mStoreType;
    }

    @NonNull
    public DefaultSigningConfig setStoreType(String storeType) {
        mStoreType = storeType;
        return this;
    }

    @Override
    public boolean isV1SigningEnabled() {
        return mV1SigningEnabled;
    }

    public void setV1SigningEnabled(boolean enabled) {
        mV1SigningEnabled = enabled;
        mV1SigningConfigured = true;
    }

    @Override
    public boolean isV2SigningEnabled() {
        return mV2SigningEnabled;
    }

    public void setV2SigningEnabled(boolean enabled) {
        mV2SigningEnabled = enabled;
        mV2SigningConfigured = true;
    }

    /** Returns whether v1SigningEnabled is configured by the user through DSL. */
    public boolean isV1SigningConfigured() {
        return mV1SigningConfigured;
    }

    /** Returns whether v2SigningEnabled is configured by the user through DSL. */
    public boolean isV2SigningConfigured() {
        return mV2SigningConfigured;
    }

    /** Note: this function is only used by AGP internally, not by users. */
    protected void internalSetV1SigningEnabled(boolean enabled) {
        mV1SigningEnabled = enabled;
    }

    /** Note: this function is only used by AGP internally, not by users. */
    protected void internalSetV2SigningEnabled(boolean enabled) {
        mV2SigningEnabled = enabled;
    }

    @Override
    public boolean isSigningReady() {
        return mStoreFile != null &&
                mStorePassword != null &&
                mKeyAlias != null &&
                mKeyPassword != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultSigningConfig that = (DefaultSigningConfig) o;

        if (mKeyAlias != null ?
                !mKeyAlias.equals(that.mKeyAlias) :
                that.mKeyAlias != null)
            return false;
        if (mKeyPassword != null ?
                !mKeyPassword.equals(that.mKeyPassword) :
                that.mKeyPassword != null)
            return false;
        if (mStoreFile != null ?
                !mStoreFile.equals(that.mStoreFile) :
                that.mStoreFile != null)
            return false;
        if (mStorePassword != null ?
                !mStorePassword.equals(that.mStorePassword) :
                that.mStorePassword != null)
            return false;
        if (mStoreType != null ?
                !mStoreType.equals(that.mStoreType) :
                that.mStoreType != null)
            return false;
        if (mV1SigningEnabled != that.mV1SigningEnabled) return false;
        if (mV2SigningEnabled != that.mV2SigningEnabled) return false;
        if (mV1SigningConfigured != that.mV1SigningConfigured) return false;
        if (mV2SigningConfigured != that.mV2SigningConfigured) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mStoreFile != null ?
                mStoreFile.hashCode() : 0);
        result = 31 * result + (mStorePassword != null ?
                mStorePassword.hashCode() : 0);
        result = 31 * result + (mKeyAlias != null ? mKeyAlias.hashCode() : 0);
        result = 31 * result + (mKeyPassword != null ? mKeyPassword.hashCode() : 0);
        result = 31 * result + (mStoreType != null ? mStoreType.hashCode() : 0);
        result = 31 * result + (mV1SigningEnabled ? 17 : 0);
        result = 31 * result + (mV2SigningEnabled ? 17 : 0);
        result = 31 * result + (mV1SigningConfigured ? 17 : 0);
        result = 31 * result + (mV2SigningConfigured ? 17 : 0);
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("storeFile", mStoreFile.getAbsolutePath())
                .add("storePassword", mStorePassword)
                .add("keyAlias", mKeyAlias)
                .add("keyPassword", mKeyPassword)
                .add("storeType", mStoreType)
                .add("v1SigningEnabled", mV1SigningEnabled)
                .add("v2SigningEnabled", mV2SigningEnabled)
                .add("v1SigningConfigured", mV1SigningConfigured)
                .add("v2SigningConfigured", mV2SigningConfigured)
                .toString();
    }
}
