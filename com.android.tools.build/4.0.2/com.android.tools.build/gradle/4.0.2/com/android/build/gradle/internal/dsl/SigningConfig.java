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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.builder.signing.DefaultSigningConfig;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.Serializable;
import javax.inject.Inject;
import org.gradle.api.Named;

/** DSL object for configuring signing configs. */
public class SigningConfig extends DefaultSigningConfig
        implements Serializable, Named, com.android.build.api.dsl.SigningConfig {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a SigningConfig with a given name.
     *
     * @param name the name of the signingConfig.
     */
    @Inject
    public SigningConfig(@NonNull String name) {
        super(name);
    }

    public SigningConfig initWith(com.android.builder.model.SigningConfig that) {
        setStoreFile(that.getStoreFile());
        setStorePassword(that.getStorePassword());
        setKeyAlias(that.getKeyAlias());
        setKeyPassword(that.getKeyPassword());
        internalSetV1SigningEnabled(that.isV1SigningEnabled());
        internalSetV2SigningEnabled(that.isV2SigningEnabled());
        setStoreType(that.getStoreType());
        return this;
    }

    /**
     * Store file used when signing.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/app-signing.html">
     * Signing Your Applications</a>
     */
    @Override
    public File getStoreFile() {
        return super.getStoreFile();
    }

    /**
     * Store password used when signing.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/app-signing.html">
     * Signing Your Applications</a>
     */
    @Override
    public String getStorePassword() {
        return super.getStorePassword();
    }

    /**
     * Key alias used when signing.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/app-signing.html">
     * Signing Your Applications</a>
     */
    @Override
    public String getKeyAlias() {
        return super.getKeyAlias();
    }

    /**
     * Key password used when signing.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/app-signing.html">
     * Signing Your Applications</a>
     */
    @Override
    public String getKeyPassword() {
        return super.getKeyPassword();
    }

    /**
     * Store type used when signing.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/app-signing.html">
     * Signing Your Applications</a>
     */
    @Override
    public String getStoreType() {
        return super.getStoreType();
    }

    /**
     * Whether signing using JAR Signature Scheme (aka v1 signing) is enabled.
     *
     * <p>See <a href="https://developer.android.com/tools/publishing/app-signing.html">
     * Signing Your Applications</a>
     */
    @Override
    public boolean isV1SigningEnabled() {
        return super.isV1SigningEnabled();
    }

    /**
     * Whether signing using APK Signature Scheme v2 (aka v2 signing) is enabled.
     *
     * <p>See <a href="https://developer.android.com/tools/publishing/app-signing.html">
     * Signing Your Applications</a>
     */
    @Override
    public boolean isV2SigningEnabled() {
        return super.isV2SigningEnabled();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", mName)
                .add(
                        "storeFile",
                        getStoreFile() != null ? getStoreFile().getAbsolutePath() : "null")
                .add("storePassword", getStorePassword())
                .add("keyAlias", getKeyAlias())
                .add("keyPassword", getKeyPassword())
                .add("storeType", getStoreType())
                .add("v1SigningEnabled", isV1SigningEnabled())
                .add("v2SigningEnabled", isV2SigningEnabled())
                .add("v1SigningConfigured", isV1SigningConfigured())
                .add("v2SigningConfigured", isV2SigningConfigured())
                .toString();
    }
}
