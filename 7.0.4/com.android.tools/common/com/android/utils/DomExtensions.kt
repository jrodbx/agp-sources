/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("DomExtensions")

package com.android.utils

import org.w3c.dom.Element
import org.w3c.dom.Node

// Extension methods to make operating on DOM more convenient

/**
 * Adds an iterable implementation on Element such that we can use it in a for loop to iterate
 * over the children
 */
operator fun Element.iterator(): Iterator<Element> {
    return object : Iterator<Element> {
        var next = findNextElement(firstChild)

        private fun findNextElement(node: Node?): Element? {
            var curr = node
            while (curr != null) {
                if (curr is Element) {
                    return curr
                }
                curr = curr.nextSibling
            }

            return null
        }

        override fun hasNext(): Boolean {
            return next != null
        }

        override fun next(): Element {
            val result = next
            next = findNextElement(next?.nextSibling)
            return result ?: error("hasNext is false")
        }
    }
}

/** Returns the first sub tag child of this element with the given tag name */
fun Element.subtag(tag: String): Element? {
    return XmlUtils.getFirstSubTagByName(this, tag)
}

/** Returns the next tag sibling of this element */
fun Element.next(): Element? {
    return XmlUtils.getNextTag(this)
}

/** Returns the next tag sibling of this element with the given tag name */
fun Element.next(tag: String): Element? {
    return XmlUtils.getNextTagByName(this, tag)
}

/** Provides an iterator for the sub tags of this element with the given tag */
fun Element.subtags(tag: String): Iterator<Element> {
    return XmlUtils.getSubTagsByName(this, tag).iterator()
}

/** Returns the number of subtags of this element */
fun Element.subtagCount(): Int {
    return XmlUtils.getSubTagCount(this)
}

/** Returns the text content of this element */
fun Element.text(): String {
    return textContent
}
