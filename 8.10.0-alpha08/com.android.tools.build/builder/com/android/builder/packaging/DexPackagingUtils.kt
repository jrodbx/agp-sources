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

package com.android.builder.packaging

import com.android.SdkConstants.FN_APK_CLASSES_DEX
import com.android.builder.files.RelativeFile
import java.io.File

/** Comparator that compares dex file paths, placing classes.dex always in front. */
object DexFileComparator : Comparator<File> {

    override fun compare(file1: File, file2: File): Int {
        return when {
            file1.name == FN_APK_CLASSES_DEX && file2.name != FN_APK_CLASSES_DEX -> -1
            file1.name != FN_APK_CLASSES_DEX && file2.name == FN_APK_CLASSES_DEX -> 1
            else -> file1.absolutePath.compareTo(file2.absolutePath)
        }
    }
}

/** Comparator that compares dex file paths, placing classes.dex always in front. */
object DexRelativeFileComparator : Comparator<RelativeFile> {

    override fun compare(file1: RelativeFile, file2: RelativeFile): Int {
        return DexFileComparator.compare(file1.getFile(), file2.getFile());
    }
}
