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

import com.android.aapt.Resources.XmlNode
import com.android.build.shrinker.ResourceShrinkerModel
import com.android.ide.common.resources.usage.ResourceUsageModel
import java.nio.file.Files
import java.nio.file.Path

/**
 * Records resource usages from AndroidManifest.xml in proto compiled format.
 *
 * @param manifest path to AndroidManifest.xml file.
 */
class ProtoAndroidManifestUsageRecorder(private val manifest: Path) : ResourceUsageRecorder {

    override fun recordUsages(model: ResourceShrinkerModel) {
        val root = XmlNode.parseFrom(Files.readAllBytes(manifest))
        recordUsagesFromNode(root, model)
    }

    private fun recordUsagesFromNode(node: XmlNode, model: ResourceShrinkerModel) {
        // Records only resources from element attributes that have reference items with resolved
        // ids or names.
        if (!node.hasElement()) {
            return
        }
        node.element.attributeList.asSequence()
            .filter { it.hasCompiledItem() }
            .map { it.compiledItem }
            .filter { it.hasRef() }
            .map { it.ref }
            .flatMap {
                // If resource id is available prefer this id to name.
                when {
                    it.id != 0 -> listOfNotNull(model.resourceStore.getResource(it.id))
                    else -> model.resourceStore.getResourcesFromUrl("@${it.name}")
                }.asSequence()
            }
            .forEach { ResourceUsageModel.markReachable(it) }
        node.element.childList.forEach { recordUsagesFromNode(it, model) }
    }
}
