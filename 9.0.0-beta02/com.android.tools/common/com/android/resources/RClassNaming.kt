/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("RClassNaming")
package com.android.resources

/** Returns the name of the field in an RClass based on the resource name. */
fun getFieldNameByResourceName(styleName: String): String {
    var i = 0
    val n = styleName.length
    while (i < n) {
        val c = styleName[i]
        if (c == '.' || c == '-' || c == ':') {
            return styleName.replace('.', '_').replace('-', '_').replace(':', '_')
        }
        i++
    }
    return styleName
}
