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
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_AUTO_VERIFY
import com.android.SdkConstants.ATTR_HOST
import com.android.SdkConstants.ATTR_MIMETYPE
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PATH
import com.android.SdkConstants.ATTR_PATH_PATTERN
import com.android.SdkConstants.ATTR_PATH_PREFIX
import com.android.SdkConstants.ATTR_PORT
import com.android.SdkConstants.ATTR_SCHEME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_DATA
import com.android.ide.common.blame.SourceFilePosition
import com.android.manifmerger.NavGraphExpander.expandNavGraphs
import com.android.utils.XmlUtils
import com.google.common.collect.ImmutableList
import java.util.TreeSet

/**
 * Singleton with [expandNavGraphs] method, which returns an XmlDocument with the <nav-graph>
 * elements converted into the corresponding <intent-filter> elements
 */
object NavGraphExpander {

    /**
     *  Return an [XmlDocument] with the <nav-graph> elements converted into the corresponding
     *  <intent-filter> elements
     *
     *  @param xmlDocument the input XmlDocument whose <nav-graph> elements will be converted
     *                     into <intent-filter> elements in the output XmlDocument.
     *                     This method assumes that this XmlDocument matches the underlying
     *                     XmlDocument.xml; e.g., if <nav-graph> elements have been added
     *                     to the DOM document since this XmlDocument was created, the new
     *                     <nav-graph> elements will not be converted.
     *  @param loadedNavigationMap the map of navigationId Strings to NavigationXmlDocuments,
     *                             which determine the set of DeepLinks used to generate the
     *                             <intent-filter> element for each <nav-graph> element
     *  @param mergingReportBuilder the MergingReport.Builder used for the entire merge
     *  @return a new XmlDocument similar to the input XmlDocument, but with any <nav-graph>
     *          elements replaced with corresponding <intent-filter> elements
     */
    fun expandNavGraphs(
            xmlDocument: XmlDocument,
            loadedNavigationMap: Map<String, NavigationXmlDocument>,
            mergingReportBuilder: MergingReport.Builder): XmlDocument {
        expandNavGraphs(xmlDocument.rootNode, loadedNavigationMap, mergingReportBuilder)
        return xmlDocument
    }

    /**
     * Helper method for [expandNavGraphs]. This method checks if the given xmlElement is
     * an <activity> element with <nav-graph> children, and if so, it calls the [expandNavGraph]
     * helper method to generate the <intent-filter> elements, and then it finally
     * removes the original <nav-graph> elements.
     */
    private fun expandNavGraphs(
            xmlElement: XmlElement,
            loadedNavigationMap: Map<String, NavigationXmlDocument>,
            mergingReportBuilder: MergingReport.Builder) {
        for (childElement in xmlElement.mergeableElements) {
            expandNavGraphs(childElement, loadedNavigationMap, mergingReportBuilder)
        }
        if (xmlElement.tagName != SdkConstants.TAG_ACTIVITY) {
            return
        }
        val navGraphs = xmlElement.getAllNodesByType(ManifestModel.NodeTypes.NAV_GRAPH)
        if (navGraphs.isEmpty()) {
            return
        }
        // expand each navGraph
        for (navGraph in navGraphs) {
            val graphValue: String? =
                navGraph.getAttributeValue(ANDROID_URI, SdkConstants.ATTR_VALUE)
            val navigationXmlId =
                    if (graphValue?.startsWith(SdkConstants.NAVIGATION_PREFIX) == true)
                        graphValue.substring(SdkConstants.NAVIGATION_PREFIX.length)
                    else null
            if (navigationXmlId == null) {
                val nsUriPrefix =
                    navGraph.lookupNamespacePrefix(ANDROID_URI, false)
                val graphName = nsUriPrefix + XmlUtils.NS_SEPARATOR + SdkConstants.ATTR_VALUE
                mergingReportBuilder.addMessage(
                        SourceFilePosition(xmlElement.document.sourceFile, xmlElement.position),
                        MergingReport.Record.Severity.ERROR,
                        "Missing or malformed attribute in <nav-graph> element. " +
                                "Android manifest <nav-graph> element must contain a $graphName " +
                                "attribute with a value beginning " +
                                "with \"${SdkConstants.NAVIGATION_PREFIX}\".")
                return
            }
            expandNavGraph(xmlElement, navigationXmlId, loadedNavigationMap, mergingReportBuilder)
        }
        // then remove each navGraph
        for (navGraph in navGraphs) {
            xmlElement.removeChild(navGraph)
            mergingReportBuilder.actionRecorder.recordNodeAction(
                    navGraph, Actions.ActionType.CONVERTED)
        }
    }

    /**
     * Helper method for [expandNavGraphs]. This method calls [findDeepLinks] to get all the
     * [DeepLink]s associated with a given navigationId, then generates the corresponding
     * <intent-filter> elements.
     */
    private fun expandNavGraph(
            xmlElement: XmlElement,
            navigationXmlId: String,
            loadedNavigationMap: Map<String, NavigationXmlDocument>,
            mergingReportBuilder: MergingReport.Builder) {
        val sourceFilePosition =
            SourceFilePosition(xmlElement.document.sourceFile, xmlElement.position)
        val deepLinks = try {
            findDeepLinks(
                navigationXmlId,
                loadedNavigationMap,
                mergingReportBuilder,
                sourceFilePosition
            )
        } catch (e: NavGraphException) {
            mergingReportBuilder.addMessage(
                sourceFilePosition,
                MergingReport.Record.Severity.ERROR,
                e.message ?: "Error finding deep links."
            )
            return
        }
        val actionRecorder = mergingReportBuilder.actionRecorder
        val deepLinkGroups = deepLinks.groupBy { getDeepLinkUriBody(it,
            includeQuery = false,
            includeFragment = false
        ) }
        for (deepLinkGroup in deepLinkGroups.values) {
            val deepLink = deepLinkGroup.first()
            // first create <intent-filter> element
            val intentFilterXmlElement =
                xmlElement.addChildElement(SdkConstants.TAG_INTENT_FILTER)
            if (deepLink.isAutoVerify) {
                intentFilterXmlElement.addAttribute(ANDROID_URI, ATTR_AUTO_VERIFY, "true")
            }
            val childElementDataList = mutableListOf<ChildElementData>()
            // then add children elements to <intent-filter> element
            if (deepLink.action.isNotBlank()) {
                childElementDataList.add(
                    ChildElementData(TAG_ACTION, ATTR_NAME, deepLink.action))
            }
            childElementDataList.add(ChildElementData(
                TAG_CATEGORY, ATTR_NAME, "android.intent.category.DEFAULT"))
            childElementDataList.add(ChildElementData(
                TAG_CATEGORY, ATTR_NAME, "android.intent.category.BROWSABLE"))
            for (scheme in deepLinkGroup.flatMap { it.schemes }.toSet()) {
                childElementDataList.add(ChildElementData(TAG_DATA, ATTR_SCHEME, scheme))
            }
            if (deepLink.host != null) {
                childElementDataList.add(ChildElementData(TAG_DATA, ATTR_HOST, deepLink.host))
            }
            if (deepLink.port != -1) {
                childElementDataList.add(
                    ChildElementData(TAG_DATA, ATTR_PORT, deepLink.port.toString()))
            }
            val path = deepLink.path
            when {
                path.substringBefore(".*").length == path.length - 2 ->
                    childElementDataList.add(
                        ChildElementData(TAG_DATA, ATTR_PATH_PREFIX, path.substringBefore(".*")))
                path.contains(".*") ->
                    childElementDataList.add(
                        ChildElementData(TAG_DATA, ATTR_PATH_PATTERN, path))
                else -> childElementDataList.add(ChildElementData(TAG_DATA, ATTR_PATH, path))
            }
            if (deepLink.mimeType != null) {
                childElementDataList.add(ChildElementData(TAG_DATA, ATTR_MIMETYPE, deepLink.mimeType))
            }
            childElementDataList.forEach {
                intentFilterXmlElement.addChildElementWithSingleAttribute(
                        it.tagName, ANDROID_URI, it.attrName, it.attrValue)
            }
            // finally record all added elements and attributes
            for (dl in deepLinkGroup) {
                recordXmlElementAddition(
                    intentFilterXmlElement, dl.sourceFilePosition, actionRecorder)
            }
        }
    }

    /**
     * Find [DeepLink]s from referenced [NavigationXmlDocument]s and return a List of them.
     *
     * If duplicate [DeepLink]s are found, throws a [NavGraphException]
     */
    @Throws(NavGraphException::class)
    fun findDeepLinks(
        navigationXmlId: String,
        loadedNavigationMap: Map<String, NavigationXmlDocument>,
        mergingReportBuilder: MergingReport.Builder,
        sourceFilePosition: SourceFilePosition
    ): List<DeepLink> {
        val deepLinkList: MutableList<DeepLink> = mutableListOf()
        findDeepLinks(
            navigationXmlId,
            loadedNavigationMap,
            deepLinkList,
            mergingReportBuilder,
            sourceFilePosition
        )
        return ImmutableList.copyOf(deepLinkList)
    }

    /**
     * This class is used to hold uri, action, and mimeType of a deep link and is used in
     * [findDeepLinks] below to check for duplicate elements.
     */
    private data class DeepLinkComparisonObject(
        private val uri: String,
        private val action: String,
        private val mimeType: String?)

    /**
     * Find [DeepLink]s from referenced [NavigationXmlDocument]s and add them
     * to the deepLinkList.
     *
     * If duplicate [DeepLink]s are found, throws a [NavGraphException]
     */
    @Throws(NavGraphException::class)
    private fun findDeepLinks(
        navigationXmlId: String,
        loadedNavigationMap: Map<String, NavigationXmlDocument>,
        deepLinkList: MutableList<DeepLink>,
        mergingReportBuilder: MergingReport.Builder,
        sourceFilePosition: SourceFilePosition,
        deepLinkComparisonObjects: MutableSet<DeepLinkComparisonObject> = mutableSetOf(),
        visitedNavigationFiles: MutableSet<String> = mutableSetOf(),
        navigationFileAncestors: TreeSet<String> = sortedSetOf()
    ) {
        // Check for an infinite loop caused by a circular reference among the navigation files.
        if (!navigationFileAncestors.add(navigationXmlId)) {
            throw NavGraphException(
                "Illegal circular reference among navigation files when traversing navigation " +
                        "file references: " +
                        navigationFileAncestors.joinToString(separator = " > ") +
                        " > $navigationXmlId."
            )
        }
        // Warn if the same navigation file is added to the navigation graph multiple times.
        if (!visitedNavigationFiles.add(navigationXmlId)) {
            mergingReportBuilder.addMessage(
                sourceFilePosition,
                MergingReport.Record.Severity.WARNING,
                "The navigation file with ID \"$navigationXmlId\" is included multiple times in " +
                        "the navigation graph, but only deep links on the first instance will be " +
                        "triggered at runtime. Consider consolidating these instances into a " +
                        "single <include> at a higher level of your navigation graph hierarchy."
            )
            navigationFileAncestors.remove(navigationXmlId)
            return
        }
        val navigationXmlDocument = loadedNavigationMap[navigationXmlId]
        navigationXmlDocument ?: throw NavGraphException(
                "Referenced navigation file with navigationXmlId = $navigationXmlId not found")
        for (deepLink in navigationXmlDocument.deepLinks) {
            for (deepLinkUri in getDeepLinkUris(deepLink)) {
                val deepLinkComparisonObject =
                    DeepLinkComparisonObject(
                        uri = deepLinkUri,
                        action = deepLink.action,
                        mimeType = deepLink.mimeType
                    )
                if (deepLinkComparisonObjects.contains(deepLinkComparisonObject)) {
                    val comparisonString = StringBuilder("uri:$deepLinkUri")
                    if (deepLink.action.isNotBlank()) {
                        comparisonString.append(", action:${deepLink.action}")
                    }
                    if (deepLink.mimeType != null) {
                        comparisonString.append(", mimeType:${deepLink.mimeType}")
                    }
                    throw NavGraphException(
                        "Multiple destinations found with a deep link containing $comparisonString."
                    )
                }
                deepLinkComparisonObjects.add(deepLinkComparisonObject)
            }
            deepLinkList.add(deepLink)
        }
        for (otherNavigationXmlId in navigationXmlDocument.navigationXmlIds) {
            findDeepLinks(
                otherNavigationXmlId,
                loadedNavigationMap,
                deepLinkList,
                mergingReportBuilder,
                sourceFilePosition,
                deepLinkComparisonObjects,
                visitedNavigationFiles,
                navigationFileAncestors
            )
        }
        navigationFileAncestors.remove(navigationXmlId)
    }

    private fun getDeepLinkUriBody(
        deepLink: DeepLink,
        includeQuery: Boolean,
        includeFragment: Boolean
    ): String {
        val hostString = if (deepLink.host == null) "//" else "//" + deepLink.host
        val portString = if (deepLink.port == -1) "" else ":" + deepLink.port
        val queryString = if (deepLink.query == null || !includeQuery) "" else "?${deepLink.query}"
        val fragmentString = if (deepLink.fragment == null || !includeFragment) "" else "#${deepLink.fragment}"
        return hostString + portString + deepLink.path + queryString + fragmentString
    }

    /**
     * Returns the list of possible URIs from a [DeepLink]
     *
     * Not guaranteed to return valid URI's (e.g., if a host is not specified, but
     * a port is), but that's okay because we're only using the returned URI's to check
     * for duplicate [DeepLink]s
     */
    private fun getDeepLinkUris(deepLink: DeepLink): List<String> {
        val builder: ImmutableList.Builder<String> = ImmutableList.builder()
        val body = getDeepLinkUriBody(deepLink, includeQuery = true, includeFragment = true)
        for (scheme in deepLink.schemes) {
            builder.add("$scheme:$body")
        }
        return builder.build()
    }

    /** Record addition of xmlElement and all of its descendants (attributes and elements) */
    private fun recordXmlElementAddition(
            xmlElement: XmlElement,
            sourceFilePosition: SourceFilePosition,
            actionRecorder: ActionRecorder) {
        val nodeRecord =
                Actions.NodeRecord(
                        Actions.ActionType.ADDED,
                        sourceFilePosition,
                        xmlElement.id,
                        null,
                        xmlElement.operationType)
        actionRecorder.recordNodeAction(xmlElement, nodeRecord)
        for (xmlAttribute in xmlElement.attributes) {
            recordXmlAttributeAddition(xmlAttribute, sourceFilePosition, actionRecorder)
        }
        for (childXmlElement in xmlElement.mergeableElements) {
            recordXmlElementAddition(childXmlElement, sourceFilePosition, actionRecorder)
        }
    }

    /** Record addition of xmlAttribute */
    private fun recordXmlAttributeAddition(
            xmlAttribute: XmlAttribute,
            sourceFilePosition: SourceFilePosition,
            actionRecorder: ActionRecorder) {
        val attributeRecord =
                Actions.AttributeRecord(
                        Actions.ActionType.ADDED, sourceFilePosition, xmlAttribute.id, null, null)
        actionRecorder.recordAttributeAction(xmlAttribute, attributeRecord)

    }

    /** class to hold data for child elements of added <intent-filter> element. */
    private data class ChildElementData(
            val tagName: String, val attrName: String, val attrValue: String)

    /** An exception during the evaluation of an Android Manifest <nav-graph> element. */
    class NavGraphException(s: String) : RuntimeException(s)
}
