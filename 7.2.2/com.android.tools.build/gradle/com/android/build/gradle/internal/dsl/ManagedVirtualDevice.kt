package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ManagedVirtualDevice
import javax.inject.Inject

open class ManagedVirtualDevice @Inject constructor(private val name: String) :
    ManagedVirtualDevice {

    override fun getName(): String = name

    override var device = ""

    override var apiLevel = -1

    override var systemImageSource = "google"

    override var abi = ""

    override var require64Bit = false
}
