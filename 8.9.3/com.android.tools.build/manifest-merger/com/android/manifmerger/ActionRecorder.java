/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.manifmerger;

import static com.android.manifmerger.XmlNode.NodeKey;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records all the actions taken by the merging tool.
 * <p>
 * Each action generates at least one {@link com.android.manifmerger.Actions.Record}
 * containing enough information to generate a machine or human readable report.
 * <p>
 *
 * The records are not organized in a temporal structure as the merging tool takes such decisions
 * but are keyed by xml elements and attributes. For each node (elements or attributes), a linked
 * list of actions that happened to the node is recorded to display all decisions that were made
 * for that particular node.
 * <p>
 *
 * This structure will permit displaying logs with co-located decisions records for each element,
 * for instance :
 * <pre>
 * activity:com.foo.bar.MyApp
 *     Added from manifest.xml:31
 *     Rejected from lib1_manifest.xml:65
 * </pre>
 *
 * <p>
 * Each record for a node (element or attribute) will contain the following metadata :
 * <p>
 *
 * <ul>
 *     <li>{@link com.android.manifmerger.Actions.ActionType} to identify whether the action
 *     applies to an attribute or an element.</li>
 *     <li>{@link com.android.ide.common.blame.SourceFilePosition} to identify the source xml
 *     location for the node.</li>
 * </ul>
 *
 * <p>
 * Elements will also contain:
 * <ul>
 *     <li>Element name : a name composed of the element type and its key.</li>
 *     <li>{@link NodeOperationType} the highest priority tool annotation justifying the merging
 *     tool decision.</li>
 * </ul>
 *
 * <p>
 * While attributes will have:
 * <ul>
 *     <li>element name</li>
 *     <li>attribute name : the namespace aware xml name</li>
 *     <li>{@link AttributeOperationType} the highest priority annotation justifying the merging
 *     tool decision.</li>
 * </ul>
 */
public class ActionRecorder {

    /**
     * Defines all the records for the merging tool activity, indexed by element name+key. Iterator
     * should be ordered by the key insertion order.
     *
     * <p>This is not a concurrent map, so we will need to guard multi-threaded access when
     * adding/removing elements.
     */
    @GuardedBy("this")
    @NonNull
    private final Map<NodeKey, Actions.DecisionTreeRecord> mRecords =
            new LinkedHashMap<NodeKey, Actions.DecisionTreeRecord>();

    /**
     * Record an {@link com.android.manifmerger.Actions.ActionType.ADDED} action for an unrecorded
     * XmlElement and its unrecorded descendants. If exhaustiveSearch is true, we check all the
     * XmlElement's descendants; if it's false, we stop searching through an XmlElement's
     * descendants if that XmlElement has already been recorded.
     *
     * <p>When the first xml file is loaded, there is nothing to merge with, however, each xml
     * element and attribute added to the initial merged file need to be recorded.
     *
     * @param xmlElement XmlElement added to the merged document.
     * @param exhaustiveSearch whether to check an already recorded XmlElement's descendants
     */
    synchronized void recordAddedNodeAction(
            @NonNull XmlElement xmlElement, boolean exhaustiveSearch) {
        boolean nodeRecorded = !getDecisionTreeRecord(xmlElement).getNodeRecords().isEmpty();
        if (!nodeRecorded) {
            recordNodeAction(xmlElement, Actions.ActionType.ADDED);
        } else if (!exhaustiveSearch) {
            return;
        }
        for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {
            // check !nodeRecorded first because if it's true, we don't have
            // to call getAttributeCreationRecord(xmlAttribute)
            if (!nodeRecorded || getAttributeCreationRecord(xmlAttribute) == null) {
                recordAttributeAction(
                        xmlAttribute,
                        Actions.ActionType.ADDED,
                        xmlElement.getAttributeOperationType(xmlAttribute.getName()));
            }
        }
        for (XmlElement childNode : xmlElement.getMergeableElements()) {
            recordAddedNodeAction(childNode, exhaustiveSearch);
        }
    }

    /**
     * Record a node that was added due to an implicit presence in earlier SDK release but requires
     * an explicit declaration in the application targeted SDK.
     * @param xmlElement the implied element that was added to the resulting xml.
     * @param reason optional contextual information whey the implied element was added.
     */
    synchronized void recordImpliedNodeAction(@NonNull XmlElement xmlElement, @Nullable String reason) {
        Actions.DecisionTreeRecord nodeDecisionTree = getDecisionTreeRecord(xmlElement);
        Actions.NodeRecord record = new Actions.NodeRecord(Actions.ActionType.IMPLIED,
                new SourceFilePosition(
                        xmlElement.getDocument().getSourceFile(),
                        xmlElement.getDocument().getRootNode().getPosition()),
                xmlElement.getOriginalId(),
                reason,
                xmlElement.getOperationType()
        );
        nodeDecisionTree.addNodeRecord(record);
    }

    /**
     * Record a node action taken by the merging tool.
     *
     * @param xmlElement the action's target xml element
     * @param actionType the action's type
     */
    synchronized void recordNodeAction(
            @NonNull XmlElement xmlElement,
            @NonNull Actions.ActionType actionType) {
        recordNodeAction(xmlElement, actionType, xmlElement);
    }

    /**
     * Record a node action taken by the merging tool.
     *
     * @param mergedElement the merged xml element
     * @param actionType    the action's type
     * @param targetElement the action's target when the action is rejected or replaced, it
     *                      indicates what is the element being rejected or replaced.
     */
    synchronized void recordNodeAction(
            @NonNull XmlElement mergedElement,
            @NonNull Actions.ActionType actionType,
            @NonNull XmlElement targetElement) {

        Actions.NodeRecord record = new Actions.NodeRecord(actionType,
                new SourceFilePosition(
                        targetElement.getDocument().getSourceFile(),
                        targetElement.getPosition()),
                targetElement.getOriginalId(),
                null, /* reason */
                mergedElement.getOperationType()
        );
        recordNodeAction(mergedElement, record);
    }

    /**
     * Records a {@link com.android.manifmerger.Actions.NodeRecord} action on a xml element.
     * @param mergedElement the target element of the action.
     * @param nodeRecord the record of the action.
     */
    synchronized void recordNodeAction(
            @NonNull XmlElement mergedElement,
            @NonNull Actions.NodeRecord nodeRecord) {
        Actions.DecisionTreeRecord nodeDecisionTree = getDecisionTreeRecord(mergedElement);
        nodeDecisionTree.addNodeRecord(nodeRecord);
        updateRecordsIfNodeKeyChanged(mergedElement);
    }

    @NonNull
    private synchronized Actions.DecisionTreeRecord getDecisionTreeRecord(
            @NonNull XmlElement xmlElement) {
        return mRecords.computeIfAbsent(
                xmlElement.getOriginalId(), k -> new Actions.DecisionTreeRecord());
    }

    /**
     * Records an attribute action taken by the merging tool
     *
     * @param attribute the attribute in question.
     * @param actionType the action's type
     * @param attributeOperationType the original tool annotation leading to the merging tool
     *     decision.
     */
    synchronized void recordAttributeAction(
            @NonNull XmlAttribute attribute,
            @NonNull Actions.ActionType actionType,
            @Nullable AttributeOperationType attributeOperationType) {

        recordAttributeAction(
                attribute, attribute.getPosition(), actionType, attributeOperationType);
    }

    /**
     * Records an attribute action taken by the merging tool
     *
     * @param attribute              the attribute in question.
     * @param attributePosition      the attribute's position.
     * @param actionType             the action's type
     * @param attributeOperationType the original tool annotation leading to the merging tool
     *                               decision.
     */
    synchronized void recordAttributeAction(
            @NonNull XmlAttribute attribute,
            @NonNull SourcePosition attributePosition,
            @NonNull Actions.ActionType actionType,
            @Nullable AttributeOperationType attributeOperationType) {

        XmlElement originElement = attribute.getOwnerElement();
        Actions.AttributeRecord attributeRecord = new Actions.AttributeRecord(
                actionType,
                new SourceFilePosition(
                        originElement.getDocument().getSourceFile(),
                        attributePosition),
                attribute.getOriginalId(),
                null, /* reason */
                attributeOperationType
        );
        recordAttributeAction(attribute, attributeRecord);
    }

    /**
     * Record a {@link com.android.manifmerger.Actions.AttributeRecord} action for an attribute of
     * an xml element.
     * @param attribute the attribute in question.
     * @param attributeRecord the record of the action.
     */
    synchronized void recordAttributeAction(
            @NonNull XmlAttribute attribute,
            @NonNull Actions.AttributeRecord attributeRecord) {

        List<Actions.AttributeRecord> attributeRecords = getAttributeRecords(attribute);
        attributeRecords.add(attributeRecord);
    }

    /**
     * Records when a default value that should be merged was rejected due to a tools:replace
     * annotation.
     *
     * @param attribute              the attribute which default value was ignored.
     * @param implicitAttributeOwner the element owning the implicit default value.
     */
    synchronized void recordImplicitRejection(
            @NonNull XmlAttribute attribute,
            @NonNull XmlElement implicitAttributeOwner) {

        List<Actions.AttributeRecord> attributeRecords = getAttributeRecords(attribute);
        Actions.AttributeRecord attributeRecord = new Actions.AttributeRecord(
                Actions.ActionType.REJECTED,
                new SourceFilePosition(
                        implicitAttributeOwner.getDocument().getSourceFile(),
                        implicitAttributeOwner.getPosition()),
                attribute.getOriginalId(),
                null, /* reason */
                AttributeOperationType.REPLACE
        );
        attributeRecords.add(attributeRecord);
    }

    /**
     * Adds the xmlElement's current node key to the map of NodeKeys to DecisionTreeRecords, if the
     * node key has changed.
     *
     * @param xmlElement the xmlElement whose calculated node key may have changed from its original
     *     node key.
     */
    private synchronized void updateRecordsIfNodeKeyChanged(@NonNull XmlElement xmlElement) {
        NodeKey originalNodeKey = xmlElement.getOriginalId();
        // by now the original NodeKey should have been added for this element.
        Preconditions.checkState(
                mRecords.containsKey(originalNodeKey), "No record for key [%s]", originalNodeKey);
        mRecords.putIfAbsent(xmlElement.getId(), mRecords.get(originalNodeKey));
    }

    /**
     * Returns the record for an attribute creation event. The attribute is "created" when it is
     * added for the first time into the resulting merged xml document.
     */
    @Nullable
    synchronized Actions.AttributeRecord getAttributeCreationRecord(
            @NonNull XmlAttribute attribute) {
        for (Actions.AttributeRecord attributeRecord : getAttributeRecords(attribute)) {
            if (attributeRecord.getActionType() == Actions.ActionType.ADDED) {
                return attributeRecord;
            }
        }
        return null;
    }

    @NonNull
    private synchronized List<Actions.AttributeRecord> getAttributeRecords(@NonNull XmlAttribute attribute) {
        XmlElement originElement = attribute.getOwnerElement();
        NodeKey storageKey = originElement.getOriginalId();
        @Nullable Actions.DecisionTreeRecord nodeDecisionTree = mRecords.get(storageKey);
        // by now the node should have been added for this element.
        Preconditions.checkNotNull(nodeDecisionTree, "No record for key [%s]", storageKey);
        List<Actions.AttributeRecord> attributeRecords =
                nodeDecisionTree.mAttributeRecords.computeIfAbsent(
                        attribute.getName(), k -> new ArrayList<>());
        return attributeRecords;
    }

    @NonNull
    synchronized Actions build() {
        return new Actions(ImmutableMap.copyOf(mRecords));
    }
}
