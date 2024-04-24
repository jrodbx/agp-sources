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

package com.android.builder.errors

/**
 * RuntimeException specialization for any exception happening during evaluation time.
 *
 * Exception will contain all necessary information to pass to the IDE to provide quick fixes
 * and exception cause.
 *
 * @param message a human readable error (for command line output, or if an older IDE doesn't know
 * this particular issue type.)
 * @param cause raised exception causing the failure.
 * @param data a data representing the source of the error. This goes hand in hand with the
 * <var>type</var>, and is not meant to be readable. Instead a (possibly translated) message
 * is created from this data and type.
 * @param multlineMessage a human readable error that spans through multiple lines (might require
 * special IDE treatment.)
 */
class EvalIssueException private constructor(
    override val message: String,
    cause: Throwable? = null,
    val data: String? = null,
    val multlineMessage: List<String>? = null) : RuntimeException(message, cause) {

    @JvmOverloads
    constructor(message: String, data: String? = null, multlineMessage: List<String>? = null) :
            this(message, null, data, multlineMessage)

    constructor(cause: Throwable) : this(cause.message ?: cause.localizedMessage, cause)
}