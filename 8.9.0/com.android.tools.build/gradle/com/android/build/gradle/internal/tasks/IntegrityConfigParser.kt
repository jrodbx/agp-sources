/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.bundle.AppIntegrityConfigOuterClass.AppIntegrityConfig
import com.android.bundle.AppIntegrityConfigOuterClass.EmulatorCheck
import com.android.bundle.AppIntegrityConfigOuterClass.InstallerCheck
import com.android.bundle.AppIntegrityConfigOuterClass.LicenseCheck
import com.android.bundle.AppIntegrityConfigOuterClass.Policy
import com.android.utils.forEach
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXParseException
import javax.xml.XMLConstants
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.SchemaFactory

class IntegrityConfigParser(private val config: Document) {

    class InvalidIntegrityConfigException(message: String, t: Throwable) : Exception(message, t)

    fun parseConfig(): AppIntegrityConfig {
        try {
            validate(config)
        } catch (e: SAXParseException) {
            throw InvalidIntegrityConfigException("The IntegrityConfig xml provided is invalid.", e)
        }
        val configElement = config.documentElement
        return AppIntegrityConfig.newBuilder()
            .setEnabled(isEnabled(configElement))
            .setLicenseCheck(parseLicenseCheckConfig(configElement))
            .setInstallerCheck(parseInstallerCheckConfig(configElement))
            .setEmulatorCheck(parseEmulatorCheckConfig(configElement))
            .build()
    }

    companion object {
        private val POLICY_ACTION_MAP: Map<String, Policy.Action> = mapOf(
            "DISABLE" to Policy.Action.DISABLE,
            "WARN" to Policy.Action.WARN,
            "WARN_THEN_DISABLE" to Policy.Action.WARN_THEN_DISABLE
        )

        private val DEFAULT_CONFIG: AppIntegrityConfig = AppIntegrityConfig.newBuilder()
            .setEnabled(true)
            .setLicenseCheck(
                LicenseCheck.newBuilder().setEnabled(false).setPolicy(
                    Policy.newBuilder().setAction(Policy.Action.WARN)
                )
            )
            .setInstallerCheck(
                InstallerCheck.newBuilder().setEnabled(true).setPolicy(
                    Policy.newBuilder().setAction(Policy.Action.WARN)
                )
            )
            .setEmulatorCheck(EmulatorCheck.newBuilder().setEnabled(true))
            .build()

        private fun org.w3c.dom.Element.getChildByTagName(tagName: String): Element? {
            childNodes.forEach {
                if (it is Element && it.tagName == tagName) {
                    return it
                }
            }
            return null
        }

        private fun parseLicenseCheckConfig(parent: Element): LicenseCheck.Builder {
            val builder = LicenseCheck.newBuilder(DEFAULT_CONFIG.licenseCheck)
            parent.getChildByTagName("LicenseCheck")?.let { licenseCheckElement ->
                builder.enabled = isEnabled(licenseCheckElement)
                parsePolicy(licenseCheckElement)?.let { policy ->
                    builder.policy = policy
                }
            }
            return builder
        }

        private fun parseInstallerCheckConfig(parent: Element): InstallerCheck.Builder {
            val builder = InstallerCheck.newBuilder(DEFAULT_CONFIG.installerCheck)
            parent.getChildByTagName("InstallerCheck")?.let { installerCheckElement ->
                builder.enabled = isEnabled(installerCheckElement)
                parsePolicy(installerCheckElement)?.let { policy ->
                    builder.policy = policy
                }
                installerCheckElement.getElementsByTagName("AdditionalInstallSource").forEach {
                    builder.addAdditionalInstallSource(it.textContent)
                }
            }
            return builder
        }

        private fun parseEmulatorCheckConfig(parent: Element): EmulatorCheck.Builder {
            val builder = EmulatorCheck.newBuilder(DEFAULT_CONFIG.emulatorCheck)
            parent.getChildByTagName("EmulatorCheck")?.let { emulatorCheckElement ->
                builder.enabled = isEnabled(emulatorCheckElement)
            }
            return builder
        }

        private fun parsePolicy(parent: Element): Policy? {
            return parent.getChildByTagName("Policy")?.let { policyElement ->
                val action = POLICY_ACTION_MAP[policyElement.getAttribute("action")]
                return Policy.newBuilder().setAction(action).build()
            }
        }

        private fun isEnabled(element: Element): Boolean {
            return !element.hasAttribute("enabled")
                    || element.getAttribute("enabled") == "true"
        }

        private fun validate(document: Document) {
            val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            val schema =
                schemaFactory.newSchema(IntegrityConfigParser::class.java.getResource("integrity_config_schema.xsd"))
            schema.newValidator().validate(DOMSource(document))
        }
    }
}
