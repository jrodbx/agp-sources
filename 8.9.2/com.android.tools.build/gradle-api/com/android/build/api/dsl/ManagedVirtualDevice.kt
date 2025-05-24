package com.android.build.api.dsl

import com.android.build.api.annotations.ReplacedByIncubating
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
     * The api level of Android to be run on the device. This annotation is deprecated,
     * use [sdkVersion] instead.
     *
     * This specifies the sdk version of the device. Setting this value will override previous
     * calls of [sdkVersion], [sdkPreview], and [apiPreview].
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html)
     * for a list of valid values.
     */
    @get: ReplacedByIncubating(
        message = "Replaced by the new property sdkVersion",
        bugId = 382716517)
    @set: ReplacedByIncubating(
        message = "Replaced by the new property sdkVersion",
        bugId = 382716517)
    var apiLevel: Int

    /**
     * The sdk version code of Android to be run on the device. This annotation is deprecated,
     * use [sdkPreview] instead.
     *
     * Setting this it will override previous calls of [apiLevel], [apiPreview], [sdkVersion], and
     * [sdkPreview] setters.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html)
     * for a list of valid values.
     */
    @get: ReplacedByIncubating(
        message = "Replaced by the new property sdkPreview",
        bugId = 382716889)
    @get: Incubating
    @set: ReplacedByIncubating(
        message = "Replaced by the new property sdkPreview",
        bugId = 382716889)
    @set: Incubating
    var apiPreview: String?

    /**
     * The SDK version of Android to be run on the device.
     *
     * Setting this value will override previous calls of [sdkVersion] or [sdkPreview] setters.
     * Only one of [sdkVersion] and [sdkPreview] should be set
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html)
     * for a list of valid values.
     */
    @get: Incubating
    @set: Incubating
    var sdkVersion: Int

    /**
     * The SDK version code of Android to be run on the device.
     *
     * Setting this value will override previous calls of [sdkVersion] or [sdkPreview] setters.
     * Only one of [sdkVersion] and [sdkPreview] should be set
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html)
     * for a list of valid values.
     */
    @get: Incubating
    @set: Incubating
    var sdkPreview: String?

    /**
     * The extension version of the system image to be run on the device.
     *
     * By default, the basic system image will be chosen for the given [sdkVersion].
     */
    @get: Incubating
    @set: Incubating
    var sdkExtensionVersion: Int?

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

    /**
     * What size pages the device should be aligned to.
     *
     * By default, the system image that is validated against Google apis will be chosen.
     * At present (api 35 or below) that is the 4KB page size system image. Newer apis will emit
     * a warning when [PageAlignment.DEFAULT_FOR_SDK_VERSION] is chosen (as we will not be able
     * to properly determine the validated image when offline.)
     */
    @get: Incubating
    @set: Incubating
    var pageAlignment: PageAlignment

    /**
     * Defines possible system image selection strategies based on the requested Page Alignment
     *
     * This allows for testing on devices for both 4KB and 16KB page sizes. See
     * [Android Page Sizes](https://developer.android.com/guide/practices/page-sizes) for more
     * information.
     */
    @Incubating
    enum class PageAlignment {
        /**
         * Selects the page alignment that would result in the certified system image for the given
         * [sdkVersion]. At present (API 35 and below), the certified image is always 4KB aligned.
         */
        @Incubating
        DEFAULT_FOR_SDK_VERSION,
        /**
         * Selects the system image with 4KB aligned pages. If no such image exists, the setup for
         * the managed device will fail.
         */
        @Incubating
        FORCE_4KB_PAGES,
        /**
         * Selects the system image with 16KB aligned pages. If no such image exists, the setup for
         * the managed device will fail.
         */
        @Incubating
        FORCE_16KB_PAGES
    }
}
