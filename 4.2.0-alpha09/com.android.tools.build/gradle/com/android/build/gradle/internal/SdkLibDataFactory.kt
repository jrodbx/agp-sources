/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.builder.sdk.SdkLibData
import com.android.repository.api.Channel
import com.android.repository.api.ConsoleProgressIndicator
import com.android.repository.api.Downloader
import com.android.repository.api.SettingsController
import com.android.repository.impl.downloader.LocalFileAwareDownloader
import com.android.repository.io.FileOpUtils
import com.android.sdklib.repository.legacy.LegacyDownloader
import com.android.utils.ILogger
import com.google.common.annotations.VisibleForTesting
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.Properties

class SdkLibDataFactory(
    private val enableDownload: Boolean,
    private val androidSdkChannel: Int?, // projectOptions.get(IntegerOption.ANDROID_SDK_CHANNEL)
    private val logger: ILogger) {

    fun getSdkLibData(): SdkLibData {
        return if (enableDownload) {
            SdkLibData.download(getDownloader(), getSettingsController())
        } else {
            SdkLibData.dontDownload()
        }
    }

    private fun getDownloader(): Downloader {
        return LocalFileAwareDownloader(
            LegacyDownloader(FileOpUtils.create(), getSettingsController())
        )
    }

    private fun getSettingsController(): SettingsController {
        val proxy = createProxy(System.getProperties(), logger)
        return object : SettingsController {
            override fun getForceHttp(): Boolean {
                return false
            }

            override fun setForceHttp(force: Boolean) {
                // Default, doesn't allow to set force HTTP.
            }

            override fun getDisableSdkPatches(): Boolean {
                return true
            }

            override fun setDisableSdkPatches(disable: Boolean) {
                // Default, doesn't allow to enable SDK patches, since this is an IDEA thing.
            }

            override fun getChannel(): Channel? {
                return if (androidSdkChannel != null) {
                    Channel.create(androidSdkChannel!!)
                } else {
                    Channel.DEFAULT
                }
            }

            override fun getProxy(): Proxy {
                return proxy
            }
        }
    }

    @VisibleForTesting
    fun createProxy(properties: Properties, logger: ILogger): Proxy {
        var host: String? = properties.getProperty("https.proxyHost")
        var port = 443
        if (host != null) {
            val maybePort = properties.getProperty("https.proxyPort")
            if (maybePort != null) {
                try {
                    port = Integer.parseInt(maybePort)
                } catch (e: NumberFormatException) {
                    logger.lifecycle(
                        "Invalid https.proxyPort '$maybePort', using default 443"
                    )
                }
            }
        } else {
            host = properties.getProperty("http.proxyHost")
            if (host != null) {
                port = 80
                val maybePort = properties.getProperty("http.proxyPort")
                if (maybePort != null) {
                    try {
                        port = Integer.parseInt(maybePort)
                    } catch (e: NumberFormatException) {
                        logger.lifecycle(
                            "Invalid http.proxyPort '$maybePort', using default 80"
                        )
                    }

                }
            }
        }
        if (host != null) {
            val proxyAddr = createAddress(host, port)
            if (proxyAddr != null) {
                return Proxy(Proxy.Type.HTTP, proxyAddr!!)
            }
        }
        return Proxy.NO_PROXY

    }

    private fun createAddress(proxyHost: String, proxyPort: Int): InetSocketAddress? {
        return try {
            val address = InetAddress.getByName(proxyHost)
            InetSocketAddress(address, proxyPort)
        } catch (e: UnknownHostException) {
            ConsoleProgressIndicator().logWarning("Failed to parse host $proxyHost")
            null
        }
    }
}