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
import com.android.builder.core.BuilderConstants;
import com.android.builder.signing.DefaultSigningConfig;
import java.io.File;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.model.ObjectFactory;

/** Factory to create SigningConfig object using an {@link ObjectFactory} to add the DSL methods. */
public class SigningConfigFactory implements NamedDomainObjectFactory<SigningConfig> {

    private final ObjectFactory objectFactory;
    private final File defaultDebugKeystoreLocation;

    public SigningConfigFactory(ObjectFactory objectFactory, File defaultDebugKeystoreLocation) {
        this.objectFactory = objectFactory;
        this.defaultDebugKeystoreLocation = defaultDebugKeystoreLocation;
    }

    @Override
    @NonNull
    public SigningConfig create(@NonNull String name) {
        SigningConfig signingConfig = objectFactory.newInstance(SigningConfig.class, name);
        if (BuilderConstants.DEBUG.equals(name)) {
            signingConfig.initWith(
                    DefaultSigningConfig.debugSigningConfig(defaultDebugKeystoreLocation));
        }
        return signingConfig;
    }
}
