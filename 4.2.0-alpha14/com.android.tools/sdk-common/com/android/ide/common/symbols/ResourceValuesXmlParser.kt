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

@file:JvmName("ResourceValuesXmlParser")

package com.android.ide.common.symbols

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX
import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX_LEN
import com.android.SdkConstants.TAG_EAT_COMMENT
import com.android.resources.ResourceType
import com.android.utils.XmlUtils.toXml
import com.google.common.collect.ImmutableList
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.ArrayList

/**
 * Parser method that can read a [SymbolTable] from a resource XML file. Resource XML files contain
 * zero or multiple resources of the following types:
 *
 * <table>
 *     <caption>Types of resources</caption>
 *     <tr><th>Type         </th><th>XML Tag (*1)       </th><th>Symbol Type    </th><th>Java Type</th></tr>
 *     <tr><td>Animation    </td><td>`anim`             </td><td>`anim`         </td><td>`int`    </td></tr>
 *     <tr><td>Animator     </td><td>`animator`         </td><td>`animator`     </td><td>`int`    </td></tr>
 *     <tr><td>Attribute    </td><td>`attr`             </td><td>`attr`         </td><td>`int`    </td></tr>
 *     <tr><td>Boolean      </td><td>`bool`             </td><td>`bool`         </td><td>`int`    </td></tr>
 *     <tr><td>Color        </td><td>`color`            </td><td>`color`        </td><td>`int`    </td></tr>
 *     <tr><td>Dimension    </td><td>`dimen`            </td><td>`dimen`        </td><td>`int`    </td></tr>
 *     <tr><td>Drawable     </td><td>`drawable`         </td><td>`drawable`     </td><td>`int`    </td></tr>
 *     <tr><td>Enumeration  </td><td>`enum`             </td><td>`id`           </td><td>`int`    </td></tr>
 *     <tr><td>Fraction     </td><td>`fraction`         </td><td>`fraction`     </td><td>`int`    </td></tr>
 *     <tr><td>ID           </td><td>`id`               </td><td>`id`           </td><td>`int`    </td></tr>
 *     <tr><td>Integer      </td><td>`integer`          </td><td>`integer`      </td><td>`int`    </td></tr>
 *     <tr><td>Integer Array</td><td>`integer-array`    </td><td>`array`        </td><td>`int`    </td></tr>
 *     <tr><td>Menu         </td><td>`menu`             </td><td>`menu`         </td><td>`int`    </td></tr>
 *     <tr><td>MipMap       </td><td>`mipmap`           </td><td>`mipmap`       </td><td>`int`    </td></tr>
 *     <tr><td>Plural       </td><td>`plurals`          </td><td>`plurals`      </td><td>`int`    </td></tr>
 *     <tr><td>Raw          </td><td>`raw`              </td><td>`raw`          </td><td>`int`    </td></tr>
 *     <tr><td>String       </td><td>`string`           </td><td>`string`       </td><td>`int`    </td></tr>
 *     <tr><td>String Array </td><td>`string-array`     </td><td>`array`        </td><td>`int`    </td></tr>
 *     <tr><td>Style        </td><td>`style`            </td><td>`style`        </td><td>`int`    </td></tr>
 *     <tr><td>Styleable    </td><td>`declare-styleable`</td><td>`styleable`(*2)</td><td>`int[]`  </td></tr>
 *     <tr><td>Transition   </td><td>`transition`       </td><td>`transition`   </td><td>`int`    </td></tr>
 *     <tr><td>Typed Array  </td><td>`array`            </td><td>`array`        </td><td>`int`    </td></tr>
 *     <tr><td>XML          </td><td>`xml`              </td><td>`xml`          </td><td>`int`    </td></tr>
 * </table>
 *
 *
 * (*1) Resources can be also declared in an extended form where the XML Tag is `item` and the
 * attribute `type` specifies whether the resource is an `attr`, a `string` et cetera. Therefore a
 * construction like this:
 *
 * <pre>
 * <resources>
 *     <declare-styleable name="PieChart">
 *         <attr name="showText" format="boolean" />
 *         <attr name="labelPosition" format="enum">
 *             <enum name="left" value="0"/>
 *             <enum name="right" value="1"/>
 *         </attr>
 *     </declare-styleable>
 * </resources>
 * </pre>
 *
 *
 * Is equal to the following construction that uses `item` tag:
 *
 * <pre>
 * <resources>
 *     <item type="styleable" name="PieChart">
 *         <item type="attr" name="showText" format="boolean" />
 *         <item type="attr" name="labelPosition" format="enum">
 *             <item type="enum" name="left" value="0"/>
 *             <item type="enum" name="right" value="1"/>
 *         </item>
 *     </item>
 * </resources>
 * </pre>
 *
 *
 * It is also worth noting that some resources can be declared with a prefix like `aapt:` or
 * `android:`. Following aapt's original behaviour, we strip the type names from those prefixes.
 * This behaviour is deprecated and might be the support for it might end in the near future.
 *
 *
 * (*2)The mapping of `declare-styleable` to symbols is complex. For each styleable, a symbol of
 * resource type `styleable` is created of java type `int[]`. For each attribute (`attr`) in the
 * `declare-styleable` a child of that parent is created and added under that symbol as well as a
 * symbol of resource type `attr` with java type `int`. In case of the symbol with `styleable` type,
 * its name is the symbol name of the `declare-styleable` element joined with the name of the `attr`
 * element by an underscore character. The value of the int array in the `declare-styleable`
 * contains the IDs of all `styleable` symbols. So, for example, the following XML:
 *
 * <pre>
 * <resources>
 *     <declare-styleable name="abc">
 *         <attr name="def" format="boolean"/>
 *         <attr name="ghi" format="int"/>
 *     </declare-styleable>
 * </resources>
 * </pre>
 *
 *
 * Will generate the following `R.java`:
 *
 * <pre>
 * class R {
 *     class attr {
 *         public static int def = 0x7f040001;
 *         public static int ghi = 0x7f040002;
 *     }
 *     class styleable {
 *         public static int[] abc = { 0x7f040001, 0x7f040002 };
 *         public static int abc_def = 0;
 *         public static int abc_ghi = 1;
 *     }
 * }
 * </pre>
 * Constructs a [SymbolTable] from the given parsed XML document. The values for the resource are
 * drawn from the given provider. For testing purposes, this method guarantees that IDs are assigned
 * in the order the resources are provided in the XML document. However, this guarantee is only to
 * make testing simpler and non-test code should not rely on this assumption as it may change, along
 * with the required refactoring of the test code.
 *
 * @param xmlDocument the parsed XML document
 * @param idProvider the provider for IDs to assign to the resources
 * @param platformAttrSymbols the platform attr symbols
 * @return the symbols for all resources in the document
 */
fun parseValuesResource(
        xmlDocument: Document,
        idProvider: IdProvider,
        platformAttrSymbols: SymbolTable?,
        validation: Boolean = true
): SymbolTable {
    val root = xmlDocument.documentElement ?:
            throw ResourceValuesXmlParseException("XML document does not have a root element.")

    if (root.tagName != "resources") {
        throw ResourceValuesXmlParseException("XML document root is not 'resources'")
    }

    if (root.namespaceURI != null) {
        throw ResourceValuesXmlParseException("XML document root has a namespace")
    }

    val builder = SymbolTable.builder()
    val enumSymbols = ArrayList<Symbol>()

    var current: Node? = root.firstChild
    while (current != null) {
        if (current.nodeType == Node.ELEMENT_NODE) {
            parseChild(
                    (current as Element?)!!,
                    builder,
                    idProvider,
                    enumSymbols,
                    platformAttrSymbols,
                    validation)
        }
        current = current.nextSibling
    }

    for (enumSymbol in enumSymbols) {
        if (!builder.contains(enumSymbol)) {
            builder.add(enumSymbol)
        }
    }
    return builder.build()
}

/**
 * Parses a single child element from the main XML document.
 *
 * @param child the element to be parsed
 * @param builder the builder for the SymbolTable
 * @param idProvider the provider for IDs to assign to the resources
 * @param enumSymbols out list of enum symbols discovered in declare-styleable
 * @param platformAttrSymbols the platform attr symbols
 * @param validation check the `name` of the symbol is a valid resource name
 */
private fun parseChild(
        child: Element,
        builder: SymbolTable.Builder,
        idProvider: IdProvider,
        enumSymbols: MutableList<Symbol>,
        platformAttrSymbols: SymbolTable?,
        validation: Boolean = true) {
    if (child.tagName == SdkConstants.TAG_EAT_COMMENT) {
        return
    }

    val resourceType = ResourceType.fromXmlTag(child)
                       ?: throw ResourceValuesXmlParseException("Unknown resource value XML element '${toXml(child)}'")

    if (resourceType == ResourceType.PUBLIC || resourceType == ResourceType.OVERLAYABLE) {
        // Doesn't declare a resource.
        return
    }

    val name = getMandatoryAttr(child, "name")

    when (resourceType) {
        ResourceType.ANIM,
        ResourceType.ANIMATOR,
        ResourceType.ARRAY,
        ResourceType.BOOL,
        ResourceType.COLOR,
        ResourceType.DIMEN,
        ResourceType.DRAWABLE,
        ResourceType.FONT,
        ResourceType.FRACTION,
        ResourceType.ID,
        ResourceType.INTEGER,
        ResourceType.INTERPOLATOR,
        ResourceType.LAYOUT,
        ResourceType.MENU,
        ResourceType.MIPMAP,
        ResourceType.PLURALS,
        ResourceType.RAW,
        ResourceType.STRING,
        ResourceType.STYLE,
        ResourceType.TRANSITION,
        ResourceType.XML ->
            builder.add(Symbol.createSymbol(resourceType, name, idProvider, validation))
        ResourceType.STYLEABLE ->
            // We also need to find all the attributes declared under declare styleable.
            parseDeclareStyleable(
                    child, idProvider, name, builder, enumSymbols, platformAttrSymbols, validation)
        ResourceType.ATTR ->
            // We also need to find all the enums declared under attr (if there are any).
            parseAttr(child, idProvider, name, builder, enumSymbols, false)
        else -> throw ResourceValuesXmlParseException(
                "Unknown resource value XML element '${toXml(child)}'")
    }
}

/**
 * Parses a declare styleable element and finds all it's `attr` children to create new Symbols for
 * each them: a `styleable` Symbol with the name which is a concatenation of the declare styleable's
 * name, an underscore and the child's name; and a `attr` Symbol with the name equal to the child's
 * name.
 *
 * @param declareStyleable the declare styleable element we are parsing
 * @param idProvider the provider for IDs to assign to the resources
 * @param styleableName name of the declare styleable element
 * @param builder the builder for the SymbolTable
 * @param enumSymbols out list of enum symbols discovered in declare-styleable
 * @param platformAttrSymbols the platform attr symbols
 */
private fun parseDeclareStyleable(
        declareStyleable: Element,
        idProvider: IdProvider,
        styleableName: String,
        builder: SymbolTable.Builder,
        enumSymbols: MutableList<Symbol>,
        platformAttrSymbols: SymbolTable?,
        validation: Boolean = true) {
    val attrNames = ImmutableList.Builder<String>()
    val attrValues = ImmutableList.builder<Int>()

    var attrNode: Node? = declareStyleable.firstChild
    while (attrNode != null) {
        if (attrNode.nodeType != Node.ELEMENT_NODE) {
            attrNode = attrNode.nextSibling
            continue
        }

        val attrElement = attrNode as Element?
        var tagName = attrElement!!.tagName
        if (tagName == SdkConstants.TAG_ITEM) {
            tagName = attrElement.getAttribute(SdkConstants.ATTR_TYPE)
        }

        if (tagName != ResourceType.ATTR.getName() || attrElement.namespaceURI != null) {
            if (tagName == TAG_EAT_COMMENT) {
                attrNode = attrNode.nextSibling
                continue
            }

            throw ResourceValuesXmlParseException(
                    "Illegal type under declare-styleable: was <$tagName>, only accepted is " +
                            "<attr>")
        }

        val attrName = getMandatoryAttr(attrElement, "name")
        val attrValue = if (attrName.startsWith(ANDROID_NS_NAME_PREFIX)) {
            if (platformAttrSymbols == null) {
                // If platform attr symbols are not provided, we don't need the actual values.
                // Use a fake ID to signal this is the case.
                -1
            } else {
                // this is an android attr.
                val realAttrName = attrName.substring(ANDROID_NS_NAME_PREFIX_LEN)

                val attrSymbol =
                    platformAttrSymbols.symbols.get(ResourceType.ATTR, realAttrName)
                            ?: throw ResourceValuesXmlParseException(
                                "Unknown android attribute '$attrName' under '$styleableName"
                            )

                attrSymbol.intValue
            }
        } else {
            parseAttr(attrElement, idProvider, attrName, builder, enumSymbols, true, validation)
        }
        attrNames.add(attrName)
        attrValues.add(attrValue)
        attrNode = attrNode.nextSibling
    }
    builder.add(
            Symbol.createStyleableSymbol(
                styleableName,
                attrValues.build(),
                attrNames.build(),
                validation
            ))
}

/**
 * Parses an attribute element and finds all it's `enum` children to create new Symbols for each of
 * them: an `id` Symbol with the name equal to the child's name.
 *
 * @param attr the declare styleable element we are parsing
 * @param idProvider the provider for IDs to assign to the resources
 * @param name name of the attr element
 * @param builder the builder for the SymbolTable
 * @return the symbol value of the parsed attribute
 */
private fun parseAttr(
        attr: Element,
        idProvider: IdProvider,
        name: String,
        builder: SymbolTable.Builder,
        enumSymbols: MutableList<Symbol>,
        isMaybeDefinition: Boolean,
        validation: Boolean = true): Int {
    var enumNode: Node? = attr.firstChild
    while (enumNode != null) {
        if (enumNode.nodeType != Node.ELEMENT_NODE) {
            enumNode = enumNode.nextSibling
            continue
        }

        val enumElement = enumNode as Element?
        var tagName = enumElement!!.tagName
        if (tagName == SdkConstants.TAG_ITEM) {
            tagName = enumElement.getAttribute(SdkConstants.ATTR_TYPE)
        }

        if (tagName != SdkConstants.TAG_ENUM || enumElement.namespaceURI != null) {
            // We only care about enums. If there is a different tag (e.g. "flag") we ignore it.
            enumNode = enumNode.nextSibling
            continue
        }

        val newEnum = Symbol.createSymbol(
                ResourceType.ID,
                getMandatoryAttr(enumElement, "name"),
                idProvider,
                validation = validation)

        enumSymbols.add(newEnum)
        enumNode = enumNode.nextSibling
    }

    val newAttr = Symbol.createSymbol(
        ResourceType.ATTR, name, idProvider, isMaybeDefinition, validation)

    if (!builder.contains(newAttr)) {
        // If we haven't encountered this attribute yet, add the new one.
        builder.add(newAttr)
        return newAttr.intValue
    } else if (!isMaybeDefinition
        && (builder[newAttr] as Symbol.AttributeSymbol).isMaybeDefinition) {
        // We already have the attribute, but it was defined under a declare-styleable. Replace it
        // with a real definition. Keep the ID of the previous definition.
        val old = builder.remove(newAttr.resourceType, newAttr.canonicalName)!!
        builder.add(Symbol.attributeSymbol(newAttr.canonicalName, old.intValue, false))
    }

    // Otherwise use existing attribute.
    return builder[newAttr]!!.intValue
}

/**
 * Obtains an attribute in an element that must exist.
 *
 * @param element the XML element
 * @param attrName the attribute name
 * @return the attribute value
 * @throws ResourceValuesXmlParseException the attribute does not exist
 */
private fun getMandatoryAttr(element: Element, attrName: String): String {
    val attr = element.getAttributeNodeNS(null, attrName) ?:
            throw ResourceValuesXmlParseException(
                    "Element '${element.tagName}' should have attribute '$attrName'")
    return attr.value
}
