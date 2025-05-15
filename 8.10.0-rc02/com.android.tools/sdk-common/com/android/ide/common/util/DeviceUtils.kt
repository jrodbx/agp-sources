/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("DeviceUtils")

package com.android.ide.common.util

import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import com.android.ide.common.resources.configuration.FolderConfiguration
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Convenience method to call <code>am get-config</code> on the [IDevice] receiver.
 *
 * This is only valid for API 21+ devices. For earlier devices, this will return an empty Set.
 *
 * @param timeOut an optional timeout, default is to wait forever.
 * @return a set of languages and region in the LA-rRG format, or an empty set for pre-21 devices.
 */
@JvmOverloads
@Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class
)
fun IDevice.getLanguages(timeOut: Duration = Duration.ZERO): Set<String> {
    if (this.version.apiLevel < 21) {
        return emptySet()
    }

    val receiver = Receiver()
    this.executeShellCommand("am get-config", receiver, timeOut.toMillis(), MILLISECONDS)

    return receiver.lines.asSequence()
        .filter { it.trim().startsWith("config:") }
        .map { FolderConfiguration.getLanguageConfigFromQualifiers(it.substring("config:".length).trim()) }
        .flatMap { it.asSequence() }
        .toSet()
}

private class Receiver : MultiLineReceiver() {

    val lines = mutableListOf<String>()

    override fun processNewLines(lines: Array<out String>) {
        this.lines.addAll(lines)
    }

    override fun isCancelled() = false
}

private val MDNS_AUTO_CONNECT_TLS_REGEX = Regex("adb-(.*)-.*\\._adb-tls-connect\\._tcp\\.?")

private const val MDNS_UNENCRYPTED_SUFFIX = "_adb._tcp."

/**
 * Returns `true` is the device is using mDNS auto-connect and TLS
 *
 * Example: `adb-xxxxxxxx-vWgJpq._adb-tls-connect._tcp.`
 *
 * See [ADB_MDNS_AUTO_CONNECT](https://android.googlesource.com/platform/system/core/+/master/adb/client/commandline.cpp#244)
 */
val IDevice.isMdnsAutoConnectTls : Boolean
    get() = isMdnsAutoConnectTls(this.serialNumber)

/**
 * Returns `true` is the device is using mDNS auto-connect clear.
 *
 * Example: `adb-xxxxxxxx-vWgJpq._adb._tcp.`
 *
 * See [ADB_MDNS_AUTO_CONNECT](https://android.googlesource.com/platform/system/core/+/master/adb/client/commandline.cpp#244)
 *
 */
val IDevice.isMdnsAutoConnectUnencrypted : Boolean
    get() = isMdnsAutoConnectUnencrypted(this.serialNumber)

/**
 * If this device is identified with 86UX00F4R, returns true for an argument like
 * adb-86UX00F4R-cYuns7._adb-tls-connect._tcp and vice versa
 */
fun IDevice.isSameAsDeviceWith(key: String): Boolean {
    if (serialNumber == key) {
        return true
    }

    if (isMdnsAutoConnectTls(key)) {
        if (isMdnsAutoConnectTls(serialNumber)) {
            return false
        }

        return areForSameDevice(key, serialNumber)
    }

    if (!isMdnsAutoConnectTls(serialNumber)) {
        return false
    }

    return areForSameDevice(serialNumber, key)
}

fun areForSameDevice(domainName: String, serialNumber: String): Boolean {
    assert(isMdnsAutoConnectTls(domainName))
    return MDNS_AUTO_CONNECT_TLS_REGEX.find(domainName)!!.groupValues[1] == serialNumber
}

fun isMdnsAutoConnectTls(serialNumber: String): Boolean {
    return MDNS_AUTO_CONNECT_TLS_REGEX.matches(serialNumber)
}

fun isMdnsAutoConnectUnencrypted(serialNumber: String): Boolean {
    return serialNumber.endsWith(MDNS_UNENCRYPTED_SUFFIX)
}
