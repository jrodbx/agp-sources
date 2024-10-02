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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Interface that marks the potential existence of [TestFixtures] component on a [Variant].
 *
 * This is implemented by select subtypes of [VariantBuilder].
 */
@Incubating
interface HasTestFixturesBuilder {
    /**
     * Set to `true` if the variant's has test fixtures, `false` otherwise.
     *
     * Default value will match [com.android.build.api.dsl.TestFixtures.enable] value
     * that is set through the extension via
     * [com.android.build.api.dsl.TestedExtension.testFixtures].
     */
    var enableTestFixtures: Boolean
}
