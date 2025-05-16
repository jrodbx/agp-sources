
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
package com.android.build.gradle.internal.generators

import com.android.SdkConstants
import com.android.build.api.variant.ResValue
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.resources.ResourceType
import com.android.utils.XmlUtils
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
/**
 * Class able to generate a res value file in an Android project.
 */
class ResValueGenerator(
    genFolder: File,
    private val requests: Map<ResValue.Key, ResValue>
) {
    /**
     * Returns a File representing where the BuildConfig class will be.
     */
    private val folderPath = File(genFolder, "values")
    /**
     * Generates the resource files
     */
    @Throws(IOException::class, ParserConfigurationException::class)
    fun generate() {
        val pkgFolder = folderPath
        if (!pkgFolder.isDirectory) {
            if (!pkgFolder.mkdirs()) {
                throw RuntimeException("Failed to create " + pkgFolder.absolutePath)
            }
        }
        val resFile = File(pkgFolder, RES_VALUE_FILENAME_XML)
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isValidating = false
        factory.isIgnoringComments = true
        val builder = factory.newDocumentBuilder()
        val document = builder.newDocument()
        val rootNode: Node = document.createElement(SdkConstants.TAG_RESOURCES)
        document.appendChild(rootNode)
        rootNode.appendChild(document.createTextNode("\n"))
        rootNode.appendChild(document.createComment("Automatically generated file. DO NOT MODIFY"))
        rootNode.appendChild(document.createTextNode("\n\n"))
        for ((key, value) in requests) {
            value.comment?.also {
                rootNode.appendChild(document.createTextNode("\n"))
                rootNode.appendChild(document.createComment(it))
                rootNode.appendChild(document.createTextNode("\n"))
            }
            val type = key.type
            var resourceType = ResourceType.fromClassName(type)
            if (resourceType == null && SdkConstants.TAG_DECLARE_STYLEABLE == type) {
                resourceType = ResourceType.STYLEABLE
            }
            val hasResourceTag =
                resourceType != null && RESOURCES_WITH_TAGS.contains(
                    resourceType
                )
            val itemNode: Node =
                document.createElement(if (hasResourceTag) type else SdkConstants.TAG_ITEM)
            val nameAttr = document.createAttribute(SdkConstants.ATTR_NAME)
            nameAttr.value = key.name
            itemNode.attributes.setNamedItem(nameAttr)
            if (!hasResourceTag) {
                val typeAttr =
                    document.createAttribute(SdkConstants.ATTR_TYPE)
                typeAttr.value = type
                itemNode.attributes.setNamedItem(typeAttr)
            }
            if (resourceType == ResourceType.STRING) {
                val translatable = document.createAttribute(SdkConstants.ATTR_TRANSLATABLE)
                translatable.value = SdkConstants.VALUE_FALSE
                itemNode.attributes.setNamedItem(translatable)
            }
            if (value.value.isNotEmpty()) {
                itemNode.appendChild(document.createTextNode(value.value))
            }
            rootNode.appendChild(itemNode)
        }
        try {
            XmlPrettyPrinter.prettyPrint(document, true)
        } catch (t: Throwable) {
            XmlUtils.toXml(document)
        }?.also {
            Files.asCharSink(resFile, Charsets.UTF_8).write(it)
        }
    }
    companion object {
        const val RES_VALUE_FILENAME_XML = "gradleResValues.xml"
        private val RESOURCES_WITH_TAGS: List<ResourceType> =
            ImmutableList.of(
                ResourceType.ARRAY,
                ResourceType.ATTR,
                ResourceType.BOOL,
                ResourceType.COLOR,
                ResourceType.STYLEABLE,
                ResourceType.DIMEN,
                ResourceType.FRACTION,
                ResourceType.INTEGER,
                ResourceType.PLURALS,
                ResourceType.STRING,
                ResourceType.STYLE
            )
    }
}
