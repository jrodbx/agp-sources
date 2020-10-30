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

package com.android.build.gradle.internal.dsl

import com.android.builder.errors.IssueReporter
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Factory for kotlin delegate properties for use in the new DSL implementations.
 *
 * The properties can be locked in afterEvaluate to prevent attempts to change values that will
 * not work.
 */
class DslVariableFactory internal constructor(private val issueReporter: IssueReporter) {

    /** Once the DSL is locked, it can only be read. */
    var locked = false
        private set

    /** Prevent any further writes to the DSL. */
    fun disableWrite() {
        locked = true
    }

    private fun <T> veto(property: KProperty<*>, oldValue: T, newValue: T): Boolean {
        if (locked) {
            issueReporter.reportError(
                IssueReporter.Type.EDIT_LOCKED_DSL_VALUE,
                "It is too late to set property '${property.name}' to '$newValue'. " +
                        "(It has value '$oldValue')\n" +
                        "The DSL is now locked as the variants have been created.\n" +
                        "Either move this call earlier, or use the variant API to customize individual variants."
            )
        }
        return !locked
    }

    /** Create a new  */
    fun <T> newProperty(initialValue: T): ReadWriteProperty<Any?, T> {
        return Delegates.vetoable(initialValue, this::veto)
    }
}