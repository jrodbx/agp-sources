/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.manifmerger.ManifestMerger2.ProcessCancellationChecker
import java.util.Optional

/**
 * Pairs XML elements from lower-priority and higher-priority manifest XMLs based on their type, their unique key
 * and feature flag attribute.
 *
 * This function facilitates the merging of manifest XMLs by aligning elements from two sources for potential
 * subsequent merging operations. Normally, the manifest XML have elements with unique keys but there are two
 * scenarios where there could elements with identical keys in a manifest: When node type allows multiple declaration
 * or when feature flag is used.
 *
 * @param lowerPriorityMergingElement The root element of the lower-priority XML source.
 * @param higherPriorityMergingElement The root element of the higher-priority XML source.
 *
 * @return A set of pairs, where each pair consists of a lower-priority XML element and an `Optional`
 *         containing either the matching higher-priority element or `Optional.empty()` if there's no match.
 */
fun mapMergingElements(
    lowerPriorityMergingElement: XmlElement,
    higherPriorityMergingElement: XmlElement,
    processCancellationChecker: ProcessCancellationChecker,
    mergingReport: MergingReport.Builder
    ): Set<Pair<XmlElement, Optional<XmlElement>>> {
    val lowPriorityElementsByKey = lowerPriorityMergingElement.childrenByTypeAndKey
    val elementsByKey = higherPriorityMergingElement.childrenByTypeAndKey
    val mappedNodes = mutableSetOf<Pair<XmlElement, Optional<XmlElement>>>()
    lowPriorityElementsByKey.keys.forEach { key ->
        processCancellationChecker.check()
        val lowerPriorityChildren = lowPriorityElementsByKey[key]
        val higherPriorityChildren = elementsByKey[key]
        lowerPriorityChildren?.forEach { lowerPriorityChild ->
            processCancellationChecker.check()
            if (higherPriorityChildren.isNullOrEmpty()) {
                // We did not find any nodes in merged manifest that match this key. This means:
                // Either, the node key is null in which case we match with the first element with same type.
                // Or, this is a unique node being added to merged manifest.
                val matchingNode = if (lowerPriorityChild.key == null) {
                    higherPriorityMergingElement.getFirstNodeByType(lowerPriorityChild.type)
                } else {
                    Optional.empty()
                }
                mappedNodes.add(lowerPriorityChild to matchingNode)
            } else if (lowerPriorityChild.type.areMultipleDeclarationAllowed()) {
                // If multiple declarations are allowed, we can ignore feature flag attribute.
                mappedNodes.add(lowerPriorityChild to Optional.of(higherPriorityChildren[0]))
            } else {
                mappedNodes.addAll(
                    mapSingleDeclarationNodeTypes(lowerPriorityChild, higherPriorityChildren, processCancellationChecker, mergingReport))
            }
        }
    }
    return mappedNodes
}

/**
 * Pairs a given lower-priority xml element's child with children of higher priority xml element with matching node type and key
 * by also considering the feature flag attributes on these xml elements.
 * @param lowerPriorityChild Child of a lower-priority XML element.
 * @param higherPriorityChildren Children of higher-priority XML element with same node type and key as [lowerPriorityChild].
 *
 * @return A set of pairs, where each pair consists of a lower-priority XML element and an `Optional`
 *         containing either the matching higher-priority element or `Optional.empty()` if there's no match.
 */
fun mapSingleDeclarationNodeTypes(
    lowerPriorityChild: XmlElement,
    higherPriorityChildren: List<XmlElement>,
    processCancellationChecker: ProcessCancellationChecker,
    mergingReport: MergingReport.Builder
    ) : Set<Pair<XmlElement, Optional<XmlElement>>> {
    val mappedNodes = mutableSetOf<Pair<XmlElement, Optional<XmlElement>>>()
    val nodesWithFeatureFlag = higherPriorityChildren.filter { it.hasFeatureFlag() }
    val nodesWithoutFeatureFlag = higherPriorityChildren - nodesWithFeatureFlag.toSet()
    if (lowerPriorityChild.hasFeatureFlag()) {
        // If the child has a feature flag, we simply find amd match with the existing
        // element with the same feature flag
        val matchedExistingChild = higherPriorityChildren.firstOrNull { it.featureFlag() == lowerPriorityChild.featureFlag() }
        if (matchedExistingChild != null || nodesWithoutFeatureFlag.isEmpty()) {
            mappedNodes.add(lowerPriorityChild to Optional.ofNullable(matchedExistingChild))
        } else {
            val highPriorityChildWithNoFeatureFlag = nodesWithoutFeatureFlag.first()
            mergingReport.addMessage(
                highPriorityChildWithNoFeatureFlag,
                MergingReport.Record.Severity.ERROR,
                """Cannot merge element ${lowerPriorityChild.id} at ${lowerPriorityChild.printPosition()}
                                |    with feature flag into an element ${highPriorityChildWithNoFeatureFlag.id}
                                |    at ${highPriorityChildWithNoFeatureFlag.printPosition()} without feature flag.
                                |    This can be fixed by explicitly declaring ${highPriorityChildWithNoFeatureFlag.id} with
                                |    feature flag ${lowerPriorityChild.featureFlag()?.attributeValue}.
                            """.trimMargin())
        }
    } else {
        if (nodesWithoutFeatureFlag.isNotEmpty()) {
            // When we have low priority child without feature flag, and we find an existing child with no feature flag,
            // we have found our match(they have node type, key and no feature flags).
            val firstNodeWithoutFlag = nodesWithoutFeatureFlag.first()
            mappedNodes.add(lowerPriorityChild to Optional.ofNullable(firstNodeWithoutFlag))
        } else {
            // Our low priority child element does not have feature flags, and also we find the
            // existing element with feature flag, in this case, we clone the child and merge
            // with all existing elements with feature flag.
            nodesWithFeatureFlag.forEach { higherPriorityChild ->
                val featureFlag = higherPriorityChild.featureFlag()
                val clonedChild = lowerPriorityChild.clone()
                featureFlag?.let { clonedChild.setFeatureFlag(it.attributeValue) }
                mappedNodes.add(clonedChild to Optional.of(higherPriorityChild))
                processCancellationChecker.check()
            }
        }
    }
    return mappedNodes
}
