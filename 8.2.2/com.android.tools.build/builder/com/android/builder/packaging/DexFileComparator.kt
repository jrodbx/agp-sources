/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants
import java.io.File

/** Comparator that compares dex file paths, placing classes.dex always in front. */
class DexFileComparator : Comparator<File> {

    override fun compare(f1: File, f2: File): Int {
        return if (f1.name.endsWith(SdkConstants.FN_APK_CLASSES_DEX)) {
            if (f2.name.endsWith(SdkConstants.FN_APK_CLASSES_DEX)) {
                f1.absolutePath.compareTo(f2.absolutePath)
            } else {
                -1
            }
        } else {
            if (f2.name.endsWith(SdkConstants.FN_APK_CLASSES_DEX)) {
                1
            } else {
               return f1
                    .absolutePath
                    .compareTo(f2.absolutePath)
            }
        }
    }
}
