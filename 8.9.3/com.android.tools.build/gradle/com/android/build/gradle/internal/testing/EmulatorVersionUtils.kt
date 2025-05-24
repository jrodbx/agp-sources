/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.testing;


import com.android.ide.common.gradle.Version
import java.io.File
import java.nio.file.Files.readAllLines
import java.util.regex.Pattern

private val SNAPSHOT_LOADABLE_VERSION = Version.parse("30.6.4")
private val FORCE_SNAPSHOT_LOAD_VERSION = Version.parse("34.2.14")
private val versionPattern =
    Pattern.compile("<major>(\\d+)</major><minor>(\\d+)</minor><micro>(\\d+)</micro>")

data class EmulatorVersionMetadata (
    val canUseForceSnapshotLoad: Boolean
)

fun getEmulatorMetadata(emulatorDir: File): EmulatorVersionMetadata {
    var foundMajorVersion = -1
    var foundMinorVersion = -1
    var foundMicroVersion = -1

    val packageFile = emulatorDir.resolve("package.xml")

    for (line in readAllLines(packageFile.toPath())) {
        val matcher = versionPattern.matcher(line)
        if (matcher.find()) {
            foundMajorVersion = matcher.group(1).toInt()
            foundMinorVersion = matcher.group(2).toInt()
            foundMicroVersion = matcher.group(3).toInt()
            break
        }
    }
    if (foundMajorVersion == -1) {
        error(
            "Could not determine version of Emulator in ${emulatorDir.absolutePath}. Update " +
                    "emulator in order to use Managed Devices."
        )
    }
    val version = Version.parse("$foundMajorVersion.$foundMinorVersion.$foundMicroVersion")

    if (version < SNAPSHOT_LOADABLE_VERSION) {
        error(
            "Emulator needs to be updated in order to use managed devices. Minimum " +
                    "version required: $SNAPSHOT_LOADABLE_VERSION. Version found: $version"
        )
    }

    return EmulatorVersionMetadata(
        canUseForceSnapshotLoad = version >= FORCE_SNAPSHOT_LOAD_VERSION
    )
}
