/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.bundle.DeviceGroup
import com.android.bundle.DeviceGroupConfig
import com.android.bundle.DeviceId
import com.android.bundle.DeviceSelector
import com.android.bundle.DeviceRam
import com.android.bundle.SystemFeature
import com.android.bundle.SystemOnChip
import com.android.utils.forEach
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXParseException
import javax.xml.XMLConstants
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.SchemaFactory

class DeviceTargetingConfigParser(private val config: Document) {

    class InvalidDeviceTargetingConfigException(message: String, t: Throwable) : Exception(message, t)

    fun parseConfig(): DeviceGroupConfig {
        try {
            validate(config)
        } catch (e: SAXParseException) {
            throw InvalidDeviceTargetingConfigException("The DeviceTargetingConfig xml provided is invalid.", e)
        }
        val builder = DeviceGroupConfig.newBuilder()
        config.documentElement.getElementsByTagNameNS(CONFIG_NS, "device-group").forEach {
            if (it is Element) {
                builder.addDeviceGroups(parseDeviceGroup(it))
            }
        }
        return builder.build()
    }

    companion object {

        private val CONFIG_NS = "http://schemas.android.com/apk/config"

        private fun parseDeviceGroup(group: Element): DeviceGroup.Builder {
            val builder = DeviceGroup.newBuilder().setName(group.getAttribute("name"))
            group.getElementsByTagNameNS(CONFIG_NS, "device-selector").forEach {
                if (it is Element) {
                    builder.addDeviceSelectors(parseDeviceSelector(it))
                }
            }
            return builder
        }

        private fun parseDeviceSelector(selector: Element): DeviceSelector.Builder {
            val builder = DeviceSelector.newBuilder()

            parseDeviceRam(selector)?.let {
                builder.setDeviceRam(it)
            }
            selector.getElementsByTagNameNS(CONFIG_NS, "included-device-id").forEach {
                if (it is Element) {
                    builder.addIncludedDeviceIds(parseDeviceId(it))
                }
            }
            selector.getElementsByTagNameNS(CONFIG_NS, "excluded-device-id").forEach {
                if (it is Element) {
                    builder.addExcludedDeviceIds(parseDeviceId(it))
                }
            }
            selector.getElementsByTagNameNS(CONFIG_NS, "required-system-feature").forEach {
                if (it is Element) {
                    builder.addRequiredSystemFeatures(parseSystemFeature(it))
                }
            }
            selector.getElementsByTagNameNS(CONFIG_NS, "forbidden-system-feature").forEach {
                if (it is Element) {
                    builder.addForbiddenSystemFeatures(parseSystemFeature(it))
                }
            }
            selector.getElementsByTagNameNS(CONFIG_NS, "system-on-chip").forEach {
                if (it is Element) {
                    builder.addSystemOnChips(parseSystemOnChip(it))
                }
            }
            return builder
        }

        private fun parseDeviceRam(element: Element): DeviceRam.Builder? {
            val ramMinBytes = element.getAttribute("ram-min-bytes")
            val ramMaxBytes = element.getAttribute("ram-max-bytes")
            if (ramMinBytes == null && ramMaxBytes == null) {
                return null
            }
            val builder = DeviceRam.newBuilder().setMinBytes(ramMinBytes?.toLongOrNull() ?: 0)
            if (ramMaxBytes != null) {
                builder.setMaxBytes(ramMaxBytes!!.toLongOrNull() ?: 0)
            }
            return builder
        }

        private fun parseDeviceId(element: Element): DeviceId.Builder {
            val builder = DeviceId.newBuilder().setBuildBrand(element.getAttribute("brand"))
            if (element.hasAttribute("device")) {
                builder.setBuildDevice(element.getAttribute("device"))
            }
            return builder
        }

        private fun parseSystemFeature(element: Element): SystemFeature.Builder {
            return SystemFeature.newBuilder().setName(element.getAttribute("name"))
        }

        private fun parseSystemOnChip(element: Element): SystemOnChip.Builder {
            return SystemOnChip.newBuilder()
                .setManufacturer(element.getAttribute("manufacturer"))
                .setModel(element.getAttribute("model"))
        }

        private fun validate(document: Document) {
            val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            val schema =
                schemaFactory.newSchema(DeviceTargetingConfigParser::class.java.getResource("device_targeting_config_schema.xsd"))
            schema.newValidator().validate(DOMSource(document))
        }
    }
}
