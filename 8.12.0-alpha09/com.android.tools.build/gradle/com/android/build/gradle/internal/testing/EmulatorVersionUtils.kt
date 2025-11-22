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

package com.android.build.gradle.internal.testing

import com.android.ide.common.gradle.Version
import com.android.repository.api.NullProgressIndicator
import com.android.repository.api.Repository
import com.android.repository.impl.meta.SchemaModuleUtil
import com.android.sdklib.repository.AndroidSdkHandler
import java.io.File

private val SNAPSHOT_LOADABLE_VERSION = Version.parse("30.6.4")
private val FORCE_SNAPSHOT_LOAD_VERSION = Version.parse("34.2.14")

data class EmulatorVersionMetadata(val canUseForceSnapshotLoad: Boolean)

fun getEmulatorMetadata(emulatorDir: File): EmulatorVersionMetadata {
    val packageFile = emulatorDir.resolve("package.xml")

    val repository = runCatching {
        SchemaModuleUtil.unmarshal(
            packageFile.inputStream(),
            AndroidSdkHandler.getAllModules(),
            false,
            NullProgressIndicator,
            packageFile.toString(),
        ) as? Repository
    }

    val version =
        repository.getOrNull()?.localPackage?.version?.let { Version.parse("${it.major}.${it.minor}.${it.micro}") }
            ?: throw IllegalStateException(
                "Could not determine version of Emulator in ${emulatorDir.absolutePath}. Update " +
                        "emulator in order to use Managed Devices.", repository.exceptionOrNull()
            )
    if (version < SNAPSHOT_LOADABLE_VERSION) {
        throw IllegalStateException(
            "Emulator needs to be updated in order to use managed devices. Minimum " +
                    "version required: $SNAPSHOT_LOADABLE_VERSION. Version found: $version"
        )
    }

    return EmulatorVersionMetadata(canUseForceSnapshotLoad = version >= FORCE_SNAPSHOT_LOAD_VERSION)
}
