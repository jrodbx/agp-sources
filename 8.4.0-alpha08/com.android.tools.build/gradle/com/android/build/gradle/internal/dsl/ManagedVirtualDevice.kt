package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.builder.core.apiVersionFromString
import com.android.builder.core.DefaultApiVersion
import com.android.builder.model.ApiVersion
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

open class ManagedVirtualDevice @Inject constructor(private val name: String) :
    ManagedVirtualDevice {

    override fun getName(): String = name

    @get: Input
    override var device = ""

    @get: Input
    override var apiLevel: Int
        get() = apiVersion?.apiLevel ?: 0
        set(value) {
            apiVersion = DefaultApiVersion(value)
        }

    @get: Internal
    override var apiPreview: String?
        get() = apiVersion?.codename
        set(value) {
            apiVersion = apiVersionFromString(value)
        }

    @get: Input
    override var systemImageSource = "google"

    @get: Input
    override var require64Bit = false

    private var apiVersion: ApiVersion? = null
}
