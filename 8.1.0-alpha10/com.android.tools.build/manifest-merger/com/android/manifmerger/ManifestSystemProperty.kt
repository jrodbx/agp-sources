/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourcePosition
import com.android.manifmerger.Actions.AttributeRecord
import com.android.manifmerger.Actions.NodeRecord
import com.android.manifmerger.ManifestMerger2.AutoAddingProperty
import com.android.manifmerger.ManifestModel.NodeTypes
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils
import org.w3c.dom.Element

/**
 * List of manifest files properties that can be directly overridden without using a
 * placeholder.
 */
interface ManifestSystemProperty : AutoAddingProperty {

    companion object {

        @JvmStatic
        val values: List<ManifestSystemProperty> = listOf(
            Application.values(),
            Document.values(),
            Instrumentation.values(),
            Manifest.values(),
            Profileable.values(),
            UsesSdk.values()
        ).flatMap { it.asList() }

        @JvmStatic
        fun valueOf(value: String): ManifestSystemProperty {
            return values.associateBy(ManifestSystemProperty::name)[value]
                ?: throw IllegalArgumentException("'$value' not a valid ManifestSystemProperty.")
        }
    }

    val name: String

    /**
     * @see [
     * https://developer.android.com/guide/topics/manifest/application-element](https://developer.android.com/guide/topics/manifest/application-element)
     */
    enum class Application : ManifestSystemProperty {

        TEST_ONLY;

        override fun addTo(actionRecorder: ActionRecorder, document: XmlDocument, value: String) {
            val msp = createOrGetElementInManifest(
                actionRecorder,
                document,
                NodeTypes.APPLICATION,
                "application injection requested"
            )
            addToElementInAndroidNS(this, actionRecorder, value, msp)
        }
    }

    enum class Document : ManifestSystemProperty {

        PACKAGE;
        override fun addTo(actionRecorder: ActionRecorder, document: XmlDocument, value: String) {
            addToElement(this, actionRecorder, value, document.rootNode)
        }
    }

    /**
     * @see [
     * http://developer.android.com/guide/topics/manifest/instrumentation-element.html](http://developer.android.com/guide/topics/manifest/instrumentation-element.html)
     */
    enum class Instrumentation : ManifestSystemProperty {
        FUNCTIONAL_TEST,
        HANDLE_PROFILING,
        NAME,
        LABEL,

        TARGET_PACKAGE;
        override fun addTo(actionRecorder: ActionRecorder, document: XmlDocument, value: String) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetElementInManifest(
                    actionRecorder,
                    document,
                    NodeTypes.INSTRUMENTATION,
                    "instrumentation injection requested"
                )
            )
        }
    }

    /**
     * @see [
     * https://developer.android.com/guide/topics/manifest/profileable-element.vcode](https://developer.android.com/guide/topics/manifest/profileable-element.vcode)
     */
    enum class Manifest : ManifestSystemProperty {

        VERSION_CODE,
        VERSION_NAME;

        override fun addTo(actionRecorder: ActionRecorder, document: XmlDocument, value: String) {
            addToElementInAndroidNS(this, actionRecorder, value, document.rootNode)
        }
    }

    enum class Profileable : ManifestSystemProperty {

        ENABLED,
        SHELL;

        override fun addTo(actionRecorder: ActionRecorder, document: XmlDocument, value: String) {
            val maybeApplicationElement = document.getByTypeAndKey(NodeTypes.APPLICATION, null)
            // If no application element, nothing to add.
            maybeApplicationElement.ifPresent { applicationElement ->
                addToElementInAndroidNS(
                    this, actionRecorder, value,
                    createOrGetElement(
                        actionRecorder,
                        document,
                        applicationElement,
                        NodeTypes.PROFILEABLE,
                        "profileable injection requested"
                    )
                )
            }
        }
    }
    /**
     * @see [
     * http://developer.android.com/guide/topics/manifest/uses-sdk-element.html.min](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html.min)
     */
    enum class UsesSdk : ManifestSystemProperty {

        MAX_SDK_VERSION,
        MIN_SDK_VERSION,
        TARGET_SDK_VERSION;

        override fun addTo(actionRecorder: ActionRecorder, document: XmlDocument, value: String) {
            addToElementInAndroidNS(
                this, actionRecorder, value,
                createOrGetElementInManifest(
                    actionRecorder,
                    document,
                    NodeTypes.USES_SDK,
                    "use-sdk injection requested"
                )
            )
        }
    }
}

fun ManifestSystemProperty.toCamelCase(): String {
    return SdkUtils.constantNameToCamelCase(name)
}

// utility method to add an attribute which name is derived from the enum name().
private fun addToElement(
    elementAttribute: ManifestSystemProperty,
    actionRecorder: ActionRecorder,
    value: String,
    to: XmlElement
) {
    to.setAttribute(elementAttribute.toCamelCase(), value)
    val xmlAttribute = XmlAttribute(
        to,
        to.getAttributeNode(elementAttribute.toCamelCase()), null
    )
    recordElementInjectionAction(actionRecorder, to, xmlAttribute)
}

// utility method to add an attribute in android namespace which local name is derived from
// the enum name().
private fun addToElementInAndroidNS(
    elementAttribute: ManifestSystemProperty,
    actionRecorder: ActionRecorder,
    value: String,
    to: XmlElement
) {
    val toolsPrefix = to.lookupNamespacePrefix(
        SdkConstants.ANDROID_URI, SdkConstants.ANDROID_NS_NAME, true)
    to.setAttributeNS(
        SdkConstants.ANDROID_URI,
        toolsPrefix + XmlUtils.NS_SEPARATOR + elementAttribute.toCamelCase(),
        value
    )
    val attr = to.getAttributeNodeNS(
        SdkConstants.ANDROID_URI,
        elementAttribute.toCamelCase()
    )
    val xmlAttribute = XmlAttribute(to, attr!!, null)
    recordElementInjectionAction(actionRecorder, to, xmlAttribute)
}

private fun recordElementInjectionAction(
    actionRecorder: ActionRecorder,
    to: XmlElement,
    xmlAttribute: XmlAttribute
) {
    actionRecorder.recordNodeAction(to, Actions.ActionType.INJECTED)
    actionRecorder.recordAttributeAction(
        xmlAttribute, AttributeRecord(
            Actions.ActionType.INJECTED,
            SourceFilePosition(to.sourceFile, SourcePosition.UNKNOWN),
            xmlAttribute.id,
            null,  /* reason */
            null /* attributeOperationType */
        )
    )
}

private fun createOrGetElementInManifest(
    actionRecorder: ActionRecorder,
    document: XmlDocument,
    nodeType: NodeTypes,
    message: String
): XmlElement {
    val manifest = document.rootNode
    return createOrGetElement(actionRecorder, document, manifest, nodeType, message)
}

private fun createOrGetElement(
    actionRecorder: ActionRecorder,
    document: XmlDocument,
    parentElement: XmlElement,
    nodeType: NodeTypes,
    message: String
): XmlElement {
    return parentElement.createOrGetElementOfType(
        document,
        nodeType,
        SdkConstants.ANDROID_URI) { xmlElement ->
        val nodeRecord = NodeRecord(
            Actions.ActionType.INJECTED,
            SourceFilePosition(
                xmlElement.sourceFile,
                SourcePosition.UNKNOWN
            ),
            xmlElement.id,
            message,
            NodeOperationType.STRICT
        )
        actionRecorder.recordNodeAction(xmlElement, nodeRecord)
    }
}
