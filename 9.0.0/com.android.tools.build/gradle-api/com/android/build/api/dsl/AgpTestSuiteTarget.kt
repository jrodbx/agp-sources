/*
 * Copyright (C) 2025 The Android Open Source Project
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
import org.gradle.api.Named
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.base.TestSuiteTarget

/**
 * A test suite target is a collection of tests that run in a particular context. The context is
 * defined by the devices they run on when dealing with a device test.
 *
 * android {
 *     // ... other android configurations like compileSdk, defaultConfig, etc.
 *
 *     testOptions {
 *         managedDevices {
 *             localDevices {
 *                 // First, define your individual managed devices
 *                 create("pixel2api30") {
 *                     device = "Pixel 2"
 *                     apiLevel = 30
 *                     systemImageSource = "aosp-atd" // or "google", "google-atd", "aosp"
 *                 }
 *                 create("pixel6api33") {
 *                     device = "Pixel 6"
 *                     apiLevel = 33
 *                     systemImageSource = "google-atd"
 *                 }
 *                 create("largeScreenApi31") {
 *                     device = "Nexus 10" // Example of a tablet
 *                     apiLevel = 31
 *                     systemImageSource = "aosp"
 *                 }
 *             }
 *
 *             groups {
 *                 // Now, define your groups referencing the devices above
 *                 create("phoneGroup") {
 *                     // This group targets specific phone-like devices
 *                     targetDevices.add(allDevices.getByName("pixel2api30"))
 *                     targetDevices.add(allDevices.getByName("pixel6api33"))
 *
 *                     // You can also set properties for all devices in this group
 *                     // if they are not already set on the individual device.
 *                     // For example:
 *                     // systemImageSource = "aosp-atd" // if all devices in this group should use this
 *                 }
 *
 *                 create("tabletGroup") {
 *                     // This group targets tablet-like devices
 *                     targetDevices.add(devices.getByName("largeScreenApi31"))
 *                 }
 *
 *                 create("allMyCiDevices") {
 *                     // This group could target all devices you use for CI
 *                     targetDevices.addAll(devices.getByName("pixel2api30"), devices.getByName("pixel6api33"), devices.getByName("largeScreenApi31"))
 *                 }
 *             }
 *         }
 *     }
 *
 *     testOptions {
 *         suites {
 *              create("someTestSuite") {
 *                   targets {
 *                       create("tabletRun") {
 *                          targetDevices += "tabletGroup"
 *                       }
 *                   }
 *              }
 *         }
 *     }
 * }
 */
@Suppress("UnstableApiUsage")
@Incubating
interface AgpTestSuiteTarget: TestSuiteTarget, Named {

    /**
     * Defines which group of devices or devices to run the test suite tests against. If the test runs on the host machine,
     * this list will be ignored.
     */
    @get:Incubating
    val targetDevices: MutableList<String>
}
