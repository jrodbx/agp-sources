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

package com.android.build.shrinker

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

internal fun Resources.ResourceTable.nullOutEntriesWithIds(ids: List<Int>)
    : Resources.ResourceTable {
    if (ids.isEmpty()) {
        return this
    }
    val packageMappings = calculatePackageMappings(ids)
    val tableBuilder = this.toBuilder()
    tableBuilder.packageBuilderList.forEach{
        val typeMappings = packageMappings[it.packageId.id]
        if (typeMappings != null) {
            it.typeBuilderList.forEach { type->
                val entryList = typeMappings[type.typeId.id]
                if (entryList != null) {
                    type.entryBuilderList.forEach { entry ->
                        if (entryList.contains(entry.entryId.id)) {
                            entry.clearConfigValue()
                            if (entry.hasOverlayableItem()) {
                                entry.clearOverlayableItem()
                            }
                        }
                    }
                }
            }
        }
    }
    return tableBuilder.build()
}

private fun calculatePackageMappings(ids: List<Int>): MutableMap<Int, Map<Int, List<Int>>> {
    val sortedIds = ids.sorted()
    val packageMapping = mutableMapOf<Int, Map<Int, List<Int>>>()
    var typeMapping = mutableMapOf<Int, List<Int>>()
    var entryList = mutableListOf<Int>()
    var oldPackageId = -1
    var oldTypeId = -1
    for (value in sortedIds) {
        val packageId = packageIdFromIdentifier(value)
        val typeId = typeIdFromIdentifier(value)
        val entryId = entryIdFromIdentifier(value)
        if (packageId != oldPackageId) {
            typeMapping = mutableMapOf()
            packageMapping.put(packageId, typeMapping)
            oldPackageId = packageId
            oldTypeId = -1
        }
        if (typeId != oldTypeId) {
            entryList = mutableListOf()
            typeMapping.put(typeId, entryList)
            oldTypeId = typeId
        }
        entryList.add(entryId)
    }
    return packageMapping
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

private fun packageIdFromIdentifier(
    identifier: Int
): Int =
    identifier shr 24

private fun typeIdFromIdentifier(
    identifier: Int
): Int =
    (identifier and 0x00FF0000) shr 16

private fun entryIdFromIdentifier(
    identifier: Int
): Int =
    (identifier and 0x0000FFFF)


