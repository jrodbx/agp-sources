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

package com.android.build.shrinker.usages

import com.android.SdkConstants.VALUE_STRICT
import com.android.build.shrinker.ResourceShrinkerModel
import com.android.utils.XmlUtils
import com.google.common.collect.ImmutableMap.copyOf
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory

/**
 * Records usages of tools:keep, tools:discard and tools:shrinkMode in resources.
 *
 * <p>This unit requires to analyze resources in raw XML format because as said in
 * <a href="https://developer.android.com/studio/write/tool-attributes>documentation</a> these
 * attributes may appear in any &lt;resources&gt; element and some files that contain such element
 * are not compiled to proto. For example raw and values resources like res/raw/keep.xml,
 * res/values/values.xml etc.
 *
 * @param rawResourcesPath path to folder with resources in raw format.
 */
class ToolsAttributeUsageRecorder(val rawResourcesPath: Path) : ResourceUsageRecorder {
    companion object {
        private val TOOLS_NAMESPACE = "http://schemas.android.com/tools"
    }

    override fun recordUsages(model: ResourceShrinkerModel) {
        Files.walk(rawResourcesPath)
            .filter { it.fileName.toString().endsWith(".xml", ignoreCase = true) }
            .forEach { processRawXml(it, model) }
    }

    private fun processRawXml(path: Path, model: ResourceShrinkerModel) {
        processResourceToolsAttributes(path).forEach { key, value ->
            when (key) {
                "keep" -> model.resourceStore.recordKeepToolAttribute(value)
                "discard" -> model.resourceStore.recordDiscardToolAttribute(value)
                "shrinkMode" ->
                    if (value == VALUE_STRICT) {
                        model.resourceStore.safeMode = false
                    }
            }
        }
    }

    private fun processResourceToolsAttributes(path: Path): Map<String, String> {
        val toolsAttributes = mutableMapOf<String, String>()
        XmlUtils.getUtfReader(path).use { reader: Reader ->
            val factory = XMLInputFactory.newInstance()
            val xmlStreamReader = factory.createXMLStreamReader(reader)

            var rootElementProcessed = false
            while (!rootElementProcessed && xmlStreamReader.hasNext()) {
                xmlStreamReader.next()
                if (xmlStreamReader.isStartElement) {
                    if (xmlStreamReader.localName == "resources") {
                        for (i in 0 until xmlStreamReader.attributeCount) {
                            if (xmlStreamReader.getAttributeNamespace(i) == TOOLS_NAMESPACE) {
                                toolsAttributes.put(
                                    xmlStreamReader.getAttributeLocalName(i),
                                    xmlStreamReader.getAttributeValue(i)
                                )
                            }
                        }
                    }
                    rootElementProcessed = true
                }
            }
        }
        return copyOf(toolsAttributes)
    }
}
