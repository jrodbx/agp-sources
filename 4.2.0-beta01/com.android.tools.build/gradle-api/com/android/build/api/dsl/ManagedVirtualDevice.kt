package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * Device type for emulators to be managed by the Android Gradle Plugin.
 *
 * When a device group containing this device is run for tests, Gradle will:
 * 1. Automatically start up an emulator matching the device definition. Including downloading any
 * and all required system image files and creating the avd.
 * 2. Run the tests on this device.
 * 3. Close the emulator upon completion.
 *
 * These APIs are experimental and may change without notice.
 */
@Incubating
interface ManagedVirtualDevice : Device {
    /**
     * The hardware profile of the device to be emulated.
     */
    var device: String

    /**
     * The api level of Android to be run on the device.
     *
     * This will default to the target api level of the application.
     */
    var apiLevel: Int

    /**
     * Which source the system image should come from. Either "google" or "aosp"
     *
     * "google", the default, will select Google Play images for the device.
     * "aosp" will use aosp images for the device.
     */
    var systemImageSource: String

    /**
     * The application binary interface for the device image.
     */
    var abi: String
}