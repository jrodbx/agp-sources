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
 * Maven publishing options shared by [SingleVariant] and [MultipleVariants].
 *
 * To publish sources & javadoc jar apart from AAR, use [withSourcesJar] and [withJavadocJar].
 * The following sets up publishing of sources & javadoc jar in two different publishing mechanisms.
 *
 * ```
 * android {
 *     publishing {
 *         singleVariant("release") {
 *             withSourcesJar()
 *             withJavadocJar()
 *         }
 *
 *         multipleVariants {
 *             withSourcesJar()
 *             withJavadocJar()
 *             allVariants()
 *         }
 *     }
 * }
 * ```
 */
interface PublishingOptions {

    /**
     * Publish java & kotlin sources jar as a secondary artifact to a Maven repository.
     */
    fun withSourcesJar()

    /**
     * Publish javadoc jar generated from java & kotlin source as a secondary artifact to a Maven
     * repository.
     */
    fun withJavadocJar()
}
