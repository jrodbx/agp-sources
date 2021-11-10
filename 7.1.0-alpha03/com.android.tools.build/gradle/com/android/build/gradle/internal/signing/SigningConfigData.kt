/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.signing

import com.android.build.api.dsl.SigningConfig
import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SigningOptions
import com.google.common.hash.Hashing
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.io.Serializable

/**
 * A derivative of the [SigningConfig] object, with input annotations on all of its properties to be
 * used with `@Nested`, with an important exception below.
 *
 * IMPORTANT: To support cache relocatability, we annotate storeFile with `PathSensitivity.NONE` to
 * ignore the store file's path. This requires that the tasks consuming this object do not take the
 * store file's path as input (i.e., the store file's path does not affect the output of those
 * tasks). If the store file's path does affect the output of a task (e.g., as with
 * `SigningConfigWriterTask`), the task must explicitly declare the store file's path as an input.
 */
data class SigningConfigData(

    @get:Input
    val name: String,

    @get:Input
    @get:Optional
    val storeType: String?,

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE) // See explanation at the javadoc of SigningConfigData
    @get:Optional
    val storeFile: File?,

    // Don't set the password as @Input as Gradle may store it to disk. Instead, we set the
    // password's hash as @Input (see getStorePasswordHash()).
    @get:Internal
    val storePassword: String?,

    @get:Input
    @get:Optional
    val keyAlias: String?,

    // Don't set the password as @Input as Gradle may store it to disk. Instead, we set the
    // password's hash as @Input (see getKeyPasswordHash()).
    @get:Internal
    val keyPassword: String?
) : Serializable {

    @Input
    @Optional
    fun getStorePasswordHash(): String? =
        storePassword?.let { Hashing.sha256().hashUnencodedChars(it).toString() }

    @Input
    @Optional
    fun getKeyPasswordHash(): String? =
        keyPassword?.let { Hashing.sha256().hashUnencodedChars(it).toString() }


    companion object {

        private const val serialVersionUID = 2L

        fun fromSigningConfig(signingConfig: SigningConfigImpl): SigningConfigData {
            return SigningConfigData(
                name = signingConfig.name ?: "",
                storeType = signingConfig.storeType.orNull,
                storeFile = signingConfig.storeFile.orNull,
                storePassword = signingConfig.storePassword.orNull,
                keyAlias = signingConfig.keyAlias.orNull,
                keyPassword = signingConfig.keyPassword.orNull
            )
        }

        fun fromDslSigningConfig(signingConfig: SigningConfig): SigningConfigData {
            return SigningConfigData(
                name = "default",
                storeType = signingConfig.storeType,
                storeFile = signingConfig.storeFile,
                storePassword = signingConfig.storePassword,
                keyAlias = signingConfig.keyAlias,
                keyPassword = signingConfig.keyPassword
            )
        }

        fun fromProjectOptions(projectOptions: ProjectOptions): SigningConfigData? {
            val signingOptions = SigningOptions.readSigningOptions(projectOptions)
            return signingOptions?.let {
                SigningConfigData(
                    name = SigningOptions.SIGNING_CONFIG_NAME,
                    storeType = signingOptions.storeType,
                    storeFile = File(signingOptions.storeFile),
                    storePassword = signingOptions.storePassword,
                    keyAlias = signingOptions.keyAlias,
                    keyPassword = signingOptions.keyPassword
                )
            }
        }
    }
}
