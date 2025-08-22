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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.signing.SigningConfigVersions
import com.android.build.gradle.options.SigningOptions
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission

/**
 * Utility class to save/load the signing config information to/from a json file.
 */
class SigningConfigUtils {

    companion object {

        /** Saves the [SigningConfigData] information to the outputFile.  */
        fun saveSigningConfigData(outputFile: File, signingConfigData: SigningConfigData?) {
            // create the file if it doesn't already exist, so we can set the permissions on it.
            outputFile.createNewFile()
            if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
                // set read, write permissions for owner only.
                val perms = HashSet<PosixFilePermission>()
                perms.add(PosixFilePermission.OWNER_READ)
                perms.add(PosixFilePermission.OWNER_WRITE)
                Files.setPosixFilePermissions(outputFile.toPath(), perms)
            } else {
                // on windows, use AclEntry to set the owner read/write permission.
                val view = Files.getFileAttributeView(
                    outputFile.toPath(), AclFileAttributeView::class.java
                )
                val entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(view.owner)
                    .setPermissions(
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.WRITE_ACL,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.WRITE_OWNER,
                        AclEntryPermission.SYNCHRONIZE,
                        AclEntryPermission.DELETE
                    )
                    .build()
                view.acl = listOf(entry)
            }

            FileUtils.write(outputFile, gson.toJson(signingConfigData), StandardCharsets.UTF_8)
        }

        /** Saves the [SigningConfigVersions] information to the outputFile.  */
        fun saveSigningConfigVersions(
            outputFile: File,
            signingConfigVersions: SigningConfigVersions
        ) {
            FileUtils.write(
                outputFile,
                gson.toJson(signingConfigVersions),
                StandardCharsets.UTF_8
            )
        }

        /** Loads the [SigningConfigData] information from a json file. */
        fun loadSigningConfigData(input: File): SigningConfigData? {
            return input.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                gson.fromJson(reader, SigningConfigData::class.java)
            }
        }

        /** Loads the [SigningConfigVersions] information from a json file. */
        @JvmStatic
        fun loadSigningConfigVersions(input: File): SigningConfigVersions {
            return input.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                gson.fromJson(reader, SigningConfigVersions::class.java)
            }
        }

        private val gson: Gson by lazy {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
            gsonBuilder.create()
        }

        fun createSigningOverride(dslServices: DslServices): SigningConfig? {
            SigningOptions.readSigningOptions(dslServices.projectOptions)?.let { signingOptions ->
                val signingConfigDsl = dslServices.newDecoratedInstance(SigningConfig::class.java, SigningOptions.SIGNING_CONFIG_NAME, dslServices)
                signingConfigDsl.storeFile(File(signingOptions.storeFile))
                signingConfigDsl.storePassword(signingOptions.storePassword)
                signingConfigDsl.keyAlias(signingOptions.keyAlias)
                signingConfigDsl.keyPassword(signingOptions.keyPassword)
                signingOptions.storeType?.let {
                    signingConfigDsl.storeType(it)
                }
                signingOptions.v1Enabled?.let {
                    signingConfigDsl.enableV1Signing = it
                }
                signingOptions.v2Enabled?.let {
                    signingConfigDsl.enableV2Signing = it
                }
                return signingConfigDsl
            }
            return null
        }
    }
}
