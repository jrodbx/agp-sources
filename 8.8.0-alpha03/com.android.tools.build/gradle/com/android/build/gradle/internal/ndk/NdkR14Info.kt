/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.ndk

import com.android.build.gradle.internal.core.Abi
import java.io.File

/**
 * NdkInfo for r14.
 */
open class NdkR14Info(val root: File) : DefaultNdkInfo(root) {

    override fun getToolchainAbi(abi: String): String {
        return if (abi == Abi.MIPS.tag) {
            Abi.MIPS64.tag
        } else abi
    }

    override fun validate(): String? {
        val error = super.validate()
        if (error != null) {
            return error
        }

        val sysrootDir = rootDirectory.resolve("sysroot")
        if (!sysrootDir.isDirectory) {
            return "$sysrootDir is not a directory."
        }

        return null
    }
}
