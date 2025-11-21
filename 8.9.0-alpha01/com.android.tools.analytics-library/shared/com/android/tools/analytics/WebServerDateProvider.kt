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
package com.android.tools.analytics

import com.android.utils.DateProvider
import com.google.common.annotations.VisibleForTesting
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

const val MILLI_TO_NANOS = 1_000_000

/**
 * Uses the "Date" response header from a http request to a webserver to provide a consistent notion
 * of time. Uses System.nanoTime() to provide a monotonic increase of time since the last
 * webrequest.
 *
 * This DateProvider can be used instead of thee System one if a network connection is present and
 * it is suspected that the local clock might be set incorrectly.
 */
open class WebServerDateProvider(initialUrl: URL) : DateProvider {

  var serverTimestampNanos = -1L
  var localNanosAtLastRequest = -1L

  init {
    if (!updateServerTimestampWithHeadRequest(initialUrl)) {
      throw IOException(
        "Unable to initializeWebServerDateProvider based on ${initialUrl}, unable to parse 'Date' response header"
      )
    }
  }

  private fun readDateHeaderWithHeadRequest(url: URL): HttpURLConnection {
    val connection = url.openConnection()
    if (connection !is HttpURLConnection) {
      throw RuntimeException(
        "Unexpected HttpConnection type: ${connection.javaClass.canonicalName}"
      )
    }
    connection.requestMethod = "HEAD"
    connection.connect()
    return connection
  }

  fun updateServerTimestampWithHeadRequest(url: URL): Boolean {
    return updateServerTimestampFromExistingConnection(readDateHeaderWithHeadRequest(url))
  }

  fun updateServerTimestampFromExistingConnection(connection: HttpURLConnection): Boolean {
    return updateOffsetFromDate(connection.getHeaderFieldDate("Date", -1))
  }

  private fun updateOffsetFromDate(date: Long): Boolean {
    if (date == -1L) {
      return false
    }
    serverTimestampNanos = date * MILLI_TO_NANOS
    localNanosAtLastRequest = nanoTime()
    return true
  }

  @VisibleForTesting protected open fun nanoTime(): Long = System.nanoTime()

  override fun now(): Date {
    val diff = nanoTime() - localNanosAtLastRequest
    val currentTimeNanos = serverTimestampNanos + diff
    return Date(currentTimeNanos / MILLI_TO_NANOS)
  }
}
