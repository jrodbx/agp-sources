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

package com.android.build.gradle.options

import com.android.build.gradle.internal.errors.DeprecationReporter

interface Option<out T> {

    sealed class Status {

        object EXPERIMENTAL : Status()

        object STABLE : Status()

        class Deprecated(val deprecationTarget: DeprecationReporter.DeprecationTarget) :
            Status() {

            fun getDeprecationTargetMessage(): String {
                return deprecationTarget.getDeprecationTargetMessage()
            }
        }

        class Removed(

            /**
             * The version when an element was removed.
             *
             * Usage note: Do not use this field to construct a removal message, use
             * getRemovedVersionMessage() instead to ensure consistent message format.
             */
            val removedVersion: Version,

            /**
             * Additional message to be shown below the pre-formatted error/warning message.
             *
             * Note that this additional message should be constructed such that it fits well in the
             * overall message:
             *
             *     "This feature was removed in version X.Y of the Android Gradle plugin.\n
             *     $additionalMessage"
             *
             * For example, avoid writing additional messages that say "This feature has been
             * removed", as it will be duplicated.
             */
            private val additionalMessage: String? = null

        ) : Status() {

            fun getRemovedVersionMessage(): String {
                return removedVersion.getRemovedVersionMessage() +
                        (additionalMessage?.let { "\n$it" } ?: "")
            }
        }
    }

    val propertyName: String

    val defaultValue: T?
        get() = null

    val status: Status

    fun parse(value: Any): T
}
