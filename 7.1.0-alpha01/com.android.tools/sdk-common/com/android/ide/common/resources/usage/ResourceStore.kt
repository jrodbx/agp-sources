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

package com.android.ide.common.resources.usage

import com.android.ide.common.resources.resourceNameToFieldName
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.utils.SdkUtils
import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.util.Collections
import java.util.regex.PatternSyntaxException

/**
 * Stores information about all application resources. Supports two modes:
 *
 * <ul>
 *     <li>Resources from a single package. In this mode packageName inside resource objects is
 *         always null. Each resource in this mode is identified by type and name.
 *     <li>Resources from multiple packages. In this mode resource is identified by triple:
 *         packageName, type and name. This mode is used for Android applications with dynamic
 *         feature modules where each module defines their resources inside its own package and
 *         the same resource name and type may be used by different resources from different
 *         modules.
 * </ul>
 */
class ResourceStore(val supportMultipackages: Boolean = false) {

    /** All known resources by id. */
    private val resourceById: MutableMap<ResourceId, Resource> =
        Maps.newLinkedHashMapWithExpectedSize(TYPICAL_RESOURCE_COUNT)

    /** All resources. */
    val resources: List<Resource>
        get() = Collections.unmodifiableList(_resources)
    private val _resources: MutableList<Resource> =
        Lists.newArrayListWithCapacity<Resource>(TYPICAL_RESOURCE_COUNT)

    /** Resources partitioned by type and name.  */
    private val typeToName: MutableMap<ResourceType, ListMultimap<String, Resource>> =
        Maps.newEnumMap(ResourceType::class.java)

    /** Map from resource ID value (R field value) to corresponding resource.  */
    private val valueToResource: MutableMap<Int, Resource> =
        Maps.newHashMapWithExpectedSize(TYPICAL_RESOURCE_COUNT)

    /** Set of resource names that are explicitly marked as kept */
    private val keepResources: MutableSet<ResourceId> = Sets.newHashSet()

    /**
     * Recorded list of keep attributes: these can contain wildcards, so they can't be applied
     * immediately; we have to apply them after scanning through all resources.
     */
    val keepAttributes: List<String>
        get() = Collections.unmodifiableList(_keepAttributes)
    private val _keepAttributes: MutableList<String> = Lists.newArrayList()

    /**
     * Recorded list of discard attributes: these can contain wildcards, so they can't be applied
     * immediately; we have to apply them after scanning through all resources.
     */
    val discardAttributes: List<String>
        get() = Collections.unmodifiableList(_discardAttributes)
    private val _discardAttributes: MutableList<String> = Lists.newArrayList()

    /**
     * Whether we should attempt to guess resources that should be kept based on looking at the
     * string pool and assuming some of the strings can be used to dynamically construct the
     * resource names. Can be turned off via `tools:shrinkMode="strict"`.
     */
    var safeMode = true

    /** Returns resource by its ID value (R field value).  */
    fun getResource(value: Int): Resource? {
        return valueToResource[value]
    }

    /**
     * Returns resource by triple: packageName, type and name. In a single package mode packageName
     * must be null.
     */
    fun getResource(packageName: String?, type: ResourceType, name: String): Resource? {
        Preconditions.checkArgument(
            supportMultipackages || packageName == null,
            "In a single package mode packageName must be null."
        )
        return resourceById[ResourceId(type, name, packageName)]
    }

    /**
     * Returns resources by type and name. In single package mode may return empty collection or
     * collection with only one element. In multiple package mode returns resources that have
     * specified type and name from all packages.
     */
    fun getResources(type: ResourceType, name: String): List<Resource> {
        val resourcesByName = typeToName[type] ?: return emptyList()
        return resourcesByName[resourceNameToFieldName(name)].toList()
    }

    /**
     * Returns resources which are referenced by specified resource url. In single package mode may
     * return empty collection or collection with only one element. In multiple package mode returns
     * resources that are referenced by specified url from all packages.
     */
    fun getResourcesFromUrl(possibleUrlReference: String): List<Resource> {
        val url = ResourceUrl.parse(possibleUrlReference)
        if (url == null || url.isFramework) {
            return emptyList()
        }
        return getResources(url.type, url.name)
    }

    /**
     * Returns all resources that are referenced by provided {@param webUrl} (for example:
     * file:///android_res/drawable/bar.png). Looks for:
     *
     * <ul>
     *   <li>a full resource URL: /android_res/type/name.ext;
     *   <li>a partial URL that identifies resources: type/name.ext.
     * </ul>
     */
    fun getResourcesFromWebUrl(webUrl: String): List<Resource> {
        val afterAndroidRes = webUrl.substringAfter("android_res/")
        val parts = afterAndroidRes.split("/", limit = 2)
        if (parts.size < 2) {
            return emptyList()
        }
        val (type, namePart) = parts
        val folderType = ResourceFolderType.getFolderType(type) ?: return emptyList()
        val name = namePart.substringBefore('.')

        return FolderTypeRelationship.getRelatedResourceTypes(folderType)
            .flatMap { getResources(it, name) }
    }

    /**
     * Adds resource to store. In a single package mode, packageName is cleared inside provided
     * {@param resource}. If resource was already added to store but without its resource ID value
     * (value = -1) this method updates resource ID value.
     *
     * @return added or existing resource.
     */
    fun addResource(resource: Resource): Resource {
        if (!supportMultipackages) {
            resource.packageName = null
        }
        val id = ResourceId(resource.type, resource.name, resource.packageName)
        val stored = resourceById[id]

        val updated = if (stored == null) {
            resourceById += id to resource
            _resources += resource
            typeToName.computeIfAbsent(resource.type) { ArrayListMultimap.create() }
                .put(resource.name, resource)
            if (resource.value != -1) {
                valueToResource += resource.value to resource
            }
            resource
        } else {
            Preconditions.checkState(
                resource.value == -1 || stored.value == resource.value,
                "Resource value must be the same between addResource calls."
            )
            if (resource.value != -1 && stored.value == -1) {
                stored.value = resource.value
                valueToResource += stored.value to stored
            }
            stored
        }

        return updated
    }

    /**
     * Returns all resources as collection of maps where each map contains resources of the same
     * type partitioned by name.
     */
    fun getResourceMaps(): Collection<ListMultimap<String, Resource>> {
        return typeToName.values
    }

    /**
     * Records resources to keep explicitly. Input is a value of 'tools:keep' attribute in a
     * &lt;resources&gt; tag.
     */
    fun recordKeepToolAttribute(value: String) {
        // We need to split value here because 'tools:keep' attribute value is comma-separated list
        // and may contain multiple keep rules.
        Splitter.on(',')
            .omitEmptyStrings()
            .trimResults()
            .split(value)
            .forEach { _keepAttributes += it }
    }

    /**
     * Records resources to discard explicitly. Input is a value of 'tools:discard' attribute in a
     * &lt;resources&gt; tag.
     */
    fun recordDiscardToolAttribute(value: String) {
        // We need to split value here because 'tools:discard' attribute value is comma-separated
        // list and may contain multiple discard rules.
        Splitter.on(',')
            .omitEmptyStrings()
            .trimResults()
            .split(value)
            .forEach { _discardAttributes += it }
    }

    /**
     * Processes all keep and discard rules which were added previously by
     * [recordKeepToolAttribute] and [recordDiscardToolAttribute] and marks all referenced
     * resources as reachable/not reachable respectively.
     *
     * <p>If the same resource is referenced by some keep and discard rule then discard takes
     * precedence.
     */
    fun processToolsAttributes() {
        _keepAttributes.asSequence()
            .flatMap { getResourcesForKeepOrDiscardPattern(it) }
            .forEach {
                it.isReachable = true
                keepResources += ResourceId(it.type, it.name, it.packageName)
            }
        _discardAttributes.asSequence()
            .flatMap { getResourcesForKeepOrDiscardPattern(it) }
            .forEach { it.isReachable = false }
    }

    fun dumpConfig(): String =
        _resources.asSequence()
            .sortedWith(compareBy({ it.type }, { it.name }))
            .map { r ->
                val id = ResourceId(r.type, r.name, r.packageName)
                val actions = listOfNotNull(
                    "remove".takeUnless { r.isReachable },
                    "no_obfuscate".takeIf { keepResources.contains(id) }
                ).joinToString(",")
                "${r.type}/${r.name}#$actions"
            }
            .joinToString("\n", "", "\n")

    fun dumpKeepResources(): String =
        keepResources
            .map { it.name }
            .sorted()
            .joinToString(",")

    fun dumpReferences(): String =
        _resources.asSequence()
            .filter { it.references != null }
            .map { "$it => ${it.references}" }
            .sorted()
            .joinToString("\n", "Resource Reference Graph:\n", "")

    fun dumpResourceModel(): String =
        _resources.asSequence()
            .sortedWith(compareBy({ it.type }, { it.name }))
            .flatMap { r ->
                val references = r.references?.asSequence() ?: emptySequence()
                sequenceOf("${r.url} : reachable=${r.isReachable}") +
                    references.map { "    ${it.url}" }
            }
            .joinToString("\n", "", "\n")

    private fun getResourcesForKeepOrDiscardPattern(pattern: String): Sequence<Resource> {
        val url = ResourceUrl.parse(pattern)
        if (url == null || url.isFramework) {
            return emptySequence()
        }
        val resources = typeToName[url.type] ?: return emptySequence()
        if (!url.name.contains("*") && !url.name.contains("?")) {
            return resources[url.name].asSequence()
        }
        return try {
            val regexp = SdkUtils.globToRegexp(resourceNameToFieldName(url.name)).toRegex()
            resources.entries().asSequence()
                .filter { regexp.matches(it.key) }
                .map { it.value }
        } catch (e: PatternSyntaxException) {
            emptySequence()
        }
    }

    /** Merges the other [ResourceStore] into this one */
    fun merge(other: ResourceStore) {
        // Populate resource store
        other._discardAttributes.forEach {
            if (!_discardAttributes.contains(it)) _discardAttributes.add(it)
        }
        other._keepAttributes.forEach {
            if (!_keepAttributes.contains(it)) _keepAttributes.add(it)
        }
        // First create all the resources to make sure they exist, and merge flags.
        // We'll recreate the references next so that they point to items in the new graph
        other._resources.forEach { r ->
            val existing = getResource(r.packageName, r.type, r.name)
                ?: addResource(Resource(r.packageName, r.type, r.name, r.value))
            existing.mFlags = existing.mFlags or r.mFlags
            r.declarations?.forEach { location ->
                val locations = existing.declarations
                if (locations == null || !locations.contains(location)) {
                    existing.addLocation(location)
                }
            }
        }
        other._resources.forEach { r ->
            val existing = getResource(r.packageName, r.type, r.name)
            if (existing != null) {
                r.references?.forEach { referenced ->
                    val ref = getResource(referenced.packageName, referenced.type, referenced.name)
                    if (ref != null) {
                        existing.addReference(ref)
                    }
                }
            }
        }
    }

    companion object {
        private const val TYPICAL_RESOURCE_COUNT = 200

        /**
         * Serialize the given store into a compact string; this will allow the
         * [ResourceStore] to be restored via a call to [deserialize]. This method
         * tries to generate compact output, generate semi-readable output and
         * be low overhead.
         */
        fun serialize(store: ResourceStore, includeValues: Boolean = true): String {
            val sb = StringBuilder(2000)
            serializeInto(sb, store, includeValues)
            return sb.toString()
        }

        fun serializeInto(
            sb: StringBuilder,
            store: ResourceStore,
            includeValues: Boolean = true
        ) {
            // Format
            // All numbers are stored as hex.
            // Sections divided by ";" (we're trying to avoid characters that have to be
            // escaped in XML such as newlines).
            if (store.supportMultipackages) {
                sb.append("P;")
            }

            // Assign id's to each resource
            val storeTypeToName = store.typeToName
            val referenceMap: BiMap<Resource, Int> = HashBiMap.create(storeTypeToName.size)
            var nextId = 0
            // Make sure we're sorting by type
            for (type in storeTypeToName.keys) {
                val list = storeTypeToName[type] ?: continue
                for (resource in list.values()) {
                    referenceMap[resource] = nextId++
                }
            }

            if (referenceMap.isEmpty()) {
                return
            }

            fun Resource.id(): String = Integer.toHexString(referenceMap[this]!!)

            var prev: ResourceType? = null
            var first = true
            for (resource in referenceMap.keys) {
                val type = resource.type
                if (type != prev) {
                    prev = type
                    if (!first) {
                        sb.append(']')
                        sb.append(',')
                        first = true
                    }
                    sb.append(type.getName())
                    // Resource names. These names cannot contain the characters , ( ) ; - which is
                    // why we use them to separate tokens.
                    sb.append('[')
                }
                if (first) {
                    first = false
                } else {
                    sb.append(',')
                }
                sb.append(resource.name)
                sb.append('(')
                sb.append(resource.flagString())
                val includeValue = includeValues && resource.value != -1
                if (store.supportMultipackages) {
                    if (resource.packageName != null) {
                        sb.append(',')
                        sb.append(resource.packageName)
                    } else if (includeValue) {
                        sb.append(',')
                    }
                }
                if (includeValue) {
                    sb.append(',')
                    sb.append(Integer.toHexString(resource.value))
                }
                sb.append(')')
            }
            sb.append(']')
            sb.append(';')

            // Store reference graph

            first = true
            for (resource in referenceMap.keys) {
                val references = resource.references
                if (references != null && references.isNotEmpty()) {
                    if (first) {
                        first = false
                    } else {
                        sb.append(',')
                    }
                    sb.append(resource.id())
                    for (reference in references) {
                        sb.append('^')
                        sb.append(reference.id())
                    }
                }
            }
            sb.append(';')

            with(store._keepAttributes) {
                if (isNotEmpty()) {
                    joinTo(sb, separator = ",")
                }
            }

            sb.append(';')

            with(store._discardAttributes) {
                if (isNotEmpty()) {
                    joinTo(sb, separator = ",")
                }
            }

            sb.append(';')
        }

        /** Reverses the [serialize] process */
        fun deserialize(s: String): ResourceStore {
            if (s.isEmpty()) {
                return ResourceStore()
            }
            var offset = 0
            var supportMultiPackages = false
            if (s.startsWith("P;")) {
                supportMultiPackages = true
                offset += 2
            }

            val resourceStore = ResourceStore(supportMultiPackages)
            val referenceMap: BiMap<Resource, Int> = HashBiMap.create(s.length / 10)

            var nextId = 0
            val length = s.length
            // initial value doesn't matter but want non-nullable type
            var type: ResourceType = ResourceType.SAMPLE_DATA
            val delimiters = charArrayOf(',', ')', '^', ';')
            while (offset < length) {
                // Find the next type start
                // attr[myAttr1(D,7f010000),myAttr2(D,7f010001)],...
                // ~~~~~
                val next = s[offset]
                if (next == ';') {
                    // Done with resource name table
                    break
                }
                val typeEnd = s.indexOf('[', offset)
                assert(typeEnd != -1)
                val typeString = s.substring(offset, typeEnd)
                val fromClassName = ResourceType.fromClassName(typeString)
                    // Must be a synthetic resource like PUBLIC, which are
                    // excluded from the reverse name lookup. Fortunately, this
                    // is not common.
                    ?: ResourceType.values().first { it.getName() == typeString }
                type = fromClassName
                offset = typeEnd + 1

                // Read in resources
                // attr[myAttr1(D,7f010000),myAttr2(D,7f010001)],dimen[activity_vertical_margin(...
                //      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                while (offset < length) {
                    //noinspection ExpensiveAssertion
                    assert(s[offset] != ']') // no empty lists should have been written
                    val nameEnd = s.indexOf('(', offset)
                    assert(nameEnd != -1)
                    val name = s.substring(offset, nameEnd)
                    offset = nameEnd + 1
                    var end = s.indexOfAny(delimiters, offset)
                    val flagString = s.substring(offset, end)
                    val flags = Resource.stringToFlag(flagString)

                    var pkg: String? = null
                    if (supportMultiPackages && s[end] == ',') {
                        // Also specifying package
                        offset = end + 1
                        val packageEnd = s.indexOfAny(delimiters, offset)
                        if (packageEnd > offset) {
                            pkg = s.substring(offset, packageEnd)
                        }
                        end = packageEnd
                    }

                    var value: Int
                    if (s[end] == ',') {
                        // Also specifying value
                        offset = end + 1
                        val valueEnd = s.indexOf(')', offset)
                        val valueString = s.substring(offset, valueEnd)
                        value = Integer.parseUnsignedInt(valueString, 16)
                        offset = valueEnd + 1
                    } else {
                        value = -1
                        offset = end + 1
                    }

                    val resource = Resource(pkg, type, name, value)
                    resource.mFlags = flags
                    referenceMap[resource] = nextId++

                    if (s[offset++] != ',') {
                        if (s[offset] == ',') {
                            offset++
                        }
                        break
                    }
                }
            }

            // Read in the resource graph
            if (offset < length - 1) {
                val inverse = referenceMap.inverse()

                offset++
                while (offset < length) {
                    if (s[offset] == ';') {
                        offset++
                        break
                    }

                    val end = s.indexOfAny(delimiters, offset)
                    val idString = s.substring(offset, end)
                    val id = Integer.parseInt(idString, 16)
                    val resource = inverse[id]!!

                    offset = end + 1
                    while (offset < length) {
                        val refEnd = s.indexOfAny(delimiters, offset)
                        val refIdString = s.substring(offset, refEnd)
                        val ref = Integer.parseInt(refIdString, 16)
                        val reference = inverse[ref]!!
                        resource.addReference(reference)
                        offset = refEnd + 1
                        if (s[offset - 1] == ',' || s[offset - 1] == ';') {
                            break
                        }
                    }
                    if (s[offset - 1] == ';') {
                        break
                    }
                }
            }

            // Keep attributes
            val keepEnd = s.indexOf(';', offset)
            if (keepEnd > offset) {
                val keep = s.substring(offset, keepEnd)
                resourceStore.recordKeepToolAttribute(keep)
            }
            offset = keepEnd + 1

            val discardEnd = s.indexOf(';', offset)
            if (discardEnd > offset) {
                val discard = s.substring(offset, discardEnd)
                resourceStore.recordDiscardToolAttribute(discard)
            }

            // Populate resource store
            referenceMap.forEach { (resource, _) ->
                resourceStore.addResource(resource)
            }

            // Mark keep resources
            resourceStore.processToolsAttributes()

            return resourceStore
        }
    }
}

private data class ResourceId(
    val type: ResourceType,
    val name: String,
    val packageName: String?
)
