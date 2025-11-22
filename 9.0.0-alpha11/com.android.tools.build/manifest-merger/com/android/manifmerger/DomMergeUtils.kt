/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.manifmerger.ManifestMerger2.MergeFailureException
import com.android.manifmerger.DomMergeUtils
import com.android.utils.Pair
import com.google.common.base.Predicate
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.lang.Exception

object DomMergeUtils {
    /**
     * Clones and transforms an XML document.
     *
     * @return a pair of document and flag on whether new document is differ from original one.
     */
    @JvmStatic
    @Throws(MergeFailureException::class)
    fun cloneAndTransform(
        document: Document, transform: Predicate<Node?>, shouldRemove: Predicate<Node?>
    ): Pair<Document, Boolean> {
        return try {
            val newDocument = document.cloneNode(false) as Document
            var changeFlag = false
            var child = document.firstChild
            while (child != null) {
                val response = cloneNode(child, newDocument, transform, shouldRemove)
                changeFlag = changeFlag or response.second
                if (response.first != null) newDocument.appendChild(response.first)
                child = child.nextSibling
            }
            Pair.of(newDocument, changeFlag)
        } catch (e: Exception) {
            throw MergeFailureException(e)
        }
    }

    /**
     * Function goes through dom tree in recursive fashion, skipping nodes per predicate
     * `shouldRemove`, and apply transformation to all others.
     *
     * @param node - current node to transform or remove
     * @param newDocument represents cloned document.
     * @param transform - function that may transform node. Returns true if transformation happened
     * @param shouldRemove - predicate returns true if need to skip element and all its children
     * @return Pair of cloned node and a flag. Flag shows whether tree was changed. Node can be null
     * when shouldRemove returns true flagging not to clone this node.
     */
    private fun cloneNode(
        node: Node,
        newDocument: Document,
        transform: Predicate<Node?>,
        shouldRemove: Predicate<Node?>
    ): Pair<Node?, Boolean> {
        if (!shouldRemove.test(node)) {
            val clone = newDocument.importNode(node, false)
            var changeFlag = transform.apply(clone)
            var child = node.firstChild
            while (child != null) {
                val response = cloneNode(child, newDocument, transform, shouldRemove)
                changeFlag = changeFlag or response.second
                if (response.first != null) clone.appendChild(response.first)
                child = child.nextSibling
            }
            return Pair.of(clone, changeFlag)
        }
        return Pair.of(null, true)
    }
}
