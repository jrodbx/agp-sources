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

package com.android.manifmerger

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME

import com.android.SdkConstants
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import java.util.ArrayList
import java.util.Collections
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Specific [NodeKeyResolver] for intent-filter elements. Intent filters do not have a proper key,
 * therefore their identity is really carried by the presence of their sub-elements. We concatenate
 * the sub-elements' attributes' names and values (after sorting them to work around declaration
 * order) and use that for the intent-filter unique key. An example key might look like
 * "action:name:android.intent.action.VIEW+category:name:android.intent.category.DEFAULT+data:host:www.example.com"
 */
object IntentFilterNodeKeyResolver : NodeKeyResolver {
    private val model = ManifestModel()

    private val dataAttributeNames = ImmutableList.of(
            SdkConstants.ATTR_SCHEME,
            SdkConstants.ATTR_HOST,
            SdkConstants.ATTR_MIME_TYPE,
            SdkConstants.ATTR_PORT,
            SdkConstants.ATTR_PATH,
            SdkConstants.ATTR_PATH_PATTERN,
            SdkConstants.ATTR_PATH_PREFIX)

    override val keyAttributesNames: ImmutableList<String>
        get() {
            val builder = ImmutableList.builder<String>()
            builder.add("action#name", "category#name")
            for (dataAttributeName in dataAttributeNames) {
                builder.add("data#$dataAttributeName")
            }
            return builder.build()
        }

    override fun getKey(element: Element): String? {
        val xmlElement = OrphanXmlElement(element, model)
        assert(xmlElement.type == ManifestModel.NodeTypes.INTENT_FILTER)
        // concatenate attribute info for action, category, and data sub-elements.
        val subElementAttributes = ArrayList<String>()
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val subElement = OrphanXmlElement(child as Element, model)
            if (subElement.type == ManifestModel.NodeTypes.ACTION ||
                    subElement.type == ManifestModel.NodeTypes.CATEGORY) {
                val attributeInfo = getAttributeInfo(subElement, ATTR_NAME)
                if (attributeInfo != null) {
                    subElementAttributes.add(attributeInfo)
                }
            } else if (subElement.type == ManifestModel.NodeTypes.DATA) {
                for (dataAttributeName in dataAttributeNames) {
                    val attributeInfo = getAttributeInfo(subElement, dataAttributeName)
                    if (attributeInfo != null) {
                        subElementAttributes.add(attributeInfo)
                    }
                }
            }
        }
        subElementAttributes.sort()
        return Joiner.on('+').join(subElementAttributes)
    }

    private fun getAttributeInfo(
            xmlElement: OrphanXmlElement, attributeName: String): String? {
        val element = xmlElement.xml
        val attr = element.getAttributeNodeNS(ANDROID_URI, attributeName)
        return if (attr == null) {
            null
        } else {
            element.tagName + ":" + attributeName + ":" + attr.value
        }
    }
}
