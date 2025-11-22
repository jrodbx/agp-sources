package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment
import com.android.builder.core.apiVersionFromString
import com.android.builder.core.DefaultApiVersion
import com.android.builder.model.ApiVersion
import com.android.build.gradle.internal.LoggerWrapper
import com.android.utils.ILogger
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

const val PAGE_16KB_SUFFIX = "_ps16k"
const val PAGE_EMPTY_SUFFIX = ""

open class ManagedVirtualDevice @Inject constructor(private val name: String) :
    ManagedVirtualDevice {

    private val logger: ILogger = LoggerWrapper.getLogger(ManagedVirtualDevice::class.java)

    override fun getName(): String = name

    @get: Input
    override var device = ""

    @get: Internal
    override var apiLevel: Int by this::sdkVersion

    @get: Internal
    override var apiPreview: String? by this::sdkPreview

    @get: Input
    override var sdkVersion: Int
        get() = apiVersion?.apiLevel ?: 0
        set(value) {
            apiVersion = DefaultApiVersion(value)
        }

    @get: Internal
    override var sdkPreview: String?
        get() = apiVersion?.codename
        set(value) {
            apiVersion = apiVersionFromString(value)
        }

    @get: Optional
    @get: Input
    override var sdkExtensionVersion: Int? = null

    @get: Input
    override var systemImageSource = "google"

    @get: Input
    override var require64Bit = false

    @get: Internal
    override var pageAlignment: PageAlignment =
        PageAlignment.DEFAULT_FOR_SDK_VERSION

    @get: Input
    val pageAlignmentSuffix: String
        get() = when (pageAlignment) {
            PageAlignment.FORCE_16KB_PAGES -> PAGE_16KB_SUFFIX
            PageAlignment.FORCE_4KB_PAGES -> PAGE_EMPTY_SUFFIX
            else -> {
                // We technically know that the 4kb aligned images are the ones that google_apis is
                // validated against up to api 36. At present, however we don't know when the switch
                // will happen where the 16kb images will be validated instead.
                if (sdkVersion > 36 &&
                    pageAlignment == PageAlignment.DEFAULT_FOR_SDK_VERSION) {
                    logger.warning("""
                            $name has a pageAlignment value of
                            DEFAULT_FOR_SDK_VERSION. However for sdkVersion = ${sdkVersion},
                            the page size of the validated system image cannot be determined. A 4 kb
                            aligned image will be selected. If this is not intended set
                            pageAlignment = ${PageAlignment.FORCE_16KB_PAGES} for
                            ${name}
                         """.trimIndent())
                }
                PAGE_EMPTY_SUFFIX
            }
        }

    private var apiVersion: ApiVersion? = null
}
