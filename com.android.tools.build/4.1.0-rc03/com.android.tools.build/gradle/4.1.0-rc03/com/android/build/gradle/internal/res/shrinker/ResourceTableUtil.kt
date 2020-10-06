/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.res.shrinker

import com.android.aapt.Resources

internal fun Resources.ResourceTable.entriesSequence(): Sequence<EntryWrapper> = sequence {
    for (resourcePackage in packageList) {
        for (resourceType in resourcePackage.typeList) {
            for (resourceEntry in resourceType.entryList) {
                val id = toIdentifier(resourcePackage, resourceType, resourceEntry)
                yield(
                    EntryWrapper(id, resourcePackage.packageName, resourceType.name, resourceEntry)
                )
            }
        }
    }
}

internal data class EntryWrapper(
    val id: Int,
    val packageName: String,
    val type: String,
    val entry: Resources.Entry
)

private fun toIdentifier(
    resourcePackage: Resources.Package,
    type: Resources.Type,
    entry: Resources.Entry
): Int =
    (resourcePackage.packageId.id shl 24) or (type.typeId.id shl 16) or entry.entryId.id
