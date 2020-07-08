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
@file:JvmName("VariantUtil")

package com.android.projectmodel

/**
 * Metadata for a variant of an [AndroidSubmodule]. Variants
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class Variant(
    /**
     * Identifier of the [Variant]. Meant to be unique within a given [AndroidSubmodule] and
     * stable across syncs. This will be used for cross-referencing the [Variant] from other
     * projects in [ProjectLibrary.variant].
     */
    val name: String,
    /**
     * User-readable name of the [Variant]. By default, this is the same as the [name].
     */
    val displayName: String = name
) {
    constructor(
        path: SubmodulePath
    ) : this(name = path.toConfigPath().simpleName)

    override fun toString(): String = printProperties(
        this, Variant(name = "")
    )
}

/**
 * Name reserved the main artifact in a [Variant].
 */
const val ARTIFACT_NAME_MAIN = "_main_"

/**
 * Name reserved the android test artifact in a [Variant].
 */
const val ARTIFACT_NAME_ANDROID_TEST = "_android_test_"

/**
 * Name reserved the unit test artifact in a [Variant].
 */
const val ARTIFACT_NAME_UNIT_TEST = "_unit_test_"
