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
package com.android.ide.common.repository

import com.android.ide.common.repository.NetworkCache.ReadUrlDataResult
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * [GoogleMavenRepositoryV2Host] abstracts away the details of how [GoogleMavenRepositoryV2] interacts with external resources like network
 * and file systems. It allows for different implementations of how data is fetched, cached, and error handled making it more testable and
 * flexible for different environments.
 */
interface GoogleMavenRepositoryV2Host {

  /** Location to search for cached repository content files */
  val cacheDir: Path?

  /** Number of milliseconds to wait until timing out attempting to access the remote repository */
  val networkTimeoutMs: Int
    get() = 3000

  /** Maximum allowed age of cached data. */
  val cacheExpiryHours: Int
    get() = TimeUnit.DAYS.toHours(1).toInt()

  /** If false, this repository won't make network requests */
  val useNetwork: Boolean
    get() = true

  /** Reads data from the specified URL. */
  fun readUrlData(url: String, timeout: Int, lastModified: Long): ReadUrlDataResult

  /** Reads default or offline data from a relative path within the repository. */
  fun readDefaultData(relative: String): InputStream? {
    return GoogleMavenRepositoryV2Host::class.java.getResourceAsStream("/versions-offline/$relative")
  }

  /** Reports an error that occurred during data access or processing. */
  fun error(throwable: Throwable, message: String?)
}
