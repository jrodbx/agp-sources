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

package com.android.build.gradle.internal.res.shrinker.graph

import com.android.aapt.Resources
import com.android.aapt.Resources.Entry
import com.android.aapt.Resources.FileReference
import com.android.aapt.Resources.FileReference.Type.PROTO_XML
import com.android.aapt.Resources.Reference
import com.android.aapt.Resources.ResourceTable
import com.android.aapt.Resources.XmlAttribute
import com.android.aapt.Resources.XmlElement
import com.android.aapt.Resources.XmlNode
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.build.gradle.internal.res.shrinker.entriesSequence
import com.android.ide.common.resources.resourceNameToFieldName
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.ResourceType
import com.android.utils.SdkUtils.IMAGE_EXTENSIONS
import com.google.common.base.Ascii
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds resources graph starting from each resource in resource table in proto format and follow
 * all references to other resources inside inlined values and external files from res/ folder.
 *
 * <p>Supports external files in the following formats:
 * <ul>
 *     <li>XML files compiled to proto format;
 *     <li>HTML, CSS, JS files inside res/raw folder;
 *     <li>Unknown files inside res/raw folder (only looks for 'android_res/<type>/<name>' pattern);
 * </ul>
 *
 * <p>As ID resources don't specify parent-child relations between resources but are just
 * identifiers for a resource or some part of the resource we don't gather them as references to
 * examined resource.
 *
 * @param resourceRoot path to <module>/res/ folder.
 * @param resourceTable path to resource table in proto format.
 */
class ProtoResourcesGraphBuilder(
    private val resourceRoot: Path,
    private val resourceTable: Path
) : ResourcesGraphBuilder {

    override fun buildGraph(model: ResourceShrinkerModel) {
        model.readResourceTable(resourceTable).entriesSequence()
            .map { (id, _, _, entry) ->
                model.getResourceByValue(id)?.let {
                    ReferencesForResourceFinder(resourceRoot, model, entry, it)
                }
            }
            .filterNotNull()
            .forEach { it.findReferences() }
    }
}

private class ReferencesForResourceFinder(
    private val resourcesRoot: Path,
    private val model: ResourceShrinkerModel,
    private val entry: Entry,
    private val current: Resource
) {
    companion object {
        private fun Reference.asItem(): Resources.Item =
            Resources.Item.newBuilder().setRef(this).build()
    }

    fun findReferences() {
        // Walk through all values of the entry and find all Item instances that may reference
        // other resources in resource table itself or specify external files that should be
        // analyzed for references.
        entry.configValueList.asSequence()
            .map { it.value }
            .flatMap { value ->
                val compoundValue = value.compoundValue
                // compoundValue.attr and compoundValue.styleable are skipped, attr defines
                // references to ID resources only, but ID and STYLEABLE resources are not supported
                // by shrinker.
                when {
                    value.hasItem() ->
                        sequenceOf(value.item)
                    compoundValue.hasStyle() ->
                        sequenceOf(compoundValue.style.parent.asItem()) +
                                compoundValue.style.entryList.asSequence().flatMap {
                                    sequenceOf(
                                        it.item,
                                        it.key.asItem()
                                    )
                                }
                    compoundValue.hasArray() ->
                        compoundValue.array.elementList.asSequence().map { it.item }
                    compoundValue.hasPlural() ->
                        compoundValue.plural.entryList.asSequence().map { it.item }
                    else -> emptySequence()
                }
            }
            .forEach { findFromItem(it) }
    }

    private fun findFromItem(item: Resources.Item) {
        try {
            when {
                item.hasRef() -> findFromReference(item.ref)
                item.hasFile() && item.file.path.startsWith("res/") -> findFromFile(item.file)
            }
        } catch (e: IOException) {
            model.debugReporter.debug { "File '${item.file.path}' can not be processed. Skipping." }
        }
    }

    private fun findFromReference(reference: Reference) {
        // Reference object may have id of referenced resource, in this case prefer resolved id.
        // In case id is not provided try to find referenced resource by name. Name is converted
        // to resource url here, because name in resource table is not normalized to R style field
        // and to find it we need normalize it first (for example, in case name in resource table is
        // MyStyle.Child in R file it is R.style.MyStyle_child).
        val referencedResource = when {
            reference.id != 0 -> model.getResourceByValue(reference.id)
            reference.name.isNotEmpty() -> model.usageModel.getResourceFromUrl("@${reference.name}")
            else -> null
        }
        // IDs are not supported by shrinker for now, just skip it.
        referencedResource
            ?.takeIf { it.type != ResourceType.ID }
            ?.let { current.addReference(it) }
    }

    private fun findFromFile(file: FileReference) {
        val path = resourcesRoot.resolve(file.path.substringAfter("res/"))
        val bytes: ByteArray by lazy { Files.readAllBytes(path) }
        val content: String by lazy { String(bytes, StandardCharsets.UTF_8) }
        val extension = Ascii.toLowerCase(path.fileName.toString()).substringAfter('.')
        when {
            file.type == PROTO_XML -> fillFromXmlNode(XmlNode.parseFrom(bytes))
            extension in listOf("html", "htm") -> model.usageModel.tokenizeHtml(current, content)
            extension == "css" -> model.usageModel.tokenizeCss(current, content)
            extension == "js" -> model.usageModel.tokenizeJs(current, content)
            extension !in IMAGE_EXTENSIONS -> model.usageModel.tokenizeUnknownBinary(current, bytes)
        }
    }

    private fun fillFromXmlNode(node: XmlNode) {
        // Check for possible reference as 'android_res/<type>/<name>' pattern inside element text.
        maybeAndroidResUrl(node.text)
        // Check special xml element <rawPathResId> which provides reference to res/raw/ apk
        // resource for wear application. Applies to all XML files for now but might be re-scoped
        // to only apply to <wearableApp> XMLs.
        maybeWearAppReference(node.element)
        node.element.attributeList.forEach { fillFromAttribute(it) }
        node.element.childList.forEach { fillFromXmlNode(it) }
    }

    private fun fillFromAttribute(attribute: XmlAttribute) {
        if (attribute.hasCompiledItem()) {
            findFromItem(attribute.compiledItem)
        }
        // Check for possible reference as 'android_res/<type>/<name>' pattern inside attribute val.
        maybeAndroidResUrl(attribute.value)
    }

    private fun maybeAndroidResUrl(text: String) {
        if (current.type != ResourceType.XML) {
            return
        }
        text.splitToSequence("android_res/")
            .drop(1)
            .map { part -> part.takeWhile { !Character.isWhitespace(it) } }
            .map { it.split('/', limit = 2) }
            .filter { it.size == 2 }
            .map { (dir, fileName) ->
                Pair(
                    ResourceType.fromFolderName(dir.substringBefore('-')),
                    fileName.substringBefore('.')
                )
            }
            .filter { (type, _) -> type != null }
            .map { (type, name) -> model.usageModel.getResource(type!!, name) }
            .forEach { ResourceUsageModel.markReachable(it) }
    }

    private fun maybeWearAppReference(element: XmlElement) {
        if (element.name == "rawPathResId") {
            val rawResourceName = element.childList
                .map { it.text }
                .joinToString(separator = "")
                .trim()

            current.addReference(
                model.usageModel.getResource(
                    ResourceType.RAW,
                    resourceNameToFieldName(rawResourceName)
                )
            )
        }
    }
}
