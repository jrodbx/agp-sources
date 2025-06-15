/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("BundleTaskUtil")

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.tools.build.bundletool.commands.AddTransparencyCommand
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.commands.BuildSdkApksCommand
import com.android.tools.build.bundletool.model.SigningConfiguration
import com.android.tools.build.bundletool.model.Password
import com.android.tools.build.bundletool.model.SignerConfig
import java.io.File
import java.security.KeyStore
import java.util.function.Supplier
import java.util.Optional

private fun toPassword(password: String?): Optional<Password> =
    Optional.ofNullable(password?.let {
        Password(Supplier { KeyStore.PasswordProtection("$it".toCharArray()) })
    })

internal fun BuildApksCommand.Builder.setSigningConfiguration(
        keystoreFile: File?, keystorePassword: String?, keyAlias: String?, keyPassword: String?
):
        BuildApksCommand.Builder {
    if (keystoreFile == null) {
        return this
    }
    setSigningConfiguration(
        createSigningConfiguration(
            keystoreFile = keystoreFile,
            keystorePassword = keystorePassword,
            keyAlias = keyAlias,
            keyPassword = keyPassword,
        )
    )
    return this
}

internal fun BuildSdkApksCommand.Builder.setSigningConfiguration(signingConfig: SigningConfigData): BuildSdkApksCommand.Builder {
    if (signingConfig.storeFile == null) {
        return this
    }
    return setSigningConfiguration(createSigningConfiguration(
            keystoreFile = signingConfig.storeFile,
            keystorePassword = signingConfig.storePassword,
            keyAlias = signingConfig.keyAlias,
            keyPassword = signingConfig.keyPassword)
    )
}

internal fun createSigningConfiguration(
        keystoreFile: File, keystorePassword: String?, keyAlias: String?, keyPassword: String?
) = SigningConfiguration.extractFromKeystore(
        keystoreFile.toPath(), keyAlias, toPassword(keystorePassword), toPassword(keyPassword)
)

internal fun AddTransparencyCommand.Builder.setSignerConfig(signingConfig: SigningConfigData):
        AddTransparencyCommand.Builder {
    setSignerConfig(
        SignerConfig.extractFromKeystore(
            signingConfig.storeFile?.toPath(),
            signingConfig.keyAlias,
            toPassword(signingConfig.storePassword),
            toPassword(signingConfig.keyPassword)
        )
    )
    return this
}

internal fun createSigningConfig(signingConfig: SigningConfigData): SigningConfiguration? {
    return SigningConfiguration.extractFromKeystore(
            signingConfig.storeFile?.toPath(),
            signingConfig.keyAlias,
            toPassword(signingConfig.storePassword),
            toPassword(signingConfig.keyPassword)
    )
}


