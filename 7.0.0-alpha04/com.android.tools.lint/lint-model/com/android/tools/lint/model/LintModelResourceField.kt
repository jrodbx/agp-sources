/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.model

interface LintModelResourceField {
    val type: String
    val name: String
    val value: String

    operator fun component1(): String = type
    operator fun component2(): String = name
    operator fun component3(): String = value
}

data class DefaultLintModelResourceField(
    override val type: String,
    override val name: String,
    override val value: String
) : LintModelResourceField {
    override fun toString(): String {
        return "$name:$type=$value"
    }
}
