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

import com.android.build.gradle.internal.dsl.SigningConfig
import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.Hashing
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
 *
 * This class is immutable, which makes it more ideal than a mutable [SigningConfig] when passing
 * around.
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
    val keyPassword: String?,

    /**
     * The resolved value of whether V1 signing is enabled, based on either (1) a value injected by
     * the IDE or (2) the DSL's enableV1Signing value along with the minSdk and the injected target
     * API.
     */
    @get:Input
    val enableV1Signing: Boolean,

    /**
     * The resolved value of whether V2 signing is enabled, based on either (1) a value injected by
     * the IDE or (2) the DSL's enableV2Signing value along with the DSL's enableV3Signing value and
     * the injected target API.
     */
    @get:Input
    val enableV2Signing: Boolean,

    /**
     * The value from the DSL object, or a default value if the DSL object's value is null or
     * unspecified.
     */
    @get:Input
    val enableV3Signing: Boolean,

    /**
     * The value from the DSL object, or a default value if the DSL object's value is null or
     * unspecified.
     */
    @get:Input
    val enableV4Signing: Boolean
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

        private const val serialVersionUID = 1L

        // The lowest API with v2 signing support
        const val MIN_V2_SDK = 24
        // The lowest API with v3 signing support
        const val MIN_V3_SDK = 28
        // The lowest API with v4 signing support
        const val MIN_V4_SDK = 28

        // TODO - targetApi should be non-null only for optimized builds (b/160002908)
        fun fromSigningConfig(
            signingConfig: SigningConfig,
            minSdk: Int,
            targetApi: Int?
        ): SigningConfigData {
            return SigningConfigData(
                name = signingConfig.name,
                storeType = signingConfig.storeType,
                storeFile = signingConfig.storeFile,
                storePassword = signingConfig.storePassword,
                keyAlias = signingConfig.keyAlias,
                keyPassword = signingConfig.keyPassword,
                enableV1Signing = enableV1Signing(signingConfig, minSdk, targetApi),
                enableV2Signing = enableV2Signing(signingConfig, minSdk, targetApi),
                enableV3Signing = enableV3Signing(signingConfig, targetApi),
                enableV4Signing = enableV4Signing(signingConfig, targetApi)
            )
        }

        /**
         * This method returns whether to sign with v1 signature.
         *
         * @param signingConfig the DSL signingConfig object
         * @param minSdk the minimum SDK
         * @param targetApi optional injected target Api
         * @return if we actually sign with v1 signature
         */
        @VisibleForTesting
        fun enableV1Signing(
            signingConfig: SigningConfig,
            minSdk: Int,
            targetApi: Int?
        ): Boolean {
            val enableV1Signing = signingConfig.enableV1Signing
            val effectiveMinSdk = targetApi ?: minSdk
            return when {
                // Sign with v1 if it's enabled explicitly.
                enableV1Signing != null -> enableV1Signing
                // We need v1 if minSdk < MIN_V2_SDK.
                effectiveMinSdk < MIN_V2_SDK -> true
                // We don't need v1 if minSdk >= MIN_V2_SDK and we're signing with v2.
                enableV2Signing(signingConfig, minSdk, targetApi) -> false
                // We need v1 if we're not signing with v2 and minSdk < MIN_V3_SDK.
                effectiveMinSdk < MIN_V3_SDK -> true
                // If minSdk >= MIN_V3_SDK, sign with v1 only if we're not signing with v3.
                else -> !enableV3Signing(signingConfig, targetApi)
            }
        }

        /**
         * This method returns whether to sign with v2 signature.
         *
         * @param signingConfig the DSL signingConfig object
         * @param minSdk the minimum SDK
         * @param targetApi optional injected target Api
         * @return if we actually sign with v2 signature
         */
        @VisibleForTesting
        fun enableV2Signing(
            signingConfig: SigningConfig,
            minSdk: Int,
            targetApi: Int?
        ): Boolean {
            val enableV2Signing = signingConfig.enableV2Signing
            val effectiveMinSdk = targetApi ?: minSdk
            return when {
                // Don't sign with v2 if we're installing on a device that doesn't support it.
                targetApi != null && targetApi < MIN_V2_SDK -> false
                // Otherwise, sign with v2 if it's enabled explicitly.
                enableV2Signing != null -> enableV2Signing
                // We sign with v2 if minSdk < MIN_V3_SDK, even if we're also signing with v1,
                // because v2 signatures can be verified faster than v1 signatures.
                effectiveMinSdk < MIN_V3_SDK -> true
                // If minSdk >= MIN_V3_SDK, sign with v2 only if we're not signing with v3.
                else -> !enableV3Signing(signingConfig, targetApi)
            }
        }

        /**
         * This method returns whether to sign with v3 signature.
         *
         * @param signingConfig the DSL signingConfig object
         * @param targetApi optional injected target Api
         * @return if we actually sign with v3 signature
         */
        @VisibleForTesting
        fun enableV3Signing(signingConfig: SigningConfig, targetApi: Int?): Boolean {
            return when {
                // Don't sign with v3 if we're installing on a device that doesn't support it.
                targetApi != null && targetApi < MIN_V3_SDK -> false
                // Otherwise, sign with v3 if it's enabled explicitly.
                else -> signingConfig.enableV3Signing ?: false
            }
        }

        /**
         * This method returns whether to sign with v4 signature.
         *
         * @param signingConfig the DSL signingConfig object
         * @param targetApi optional injected target Api
         * @return if we actually sign with v4 signature
         */
        private fun enableV4Signing(signingConfig: SigningConfig, targetApi: Int?): Boolean {
            return when {
                // Don't sign with v4 if we're installing on a device that doesn't support it.
                targetApi != null && targetApi < MIN_V4_SDK -> false
                // Otherwise, sign with v4 if it's enabled explicitly.
                else -> signingConfig.enableV4Signing ?: false
            }
        }
    }
}