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

package com.android.resources

import com.android.utils.forEach
import org.w3c.dom.Node

/**
 * A utility to rewrite resource references in Xml file with the fully qualified names once
 * including package names
 *
 * @param localPackage name of local package
 * @param packageProvider a functor that provides name of a package based on reference type and name
 */
class NamespaceReferenceRewriter(
    private val localPackage: String,
    private val packageProvider: (type: String, name: String) -> String) {

    /**
     *  Method that rewrites references in a manifest file
     *
     *  @param node a root node of a manifest xml document
     *  @param localLib shows if the reference is for local library (not remote). In this case we
     *        should not allow private resources access
     */
    fun rewriteManifestNode(node: Node, localLib: Boolean) {
        if (node.nodeType == Node.ATTRIBUTE_NODE) {
            // The content could be a resource reference. If it is not, do not update the content.
            // Even if it resolves the resource to it's own package, we need to keep the full
            // namespace reference since the manifests get merged at app level.
            val content = node.nodeValue
            val (namespacedContent, _) = rewritePossibleReference(content, true, localLib)
            if (content != namespacedContent) {
                node.nodeValue = namespacedContent
            }
        }

        // First fix the attributes.
        node.attributes?.forEach {
            rewriteManifestNode(it, localLib)
        }

        // Now fix the children.
        node.childNodes?.forEach {
            rewriteManifestNode(it, localLib)
        }
    }

    fun rewriteManifestNode(node: Node) = rewriteManifestNode(node, false)

    /** A pair of reference and it package (if any, otherwise empty string) */
    data class RewrittenReference(val content: String, val pckg: String = "")

    /**
     * Method that converts a reference content to the one with fully qualified namespaces
     *
     * @param content the content of a reference
     * @param writeLocalPackage if a local namespace should also be expanded
     * @param localLib shows if the reference is for local library (not remote). In this case we
     *        should not allow private resources access
     *
     * @return a converted reference
     */
    fun rewritePossibleReference(
        content: String,
        writeLocalPackage: Boolean = false,
        localLib: Boolean = false
    ): RewrittenReference {
        if (!content.startsWith("@") && !content.startsWith("?")) {
            // Not a reference, don't rewrite it.
            return RewrittenReference(content)
        }
        if (!content.contains("/")) {
            // Not a reference, don't rewrite it.
            return RewrittenReference(content)
        }
        if (content.startsWith("@+")) {
            // ID declarations are inheritently local, don't rewrite it.
            return RewrittenReference(content)
        }
        if (content.contains(':')) {
            // The reference is already namespaced (probably @android:...), don't rewrite it.
            return RewrittenReference(content)
        }

        val trimmedContent = content.trim()
        val type = trimmedContent.substringBefore('/').drop(1)
        val name = trimmedContent.substringAfter('/')

        val pckg = packageProvider(type, name)

        // Make all regular references (not '?') able to access private resources if it is not local
        // reference
        val prefixChar = if (content.startsWith('@')) {
            if (pckg == localPackage && localLib) {
                "@"
            } else {
                "@*"
            }
        } else {
            "?"
        }

        // Normally we don't want to add the local package to the resource reference (unless we're
        // rewriting the Manifest or it's an XML namespace) since it increases the size of the file.
        if (!writeLocalPackage && pckg == localPackage) {
            return RewrittenReference(content)
        }

        // Rewrite the reference using the package and the un-canonicalized name.
        return RewrittenReference("$prefixChar$pckg:$type/$name", pckg)
    }
}
