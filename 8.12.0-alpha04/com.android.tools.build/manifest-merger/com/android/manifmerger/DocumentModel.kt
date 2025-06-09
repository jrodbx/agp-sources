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

/**
 * An interface that provides strategy to merge XmlDocuments.
 *
 * @param T a type that represents different types of XmlElements, instances of that type
 *        define how XmlElements can be merged together
 */
internal interface DocumentModel<T> {
    /**
     * @return a XmlElement type based on its tag/name
     */
    fun fromXmlSimpleName(name: String): T

    /**
     * @return a tag/name of XmlElement based on its type
     */
    fun toXmlName(type: T): String

    // This should ideally be a property of AttributeModel
    fun autoRejectConflicts(): Boolean
}
