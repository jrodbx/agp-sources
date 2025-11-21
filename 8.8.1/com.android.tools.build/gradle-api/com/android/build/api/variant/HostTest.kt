/*
 * Copyright (C) 2024 The Android Open Source Project
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
import org.gradle.api.tasks.testing.Test

/**
 * Model for Host Test components that contains build-time properties. Host Tests run on a JVM
 * on your build machine, as opposed to [DeviceTest] which run on an Android Device.
 *
 * This object is accessible on subtypes of [Variant] that implement [HasHostTests], via
 * [HasHostTests.hostTests]. It is also part of [Variant.nestedComponents].
 *
 * The presence of this component in a variant is controlled by [HostTestBuilder.enable]
 * which is accessible on subtypes of [VariantBuilder] that implement [HasHostTestsBuilder]
 */
@Incubating
interface HostTest: TestComponent {

    /**
     * Runs some action to configure the Variant's unit test [Test] task.
     *
     * The action will only run if the task is configured. In particular the
     * [HasHostTestsBuilder.hostTests[HasHostTestsBuilder.UNIT_TEST_TYPE]?.enable] must be set to
     * true (it is true by default).
     *
     * Example :
     * ```(kotlin)
     *  androidComponents {
     *      onVariants { variant ->
     *          variant.hostTests[HostTestsBuilder.UNIT_TEST_TYPE]?.configureTestTask { testTask ->
     *              testTask.beforeTest { descriptor ->
     *                  println("Running test: " + descriptor)
     *              }
     *          }
     *      }
     *  }
     * ```
     * @param action to configure the [Test] task.
     */
    @Incubating
    fun configureTestTask(action: (Test)-> Unit)

    /**
     * Whether test coverage is enabled for this host test.
     *
     * If enabled, this uses Jacoco to capture coverage and creates a report in the build
     * directory.
     *
     * You cannot change the value any longer, to change it, please use
     * [HostTestBuilder.enableCodeCoverage] in the [AndroidComponentsExtension.beforeVariants]
     * callback.
     */
    @get:Incubating
    val codeCoverageEnabled: Boolean
}
