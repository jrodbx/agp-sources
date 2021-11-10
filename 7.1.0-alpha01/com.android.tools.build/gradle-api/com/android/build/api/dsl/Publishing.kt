/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.dsl

/**
 * Maven publishing DSL object for configuring options related to publishing Android variants to a
 * Maven repository.
 *
 * In single variant publishing, each component created by Android Gradle Plugin is only associated
 * with one variant.
 *
 * [LibraryPublishing] extends this with options for publishing AAR
 * [ApplicationPublishing] extends this with options for publishing APK and AAB
 */
interface Publishing<SingleVariantT: SingleVariant> {
    /**
     * Publish a variant with single variant publishing mechanism.
     */
    fun singleVariant(variantName: String)

    /**
     * Publish a variant with single variant publishing options.
     */
    fun singleVariant(variantName: String, action: SingleVariantT.() -> Unit)
}
