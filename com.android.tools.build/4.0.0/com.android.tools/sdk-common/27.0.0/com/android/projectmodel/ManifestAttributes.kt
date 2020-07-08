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
@file:JvmName("ManifestAttributesUtil")

package com.android.projectmodel

import com.android.sdklib.AndroidVersion

/**
 * Holds the set of simple values that can be present in an Android manifest. Multiple [ManifestAttributes] instances
 * can be merged using the [ManifestAttributes.plus] operator, in which case the non-null values
 * from the right instance will override the ones on the left. This allows a [ManifestAttributes]
 * instance to hold either the set of resolved values for a manifest or a set of overrides.
 *
 * This is not a container for all things related to the manifest. New properties should be added
 * here if and only if they could be obtained directly from a manifest and overridden in a [Config].
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class ManifestAttributes(
    /**
     * The application ID. This is the name of the application, and is defined here:
     * https://developer.android.com/studio/build/application-id.html. Null if undefined.
     * Non-application [Artifact] instances do not normally specify this attribute and should
     * ignore it if present. If this [ManifestAttributes] was produced directly from a manifest
     * file, this property will hold the value of the _package_ attribute.
     */
    val applicationId: String? = null,
    /**
     * The internal version number of the Android application. For the full specification of this attribute, see
     * https://developer.android.com/guide/topics/manifest/manifest-element.html#vcode. Null if undefined.
     */
    val versionCode: Int? = null,
    /**
     * The version name shown to users. For more information, see
     * https://developer.android.com/studio/publish/versioning.html
     */
    val versionName: String? = null,
    /**
     * The minimum supported SDK version or null if not set. For more information, see
     * https://developer.android.com/guide/topics/manifest/uses-sdk-element.html. Null if undefined.
     */
    val minSdkVersion: AndroidVersion? = null,
    /**
     * The min SDK version that we pass at runtime. This is normally the same as [minSdkVersion], but with "preview"
     * platforms the [minSdkVersion], [targetSdkVersion], and [compileSdkVersion] are all coerced to the same "preview"
     * platform value. This should only be used by the launch code, packaging code, etc. Null if undefined.
     */
    val apiVersion: AndroidVersion? = null,
    /**
     * The maximum supported SDK version or null if not set. For more information, see
     * https://developer.android.com/guide/topics/manifest/uses-sdk-element.html. Null if undefined.
     */
    val maxSdkVersion: AndroidVersion? = null,
    /**
     * The target SDK version. For more information, see
     * https://developer.android.com/guide/topics/manifest/uses-sdk-element.html. Null if undefined.
     */
    val targetSdkVersion: AndroidVersion? = null,
    /**
     * The SDK version the app is meant to be compiled against. Also known as buildSdkVersion.
     * For more information: https://developer.android.com/studio/build/index.html. Null if not overridden.
     */
    val compileSdkVersion: AndroidVersion? = null,
    /**
     * True if the application is debuggable, false if the application is not debuggable. Null if undefined.
     */
    val debuggable: Boolean? = null
) {
    /**
     * Creates a new set of attributes by replacing any attribute in this object if there is a corresponding
     * non-null property in the other set.
     */
    operator fun plus(other: ManifestAttributes): ManifestAttributes =
        ManifestAttributes(
            applicationId = other.applicationId ?: applicationId,
            versionCode = other.versionCode ?: versionCode,
            versionName = other.versionName ?: versionName,
            apiVersion = other.apiVersion ?: apiVersion,
            minSdkVersion = other.minSdkVersion ?: minSdkVersion,
            maxSdkVersion = other.maxSdkVersion ?: maxSdkVersion,
            targetSdkVersion = other.targetSdkVersion ?: targetSdkVersion,
            compileSdkVersion = other.compileSdkVersion ?: compileSdkVersion,
            debuggable = other.debuggable ?: debuggable
        )

    override fun toString(): String = printProperties(this, emptyManifestAttributes)

    /**
     * Returns a copy of the receiver, with the [targetSdkVersion], [minSdkVersion], [apiVersion],
     * and [compileSdkVersion] all set to the given version number.
     */
    fun withVersion(version: AndroidVersion?) = copy(
        targetSdkVersion = version,
        minSdkVersion = version,
        apiVersion = version,
        compileSdkVersion = version
    )
}

val emptyManifestAttributes = ManifestAttributes()
