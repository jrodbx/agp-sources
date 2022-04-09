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

package com.android.manifmerger

import com.android.annotations.concurrency.Immutable
import com.google.common.collect.ImmutableList
import org.w3c.dom.Element

/**
 * Interface responsible for providing a key extraction capability from an element.
 * Some elements store their keys as an attribute, some as a sub-element attribute, some don't
 * have any key.
 */
@Immutable
internal interface NodeKeyResolver {

    /**
     * The attribute(s) used to store the xml element key, or null if element does not have a key.
     */
    val keyAttributesNames: ImmutableList<String>

    /**
     * Returns the key associated with this xml element.
     * @param element the element to get the key from
     * @return the key as a string to uniquely identify the element from similarly typed elements
     * in the document or null if there is no key.
     */
    fun getKey(element: Element): String?
}
