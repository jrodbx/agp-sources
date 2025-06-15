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
@file:JvmName("CacheUtils")

package com.android.utils.concurrency

import com.google.common.base.Throwables
import com.google.common.cache.Cache
import com.google.common.util.concurrent.UncheckedExecutionException
import java.util.concurrent.ExecutionException

/**
 * Same as [Cache.get] but in case of exceptions unwraps the [ExecutionException] layer.
 *
 * This is useful if the loader can throw "special" exceptions like ProcessCanceledException
 * in the IDE.
 */
fun <K, V> Cache<K, V>.getAndUnwrap(key: K, loader: () -> V): V {
  try {
    return get(key, loader)
  }
  catch (e: ExecutionException) {
    Throwables.throwIfUnchecked(e.cause!!)
    throw UncheckedExecutionException(e.cause!!)
  }
  catch (e: UncheckedExecutionException) {
    Throwables.throwIfUnchecked(e.cause!!)
    throw e
  }
}

fun <K, V> Cache<K, V>.retainAll(keys: Collection<K>) {
  asMap().keys.retainAll(keys)
}
