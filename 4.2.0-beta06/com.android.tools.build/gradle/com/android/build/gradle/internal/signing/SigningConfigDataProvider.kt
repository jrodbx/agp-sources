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

import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.SigningConfigUtils
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.io.Serializable

/**
 * Encapsulates different ways to get the signing config information. It may be `null`.
 *
 * This class is designed to be used by tasks that are interested in the actual signing config
 * information, not the ways to get that information (i.e., *how* to get the info is internal to
 * this class).
 *
 * Those tasks should then annotate this object with `@Nested`, so that if the signing config
 * information has changed, the tasks will be re-executed with the updated info.
 */
class SigningConfigDataProvider(

    /** When not `null`, the signing config information can be obtained directly in memory. */
    @get:Nested
    @get:Optional
    val signingConfigData: SigningConfigData?,

    /** When not `null`, the signing config information can be obtained from a file. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val signingConfigFileCollection: FileCollection?,

    /**
     * The result of validating the signing config information. It may be `null` if the validation
     * is already taken care of elsewhere (e.g., by a different module/task).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    val signingConfigValidationResultDir: Provider<Directory>?
) {

    /** Resolves this provider to get the signing config information. It may be `null`. */
    fun resolve(): SigningConfigData? {
        return convertToParams().resolve()
    }

    /** Converts this provider to [SigningConfigProviderParams] to be used by Gradle workers. */
    fun convertToParams(): SigningConfigProviderParams {
        return SigningConfigProviderParams(
            signingConfigData,
            signingConfigFileCollection?.let { it.singleFile }
        )
    }

    companion object {

        @JvmStatic
        fun create(creationConfig: ComponentCreationConfig): SigningConfigDataProvider {
            val isInDynamicFeature =
                creationConfig.variantType.isDynamicFeature
                        || (creationConfig is TestComponentImpl
                                && creationConfig.testedConfig.variantType.isDynamicFeature)

            // We want to avoid writing the signing config information to disk to protect sensitive
            // data (see bug 137210434), so we'll attempt to get this information directly from
            // memory first.
            return if (!isInDynamicFeature) {
                // Get it from the variant scope
                SigningConfigDataProvider(
                    signingConfigData = creationConfig.variantDslInfo.signingConfig?.let {
                        SigningConfigData.fromSigningConfig(it)
                    },
                    signingConfigFileCollection = null,
                    signingConfigValidationResultDir = creationConfig.artifacts.get(
                        InternalArtifactType.VALIDATE_SIGNING_CONFIG
                    )
                )
            } else {
                // Get it from the injected properties passed from the IDE
                val signingConfigData =
                    SigningConfigData.fromProjectOptions(creationConfig.services.projectOptions)
                return if (signingConfigData != null) {
                    SigningConfigDataProvider(
                        signingConfigData = signingConfigData,
                        signingConfigFileCollection = null,
                        // Validation for this case is currently missing because the base module
                        // doesn't publish its validation result so that we can use it here.
                        // However, normally the users would build both the base module and the
                        // dynamic feature module, therefore the signing config info for both
                        // modules would be validated when the base module is built, so it may be
                        // acceptable to not validate it here.
                        signingConfigValidationResultDir = null
                    )
                } else {
                    // Otherwise, get it from the published artifact
                    SigningConfigDataProvider(
                        signingConfigData = null,
                        signingConfigFileCollection =
                            creationConfig.variantDependencies.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.PROJECT,
                                AndroidArtifacts.ArtifactType.FEATURE_SIGNING_CONFIG_DATA
                            ),
                        // Validation is taken care of by the task in the base module that publishes
                        // the signing config info (SigningConfigWriterTask).
                        signingConfigValidationResultDir = null
                    )
                }
            }
        }
    }
}

/**
 * Similar to [SigningConfigDataProvider], but uses a [File] instead of a [FileCollection] to be
 * used by Gradle workers.
 */
class SigningConfigProviderParams(
    private val signingConfigData: SigningConfigData?,
    private val signingConfigFile: File?
) : Serializable {

    /** Resolves this provider to get the signing config information. It may be `null`. */
    fun resolve(): SigningConfigData? {
        return signingConfigData
                ?: signingConfigFile?.let { SigningConfigUtils.loadSigningConfigData(it) }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
