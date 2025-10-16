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

package com.android.build.gradle.internal.privaysandboxsdk

import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TOOLS_NS_NAME
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.ide.common.xml.XmlFormatPreferences
import com.android.ide.common.xml.XmlFormatStyle
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.utils.PositionXmlParser
import com.android.utils.forEach
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.io.InputStream

/**
 * Given an input manifest file from a privacy sandbox sdk,
 * return a manifest snippet with just the uses-permission elements,
 * all tagged with tools:requiredByPrivacySandboxSdk, for merging
 * in to the main app manifest.
 *
 * Bundle tool then strips them when the privacy sandbox is supported,
 * where the SDKs have the default permissions in the SDK, but keeps
 * them in the main app for the backward compatibility case.
 */
fun extractPrivacySandboxPermissions(asarManifest: InputStream): String {
    val document = PositionXmlParser.parse(asarManifest)
    val rootElement = document.documentElement
    var needsToolsNamespace = false
    var hasToolsNamespace = false
    var toolsNsName = TOOLS_NS_NAME
    // Find existing tools namespace
    rootElement.attributes.forEach { attribute ->
        if (attribute is Attr && attribute.name.startsWith(XMLNS_PREFIX) && attribute.value == TOOLS_URI) {
            toolsNsName = attribute.name.removePrefix(XMLNS_PREFIX)
            hasToolsNamespace = true
        }
    }
    // Keep only uses-permission elements
    rootElement.childNodes.forEach { node ->
        if (node is Element && node.tagName == TAG_USES_PERMISSION) {
            needsToolsNamespace = true
            val attr: Attr =
                    document.createAttributeNS(
                            TOOLS_URI,
                            "$toolsNsName:requiredByPrivacySandboxSdk")
            attr.value = "true"
            node.attributes.setNamedItemNS(attr)
        } else {
            rootElement.removeChild(node)
        }
    }
    if (needsToolsNamespace && !hasToolsNamespace) {
        rootElement.setAttribute(XMLNS_PREFIX + toolsNsName, TOOLS_URI)
    }
    return XmlPrettyPrinter.prettyPrint(
            document,
            XmlFormatPreferences.defaults(),
            XmlFormatStyle.MANIFEST,
            null,
            false)
}
