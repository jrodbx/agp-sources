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

package com.android.build.gradle.internal.dsl

import java.io.File

/**
 * A container for a collection of files that has the capability to add a single existing file
 * or a group of existing files to the collection
 *
 * TODO(b/267309622): Move to gradle-api
 */
interface ConfigurableFiles {
    val files: MutableList<File>
    fun file(file: Any)
    fun files(vararg files: Any)
}
