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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;

@Immutable
public final class SigningOptions {

    /**
     * Reads the override signing options from the project properties.
     *
     * @param options the project options to read
     * @return The signing options overrides, or null if the options are not present or are
     *     incomplete.
     */
    @Nullable
    public static SigningOptions readSigningOptions(@NonNull ProjectOptions options) {
        String signingStoreFile = options.get(StringOption.IDE_SIGNING_STORE_FILE);
        String signingStorePassword = options.get(StringOption.IDE_SIGNING_STORE_PASSWORD);
        String signingKeyAlias = options.get(StringOption.IDE_SIGNING_KEY_ALIAS);
        String signingKeyPassword = options.get(StringOption.IDE_SIGNING_KEY_PASSWORD);
        Boolean isV1SigningConfigured =
                options.get(OptionalBooleanOption.SIGNING_V1_ENABLED) != null;
        Boolean isV2SigningConfigured =
                options.get(OptionalBooleanOption.SIGNING_V2_ENABLED) != null;

        if (signingStoreFile != null
                && signingStorePassword != null
                && signingKeyAlias != null
                && signingKeyPassword != null) {

            return new SigningOptions(
                    signingStoreFile,
                    signingStorePassword,
                    signingKeyAlias,
                    signingKeyPassword,
                    options.get(StringOption.IDE_SIGNING_STORE_TYPE),
                    options.get(OptionalBooleanOption.SIGNING_V1_ENABLED),
                    options.get(OptionalBooleanOption.SIGNING_V2_ENABLED),
                    isV1SigningConfigured,
                    isV2SigningConfigured);
        }

        return null;
    }

    @NonNull private final String storeFile;
    @NonNull private final String storePassword;
    @NonNull private final String keyAlias;
    @NonNull private final String keyPassword;
    @Nullable private final String storeType;
    @Nullable private final Boolean v1Enabled;
    @Nullable private final Boolean v2Enabled;
    @NonNull private final Boolean v1Configured;
    @NonNull private final Boolean v2Configured;

    public SigningOptions(
            @NonNull String storeFile,
            @NonNull String storePassword,
            @NonNull String keyAlias,
            @NonNull String keyPassword,
            @Nullable String storeType,
            @Nullable Boolean v1Enabled,
            @Nullable Boolean v2Enabled,
            @NonNull Boolean v1Configured,
            @NonNull Boolean v2Configured) {
        this.storeFile = storeFile;
        this.storeType = storeType;
        this.storePassword = storePassword;
        this.keyAlias = keyAlias;
        this.keyPassword = keyPassword;
        this.v1Enabled = v1Enabled;
        this.v2Enabled = v2Enabled;
        this.v1Configured = v1Configured;
        this.v2Configured = v2Configured;
    }

    @NonNull
    public String getStoreFile() {
        return storeFile;
    }

    @NonNull
    public String getStorePassword() {
        return storePassword;
    }

    @NonNull
    public String getKeyAlias() {
        return keyAlias;
    }

    @NonNull
    public String getKeyPassword() {
        return keyPassword;
    }

    @Nullable
    public String getStoreType() {
        return storeType;
    }

    @Nullable
    public Boolean getV1Enabled() {
        return v1Enabled;
    }

    @Nullable
    public Boolean getV2Enabled() {
        return v2Enabled;
    }

    @NonNull
    public Boolean getV1Configured() {
        return v1Configured;
    }

    @NonNull
    public Boolean getV2Configured() {
        return v2Configured;
    }
}
