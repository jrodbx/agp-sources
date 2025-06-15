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

import org.gradle.api.Incubating

/**
 * Options for configuring Android Emulator Access
 *
 * When enabled, it will be possible to control the emulator through gRPC.
 */
@Incubating
interface EmulatorControl {

    /** True if emulator control should be enabled. */
    @get:Incubating
    @set:Incubating
    var enable: Boolean

    /** Set of endpoints to which access is granted, this is only required if
     * the method you wish to access is in the set of methods that require
     * authorization as defined in emulator_access.json used by the emulator
     * this test is running on.
     *
     * Details on which endpoints and what considerations are taken to make an endpoint
     * accessible is described in go/emu-grpc-integration.
     */
    @get:Incubating
    val allowedEndpoints: MutableSet<String>

    /** The duration in seconds the test can access the gRPC endpoint.
     * The default value is 3600 (one hour).
     */
    @get:Incubating
    @set:Incubating
    var secondsValid: Int
}
