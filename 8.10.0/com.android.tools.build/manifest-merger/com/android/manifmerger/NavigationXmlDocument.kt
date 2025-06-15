/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.ide.common.blame.SourceFile
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourceFilePosition.UNKNOWN
import com.android.manifmerger.DeepLink.DeepLinkException
import com.android.utils.PositionXmlParser
import com.android.utils.XmlUtils
import com.google.common.collect.ImmutableList
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap

/**
 * Represents a loaded navigation xml document.
 *
 * Has a pointer to the root [Element] and provides services to get lists of the directly
 * referenced navigation xml IDs and [DeepLink]s
 */
class NavigationXmlDocument private constructor(
    private val sourceFile: SourceFile?,
    private val rootElement: Element?,
    private val precomputedData: NavigationXmlDocumentData?) {

    constructor(data: NavigationXmlDocumentData) :
            this(null, null, data)

    constructor(sourceFile: SourceFile, rootElement: Element) :
            this(sourceFile, rootElement, null)

    fun convertToData(
        manifestPlaceHolders: Map<String, String>,
        useUnknownSourceFilePosition: Boolean
    ) =
        NavigationXmlDocumentData(
            name!!,
            navigationXmlIds,
            processDeepLinks(deepLinks, manifestPlaceHolders, useUnknownSourceFilePosition)
        )

    /**
     * The list of navigation xml IDs found in this document, including duplicates.
     * navigation xml IDs are found in <include> elements as
     * "app:graph" attribute values, trimmed of their "@navigation/" prefix.
     */
    val navigationXmlIds: List<String> by lazy {
        precomputedData?.let {
            return@lazy it.navigationXmlIds
        }
        val navigationXmlIds = ArrayList<String>()
        getNavigationXmlIds(navigationXmlIds, rootElement!!)
        ImmutableList.copyOf(navigationXmlIds)
    }

    /**
     * The list of [DeepLink]s found in this document, including duplicates.
     * [DeepLink]s are found in <deepLink> elements.
     */
    val deepLinks: List<DeepLink> by lazy {
        precomputedData?.let {
            return@lazy it.deepLinks
        }
        val deepLinks = ArrayList<DeepLink>()
        getDeepLinks(deepLinks, rootElement!!)
        ImmutableList.copyOf(deepLinks)
    }

    val name: String? =
        precomputedData?.let {
            it.name
        } ?: sourceFile!!.description

    /**
     * Recursive search for navigation xml IDs
     *
     * @param navigationXmlIds The list of navigation xml IDs found in the previous recursive calls.
     * Subsequent IDs will be added to this list.
     * @param element The element searched for navigation xml IDS. The search is recursive through
     * element's descendants.
     */
    @Throws(NavigationXmlDocumentException::class)
    private fun getNavigationXmlIds(navigationXmlIds: MutableList<String>, element: Element) {
        if (element.tagName == SdkConstants.TAG_INCLUDE) {
            val namedNodeMap : NamedNodeMap? = element.attributes
            val graphValue =
                    namedNodeMap
                            ?.getNamedItemNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_GRAPH)
                            ?.nodeValue
            if (graphValue != null && graphValue.startsWith(SdkConstants.NAVIGATION_PREFIX)) {
                navigationXmlIds.add(graphValue.substring(SdkConstants.NAVIGATION_PREFIX.length))
            } else {
                val nsUriPrefix =
                        XmlUtils.lookupNamespacePrefix(element, SdkConstants.AUTO_URI, false)
                val graphName = nsUriPrefix + XmlUtils.NS_SEPARATOR + SdkConstants.ATTR_GRAPH
                throw NavigationXmlDocumentException(
                        "Navigation XML document <include> element must contain a $graphName " +
                                "attribute, whose value begins " +
                                "with ${SdkConstants.NAVIGATION_PREFIX} .")
            }
        }
        for (childNode in XmlUtils.getSubTagsAsList(element)) {
            getNavigationXmlIds(navigationXmlIds, childNode)
        }
    }

    /**
     * Recursive search for [DeepLink]s, which are found in <deepLink> elements
     *
     * @param deepLinks The list of [DeepLink]s found in the previous recursive calls.
     * Subsequent [DeepLink]s will be added to this list.
     * @param element The element searched for [DeepLink]s. The search is recursive through
     * element's descendants.
     */
    @Throws(NavigationXmlDocumentException::class, DeepLinkException::class)
    private fun getDeepLinks(deepLinks: MutableList<DeepLink>, element: Element) {
        if (element.tagName == SdkConstants.TAG_DEEP_LINK) {
            val namedNodeMap : NamedNodeMap? = element.attributes
            val deepLinkUri =
                    namedNodeMap
                            ?.getNamedItemNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_URI)
                            ?.nodeValue
            val autoVerifyAttribute =
                    namedNodeMap
                            ?.getNamedItemNS(
                                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_AUTO_VERIFY)
            val action =
                    namedNodeMap?.getNamedItemNS(
                            SdkConstants.AUTO_URI, SdkConstants.ATTR_DEEPLINK_ACTION)?.nodeValue
            val mimeType =
                    namedNodeMap?.getNamedItemNS(
                            SdkConstants.AUTO_URI, SdkConstants.ATTR_DEEPLINK_MIMETYPE)?.nodeValue
            if (mimeType != null && mimeType.isBlank()) {
                throw NavigationXmlDocumentException(
                        "Navigation XML document <deepLink> element " +
                                "${SdkConstants.ATTR_DEEPLINK_MIMETYPE} cannot be blank.")
            }
            val autoVerify = autoVerifyAttribute?.nodeValue == "true"
            val sourceFilePosition =
                    SourceFilePosition(sourceFile!!, PositionXmlParser.getPosition(element))
            if (deepLinkUri != null) {
                deepLinks.add(DeepLink.fromUri(
                        deepLinkUri, sourceFilePosition, autoVerify, action, mimeType))
            } else {
                val nsUriPrefix =
                        XmlUtils.lookupNamespacePrefix(element, SdkConstants.AUTO_URI, false)
                val uriName = nsUriPrefix + XmlUtils.NS_SEPARATOR + SdkConstants.ATTR_URI
                throw NavigationXmlDocumentException(
                        "Navigation XML document <deepLink> element must contain a $uriName " +
                                "attribute.")
            }
        }
        for (childNode in XmlUtils.getSubTagsAsList(element)) {
            getDeepLinks(deepLinks, childNode)
        }
    }

    /** An exception during the evaluation of a [NavigationXmlDocument].  */
    class NavigationXmlDocumentException(s: String) : RuntimeException(s)
}

private fun processDeepLinks(
    deepLinks: List<DeepLink>,
    manifestPlaceHolders: Map<String, String>,
    useUnknownSourceFilePosition: Boolean
): List<DeepLink> {
    return deepLinks.map { deepLink ->
        DeepLink(
            deepLink.schemes.map { it.performPlaceholderSubstitution(manifestPlaceHolders) },
            deepLink.host?.performPlaceholderSubstitution(manifestPlaceHolders),
            deepLink.port,
            deepLink.path.performPlaceholderSubstitution(manifestPlaceHolders),
            deepLink.query,
            deepLink.fragment,
            if (useUnknownSourceFilePosition) {
                UNKNOWN
            } else {
                SourceFilePosition(
                    SourceFile(deepLink.sourceFilePosition.file.sourceFile.name),
                    deepLink.sourceFilePosition.position
                )
            },
            deepLink.isAutoVerify,
            deepLink.action,
            deepLink.mimeType
        )
    }
}

private fun String.performPlaceholderSubstitution(manifestPlaceHolders: Map<String, String>): String {
    var result = this
    manifestPlaceHolders.forEach {
        result = result.replace("\${${it.key}}", it.value)
    }
    return result
}
