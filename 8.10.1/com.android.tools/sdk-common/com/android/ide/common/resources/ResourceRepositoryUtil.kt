/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("ResourceRepositoryUtil")

package com.android.ide.common.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.resources.ResourceType
import com.google.common.collect.Table
import com.google.common.collect.Tables
import java.util.EnumMap
import java.util.HashMap
import java.util.SortedSet
import java.util.TreeSet

/**
 * Returns the resources values matching a given [FolderConfiguration].
 *
 * @param referenceConfig the configuration that each value must match
 * @return a [Table] with one row for every namespace present in this repository, where
 *         every row contains an entry for all resource types
 */
fun ResourceRepository.getConfiguredResources(
    referenceConfig: FolderConfiguration
): Table<ResourceNamespace, ResourceType, ResourceValueMap> {
    val backingMap: Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> =
        if (KnownNamespacesMap.canContainAll(namespaces)) { KnownNamespacesMap() } else { HashMap() }
    val result = Tables.newCustomTable(backingMap) { EnumMap(ResourceType::class.java) }

    for (namespace in namespaces) {
        // TODO(namespaces): Move this method to ResourceResolverCache.
        // For performance reasons don't mix framework and non-framework resources since
        // they have different life spans.
        assert(namespaces.size == 1 || namespace !== ResourceNamespace.ANDROID)

        for (type in ResourceType.values()) {
            // get the local results and put them in the map
            result.put(
                namespace,
                type,
                getConfiguredResources(namespace, type, referenceConfig)
            )
        }
    }
    return result
}

/**
 * Returns a map of (resource name, resource value) for the given [ResourceType].
 *
 * The values returned are taken from the resource files best matching a given [FolderConfiguration].
 *
 * @param namespace namespaces of the resources
 * @param type the type of the resources.
 * @param referenceConfig the configuration to best match
 */
fun ResourceRepository.getConfiguredResources(
    namespace: ResourceNamespace,
    type: ResourceType,
    referenceConfig: FolderConfiguration
): ResourceValueMap {
    val itemsByName = getResources(namespace, type).asMap()
    val result = ResourceValueMap.createWithExpectedSize(itemsByName.size)

    for (itemGroup in itemsByName.values) {
        // Look for the best match for the given configuration.
        val match = referenceConfig.findMatchingConfigurable<ResourceItem>(itemGroup)
        if (match != null) {
            val value = match.resourceValue
            if (value != null) {
                result[match.name] = value
            }
        }
    }

    return result
}

// TODO: namespaces
fun ResourceRepository.getConfiguredValue(
    type: ResourceType,
    name: String,
    referenceConfig: FolderConfiguration
): ResourceValue? {
    val items = getResources(ResourceNamespace.TODO(), type, name)
    // Look for the best match for the given configuration.
    // The match has to be of type ResourceFile since that's what the input list contains.
    val match = referenceConfig.findMatchingConfigurable<ResourceItem>(items)
    return match?.resourceValue
}

/** Returns the sorted list of locales used in the resources. */
fun ResourceRepository.getLocales(): SortedSet<LocaleQualifier> {
    // As an optimization we could just look for values since that's typically where
    // the languages are defined -- not on layouts, menus, etc -- especially if there
    // are no translations for it.
    val locales = TreeSet<LocaleQualifier>()

    for (repository in leafResourceRepositories) {
        repository.accept {
            val locale = it.configuration.localeQualifier
            if (locale != null) {
                locales.add(locale)
            }
            ResourceVisitor.VisitResult.CONTINUE
        }
    }

    return locales
}
