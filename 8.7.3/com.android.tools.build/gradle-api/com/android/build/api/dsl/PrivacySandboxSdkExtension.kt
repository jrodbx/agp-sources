/*
 * Copyright (C) 2022 The Android Open Source Project
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

import org.gradle.api.Incubating

@Incubating
interface PrivacySandboxSdkExtension {

    /**
     * Specifies the API level to compile your project against. The Android plugin requires you to
     * configure this property.
     *
     * This means your code can use only the Android APIs included in that API level and lower.
     * You can configure the compile sdk version by adding the following to the `android`
     * block: `compileSdk = 26`.
     *
     * You should generally
     * [use the most up-to-date API level](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels)
     * available.
     * If you are planning to also support older API levels, it's good practice to
     * [use the Lint tool](https://developer.android.com/studio/write/lint.html)
     * to check if you are using APIs that are not available in earlier API levels.
     *
     * The value you assign to this property is parsed and stored in a normalized form, so
     * reading it back may return a slightly different value.
     */
    @get:Incubating
    @set:Incubating
    var compileSdk: Int?

    @get:Incubating
    @set:Incubating
    var compileSdkExtension: Int?

    @get:Incubating
    @set:Incubating
    var compileSdkPreview: String?

    /**
     * Specifies the version of the
     * [SDK Build Tools](https://developer.android.com/studio/releases/build-tools.html)
     * to use when building your project.
     *
     * By default, the plugin uses the minimum version of the build tools required by the
     * [version of the plugin](https://developer.android.com/studio/releases/gradle-plugin.html#revisions)
     * you're using.
     * To specify a different version of the build tools for the plugin to use,
     * specify the version as follows:
     *
     * ```
     * android {
     *     // Specifying this property is optional.
     *     buildToolsVersion "26.0.0"
     * }
     * ```
     *
     * For a list of build tools releases, read
     * [the release notes](https://developer.android.com/studio/releases/build-tools.html#notes).
     *
     * Note that the value assigned to this property is parsed and stored in a normalized form,
     * so reading it back may give a slightly different result.
     */
    @get:Incubating
    @set:Incubating
    var buildToolsVersion: String

    @get:Incubating
    val experimentalProperties: MutableMap<String, Any>

    @get:Incubating
    @set:Incubating
    var minSdk: Int?

    @get:Incubating
    @set:Incubating
    var minSdkPreview: String?

    @get:Incubating
    @set:Incubating
    var targetSdk: Int?

    @get:Deprecated(message = "namespace is replaced with applicationId in bundle block", replaceWith = ReplaceWith("bundle.applicationId"))
    @get:Incubating
    @set:Deprecated(message = "namespace is replaced with applicationId in bundle block", replaceWith = ReplaceWith("bundle.applicationId"))
    @set:Incubating
    var namespace: String?

    @get:Incubating
    val bundle: PrivacySandboxSdkBundle

    @Incubating
    fun bundle(action: PrivacySandboxSdkBundle.() -> Unit)

    @get:Incubating
    val signingConfig: SigningConfig

    @Incubating
    fun signingConfig(action: SigningConfig?.() -> Unit)

    /**
     * Specifies options for the R8/D8 optimization tool.
     *
     * For more information about the properties you can configure in this block, see [PrivacySandboxSdkOptimization].
     */
    @get:Incubating
    val optimization: PrivacySandboxSdkOptimization

    /**
     * Specifies options for the R8/D8 optimization tool.
     *
     * For more information about the properties you can configure in this block, see [PrivacySandboxSdkOptimization].
     */
    @Incubating
    fun optimization(action: PrivacySandboxSdkOptimization.() -> Unit)

}
