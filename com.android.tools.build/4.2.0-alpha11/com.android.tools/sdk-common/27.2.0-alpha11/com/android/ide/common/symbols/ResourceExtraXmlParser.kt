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

@file:JvmName("ResourceExtraXmlParser")

package com.android.ide.common.symbols

import com.android.SdkConstants
import com.android.resources.ResourceType
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * A parser method used for finding all inline declaration of android resources in XML files.
 *
 * Apart from placing files into sub-directories of the resource directory and declaring them in XML
 * files inside the `values` directory, `id` resources can also be lazily declared in other XML
 * files in non-values directories.
 *
 * For example, inside `layout/main_activity.xml` we could have a line such as:
 *
 * <pre>
 * `android:id="@+id/activity_main"`
 * </pre>
 *
 * This construction is an example of a inline declaration of a resource with the type `id` and name
 * `activity_main`. Even though it is not declared inside a `values` directory, it still needs to be
 * parsed and processed into a Symbol:
 *
 * <table>
 *     <caption>Parsing result</caption>
 *     <tr><th>Java type  </th><th>Resource type  </th><th>Resource name    </th><th>ID</th></tr>
 *     <tr><td>int        </td><td>id             </td><td>activity_main    </td><td>0 </td></tr>
 * </table>
 *
 * It is also worth noting that some resources can be declared with a prefix like `aapt:` or
 * `android:`. Following aapt's original behaviour, we strip the type names from those prefixes.
 * This behaviour is deprecated and might be the support for it might end in the near future.
 *
 * This method finds all constructions of type <@code '"@+id/name"'> in the given file and returns
 * a [SymbolTable] containing these symbols.
 *
 * @param xmlDocument an xml file to parse
 * @param idProvider the provider for IDs to assign to the resources
 * @return the symbols for all resources in the file
 */
fun parseResourceForInlineResources(xmlDocument: Document, idProvider: IdProvider): SymbolTable {
    val root = xmlDocument.documentElement ?:
            throw ResourceValuesXmlParseException("XML document does not have a root element.")
    val builder = SymbolTable.builder()
    parseChild(root, builder, idProvider)

    return builder.build()
}

/**
 * Parses an Element in search of lazy resource declarations and afterwards parses the Element's
 * children recursively.
 */
private fun parseChild(
        element: Element,
        builder: SymbolTable.Builder,
        idProvider: IdProvider) {

    // Check if the node contains any lazy resource declarations.
    val attrs = element.attributes
    for (i in 0 until attrs.length) {
        val attr = attrs.item(i)
        checkForResources((attr as Attr).value, builder, idProvider)
    }

    // Parse all of the Element's children as well, in case they contain lazy declarations.
    var current: Node? = element.firstChild
    while (current != null) {
        if (current.nodeType == Node.ELEMENT_NODE) {
            parseChild((current as Element?)!!, builder, idProvider)
        }
        current = current.nextSibling
    }
}

/**
 * Checks whether a given text is a lazy declaration of type "@+id/name". If it is, changes it into
 * a new Symbol and adds it into the SymbolTable builder.
 */
private fun checkForResources(
        text: String?,
        builder: SymbolTable.Builder,
        idProvider: IdProvider) {
    if (text != null && text.startsWith(SdkConstants.NEW_ID_PREFIX)) {

        val name = text.substring(SdkConstants.NEW_ID_PREFIX.length, text.length)
        val newSymbol = Symbol.createAndValidateSymbol(
                ResourceType.ID,
                name,
                idProvider)
        if (!builder.contains(newSymbol)) {
            builder.add(newSymbol)
        }
    }
}
