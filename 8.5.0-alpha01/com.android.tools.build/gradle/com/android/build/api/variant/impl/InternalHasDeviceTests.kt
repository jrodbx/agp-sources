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

package com.android.build.api.variant.impl

import com.android.build.api.component.impl.DeviceTestImpl
import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.HasDeviceTests
import com.android.builder.core.ComponentTypeImpl

/**
 * Internal marker interface for [VariantImpl] that potentially has associated device tests,
 * e.g. the androidTest component.
 *
 * Encapsulates access to the deprecated [com.android.build.api.variant.HasAndroidTest] interface.
 */
interface InternalHasDeviceTests: HasDeviceTests, com.android.build.api.variant.HasAndroidTest {

    //
    // Public API implementation
    //
    override val deviceTests: MutableList<DeviceTest>

    //
    // Private APIs
    //
    val defaultDeviceTest: DeviceTest?
        get() = deviceTests.find {
            if (it is DeviceTestImpl) {
                return@find it.componentType == ComponentTypeImpl.ANDROID_TEST
            }
            // if it's not our default android test, it has to be KmpAndroidTestImpl, otherwise
            // that's not the default device test.
            it is KmpAndroidTestImpl
        }

    /**
     * This method will most likely be promoted to public APIs soon once we figure out a good
     * naming scheme for the [DeviceTest] instances.
     *
     * Right now, each [DeviceTest] being a [com.android.build.api.variant.Component] and therefore
     * has a [com.android.build.api.variant.ComponentIdentity]. This identity has a
     * [com.android.build.api.variant.ComponentIdentity.name] which is composed of the Variant's
     * name and test type. So a `blueDebug` variant's android test Component will be named
     * `blueDebugAndroidTest`. This is due because all the Components must have a unique name within
     * the project.
     *
     * With this API, the [HasDeviceTests] instance (this) is the Variant object, so when we call
     * [deviceTests], the list is already bounded by the Variant instance it is coming from. So
     * naming could be simplified from `blueDebugAndroidTest` to just 'androidTest' or 'AndroidTest',
     * basically using the [ComponentTypeImpl.suffix].
     *
     * Obviously, we will eventually add a 'add' method which will create new instances of
     * [DeviceTest] which will require a unique name within the variant scope, so that part has to
     * be figured out at the same time.
     */
    fun getByName(name: String): DeviceTest? =
        deviceTests.find { deviceTest -> deviceTest.name == name }

    //
    // Private APIs
    //

    /**
     * This is the deprecated public API implementation mainly for backward compatibility.
     * Internally. please use [defaultDeviceTest]
     */
    override val androidTest: AndroidTest?
        get() = defaultDeviceTest as? AndroidTest
}

