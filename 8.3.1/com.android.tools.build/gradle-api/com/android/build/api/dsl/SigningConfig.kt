/*
 * Copyright (C) 2019 The Android Open Source Project
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

import java.io.File

/**
 * DSL object for configuring options related to signing for APKs and bundles.
 *
 * [ApkSigningConfig] extends this with options relating to just APKs
 *
 */
interface SigningConfig {

    /**
     * Store file used when signing.
     *
     * See [Signing Your Applications](http://developer.android.com/tools/publishing/app-signing.html)
     */
    var storeFile: File?

    /**
     * Store password used when signing.
     *
     * See [Signing Your Applications](http://developer.android.com/tools/publishing/app-signing.html)
     */
    var storePassword: String?

    /**
     * Key alias used when signing.
     *
     * See [Signing Your Applications](http://developer.android.com/tools/publishing/app-signing.html)
     */
    var keyAlias: String?

    /**
     * Key password used when signing.
     *
     * See [Signing Your Applications](http://developer.android.com/tools/publishing/app-signing.html)
     */
    var keyPassword: String?

    /**
     * Store type used when signing.
     *
     * See [Signing Your Applications](http://developer.android.com/tools/publishing/app-signing.html)
     */
    var storeType: String?

    /**
     * Copies all properties from the given signing config.
     */
    fun initWith(that: SigningConfig)
}
