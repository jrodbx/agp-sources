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
package com.android.builder.model.v2.models

import com.android.builder.model.v2.AndroidModel
import com.android.builder.model.v2.ide.Library

/**
 * Global map of all the [Library] instances used in a single or multi-module gradle project.
 *
 * This is a separate model to query (the same way [AndroidProject] is queried). It must
 * be queried after all the models have been queried for their [AndroidProject].
 *
 * @since 4.2
 */
interface GlobalLibraryMap: AndroidModel {
    /**
     * the list of external libraries used by all the variants in the module.
     *
     * @return the map of address to library
     */
    val libraries: Map<String, Library>
}