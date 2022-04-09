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

package com.android.build.gradle.internal.privaysandboxsdk

import com.android.SdkConstants
import com.android.ide.common.xml.XmlFormatPreferences
import com.android.ide.common.xml.XmlFormatStyle
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.utils.PositionXmlParser
import com.android.utils.forEach
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.io.InputStream

fun tagAllElementsAsRequiredByPrivacySandboxSdk(asarManifest: InputStream): String {
    val document = PositionXmlParser.parse(asarManifest)
    val rootElement = document.documentElement
    rootElement.childNodes.forEach { node ->
        if (node is Element && node.tagName == "uses-permission") {
            val attr: Attr =
                    document.createAttributeNS(
                            SdkConstants.TOOLS_URI,
                            "${SdkConstants.TOOLS_NS_NAME}:requiredByPrivacySandboxSdk")
            attr.value = "true"
            node.attributes.setNamedItemNS(attr)
        } else {
            rootElement.removeChild(node)
        }

    }
    return XmlPrettyPrinter.prettyPrint(
            document,
            XmlFormatPreferences.defaults(),
            XmlFormatStyle.MANIFEST,
            null,
            false)
}
