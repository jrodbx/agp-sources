/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.manifmerger.ManifestUtils
import com.android.utils.XmlUtils
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Utility class that provide functions that manipulate DOM.
 * Note: DOM modification should never be done directly during manifest merger. Use the wrapper
 * objects(with prefix Xml example: XmlDocument, XmlElement, XmlAttribute) mutation methods to
 * modify DOM during manifest merger. Only use this utility class when the wrapper objects are not
 * available.
 */
object ManifestUtils {

    /**
     * Add given attribute to [ManifestModel.NodeTypes.MANIFEST] element of the Android Manifest DOM
     *
     * @param document Manifest [Document] object
     * @param attribute Attribute name
     * @param value Attribute value
     * @return The Attr value as a nullable string
     */
    fun setManifestAndroidAttribute(
        document: Document, attribute: String, value: String
    ): String? {
        val manifest = document.documentElement ?: return null
        val previousValue = if (manifest.hasAttributeNS(SdkConstants.ANDROID_URI, attribute)) manifest.getAttributeNS(
            SdkConstants.ANDROID_URI, attribute
        ) else null
        setAndroidAttribute(manifest, attribute, value)
        return previousValue
    }

    private fun setAndroidAttribute(node: Element, localName: String, value: String?) {
        val prefix = XmlUtils.lookupNamespacePrefix(
            node, SdkConstants.ANDROID_URI, SdkConstants.ANDROID_NS_NAME, true
        )
        node.setAttributeNS(SdkConstants.ANDROID_URI, "$prefix:$localName", value)
    }
}
