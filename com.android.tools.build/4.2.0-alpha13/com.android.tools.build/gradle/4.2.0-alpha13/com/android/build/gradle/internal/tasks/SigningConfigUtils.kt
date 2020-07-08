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
import com.android.build.gradle.internal.signing.SigningConfigData
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

        private const val SIGNING_CONFIG_FILE_NAME = "signing-config.json"

        /** Returns the signing config file under the given directory. */
        fun getSigningConfigFile(directory: File) = File(directory, SIGNING_CONFIG_FILE_NAME)

        /** Saves the signing config information to a json file under the given output directory. */
        fun save(outputDirectory: File, signingConfig: SigningConfigData?) {
            val outputFile = getSigningConfigFile(outputDirectory)

            // create the file, so we can set the permissions on it.
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

            FileUtils.write(outputFile, getGson().toJson(signingConfig), StandardCharsets.UTF_8)
        }

        /** Loads the signing config information from a json file. */
        fun load(input: File): SigningConfigData? {
            return input.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                getGson().fromJson(reader, SigningConfigData::class.java)
            }
        }

        private fun getGson(): Gson {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
            return gsonBuilder.create()
        }
    }
}
