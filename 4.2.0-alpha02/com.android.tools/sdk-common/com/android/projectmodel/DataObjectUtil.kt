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
package com.android.projectmodel

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.memberProperties

internal fun <T : Any> getConstructor(clazz: KClass<T>): KFunction<T>? {
    val primaryConstructor = clazz.primaryConstructor
    if (primaryConstructor != null && primaryConstructor.visibility == KVisibility.PUBLIC) {
        return clazz.primaryConstructor
    }
    return clazz.constructors.firstOrNull { it.visibility == KVisibility.PUBLIC }
}

internal const val MAX_COLLECTION_SIZE = 10

/**
 * Prints the properties of the given Kotlin object. Any optional parameters that are equal to
 * the default values will be omitted. All non-optional properties included in [defaultValues]
 * will not be used, since non-optional properties are always printed. For this reason, defaultValues
 * should be filled in with the simplest-possible values for any non-optional attributes.
 *
 * This should not be used for recursive types (types which can transitively contain instances
 * of themself), since the output string for such types can grow arbitrarily large.
 */
internal fun <T : Any> printProperties(toPrint: T, defaultValues: T): String {
    val clazz = toPrint.javaClass.kotlin
    val parameters = getConstructor(clazz)?.parameters.orEmpty()
    val optionalParameters = parameters.filter { it.isOptional }.mapNotNull { it.name }.toSet()
    val parameterNames = parameters.map { it.name }.toSet()
    val propertyDescriptions = ArrayList<String>()
    for (prop in clazz.memberProperties) {
        if (prop.visibility != KVisibility.PUBLIC) {
            continue
        }
        val actualValue = prop.get(toPrint)
        if (parameterNames.contains(prop.name)
            && (!optionalParameters.contains(prop.name) || (prop.get(defaultValues) != actualValue))
        ) {

            val valueString =
                if (actualValue is Collection<*>) printCollection(actualValue) else actualValue.toString()
            propertyDescriptions.add("${prop.name}=$valueString")
        }
    }
    return propertyDescriptions.joinToString(",", clazz.simpleName + "(", ")")
}

internal fun printCollection(toPrint: Collection<*>): String =
    if (toPrint.size > MAX_COLLECTION_SIZE) {
        toPrint.take(MAX_COLLECTION_SIZE)
            .joinToString(",", "[", ",...${toPrint.size - MAX_COLLECTION_SIZE} more]")
    } else {
        toPrint.toString()
    }

