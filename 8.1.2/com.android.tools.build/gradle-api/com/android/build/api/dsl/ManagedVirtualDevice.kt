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
     * Setting this it will override previous calls of [apiLevel] or [apiPreview] setters.
     * Only one of [apiLevel] and [apiPreview] should be set.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html)
     * for a list of valid values.
     */
    var apiLevel: Int

    /**
     * The api version code of Android to be run on the device.
     * Setting this it will override previous calls of [apiLevel] or [apiPreview] setters.
     * Only one of [apiLevel] and [apiPreview] should be set.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html)
     * for a list of valid values.
     */
    @get: Incubating
    @set: Incubating
    var apiPreview: String?

    /**
     * Which source the system image should come from. Either "google", "google-atd", "aosp", or
     * "aosp-atd". You can also specify an explicit source such as "google_apis_playstore".
     *
     * "google", the default, will select Google APIs images for the device.
     * "google-atd" will use automated test device images from Google APIs.
     * "aosp" will use the Android Open Source Project images for the device.
     * "aosp-atd" will use automated test device image from the Android Open Source Project.
     */
    var systemImageSource: String

    /**
     * Whether the image must be a 64 bit image. Defaults to false.
     *
     * On x86_64 machines:
     *   When false, the managed device will use the 32 bit image if available with the given api
     *   level and source, otherwise the 64 bit image will be used.
     *   When true, the 64 image must be used and setup will fail if an appropriate image does not
     *   exist.
     * On arm machines:
     *   The value of this parameter has no effect. An arm64 image is always selected.
     */
    var require64Bit: Boolean
}
