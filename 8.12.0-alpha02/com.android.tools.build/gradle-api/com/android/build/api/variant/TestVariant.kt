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
package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Model for test components that only contains build-time properties.
 *
 * This is for components associated with the `com.android.test` plugin. This is not for
 * test components associated with variants. For these see [TestComponent].
 *
 * See [Component] and [Variant] for more information.
 */
interface TestVariant: GeneratesTestApk, Variant {
    /**
     * Variant's application ID as present in the final manifest file of the APK.
     */
    override val applicationId: Property<String>

    /**
     * The application of the app under tests.
     */
    val testedApplicationId: Provider<String>

    /**
     * The [Directory] containing the APKs of the application under test.
     *
     * The [Directory] content should be read using a
     * [com.android.build.api.variant.BuiltArtifactsLoader]
     */
    @get:Incubating
    val testedApks: Provider<Directory>
}
