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
package com.android.utils.concurrency

import com.android.annotations.concurrency.GuardedBy
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.lang.ref.WeakReference
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

/**
 * Provides access to values of type [V], which change over time and are expensive to (re)compute.
 *
 * Implementations may cache the last known value for instant access, but this is optional and consumers should not rely on one being
 * available. Implementations may batch requests for update or ignore them if they have other ways of staying up-to-date, e.g. a PSI
 * listener.
 *
 * If calling code is asynchronous, it should add a listener to the result of [get]. Note that the returned future may already be
 * done, so the value may be ready to consume immediately.
 *
 * All implementations of this interface must be thread safe.
 */
interface AsyncSupplier<V>: Supplier<ListenableFuture<V>> {
  /**
   * Requests the value to be recomputed and returns the last known value, if available.
   *
   * [AsyncSupplier] implementations don't need to hold on to old values, so this is not guaranteed to ever be non-null. Even if
   * the [AsyncSupplier] caches the last known value, it may have not been computed yet.
   */
  val now: V?
  /**
   * Returns a future with the computed value.
   *
   * [AsyncSupplier] implementations may batch requests, so it's possible the computation hasn't started yet or that the same
   * [ListenableFuture] will be returned from multiple calls to [AsyncSupplier.get]. Because of that, cancelling this future will
   * have no effect.
   *
   * If the [AsyncSupplier] keeps the value up-to-date through a listener or a similar mechanism, the future is returned in done
   * state.
   */
  override fun get(): ListenableFuture<V>
}

/**
 * An [AsyncSupplier] based on two functions: one to compute a new value and one to check if a previously computed value can be reused.
 *
 * @param compute The expensive computation that returns the value. This computation will be called on the given
 *  [executor]. The calls to the [executor] will be serialized so at most one computation is running at a given
 *  time.
 * @param isUpToDate Function that returns whether the passed value is up to date. This method should be thread-safe.
 */
class CachedAsyncSupplier<V> @JvmOverloads constructor(private val compute: () -> V,
                                                       private val isUpToDate: (value: V) -> Boolean = { _ -> true },
                                                       executor: ExecutorService) : AsyncSupplier<V> {
  private val executor = MoreExecutors.listeningDecorator(executor)
  private val runningComputationLock = ReentrantLock()
  @GuardedBy("runningComputationLock")
  private var runningComputation: ListenableFuture<V> = Futures.immediateFuture(null)
  private val lastComputedValueLock = ReentrantReadWriteLock()
  @GuardedBy("lastComputedValueLock")
  private var lastComputedValue: V? = null
    get() = lastComputedValueLock.read {
      field
    }
    set(newValue) {
      lastComputedValueLock.write {
        field = newValue
      }
    }

  override var now: V?
    get() {
      // This will trigger a refresh if needed
      val currentComputation = get()

      return if (currentComputation.isDone) {
        Futures.getDone(currentComputation)
      }
      else lastComputedValue
    }
    private set(newValue) {
      lastComputedValue = newValue
    }

  override fun get(): ListenableFuture<V> {
    val cachedValue = lastComputedValue
    if (cachedValue != null && isUpToDate(cachedValue)) {
      return Futures.immediateFuture(cachedValue)
    }

    return runningComputationLock.withLock {
      if (runningComputation.isDone) {
        runningComputation = Futures.nonCancellationPropagating(executor.submit(Callable<V> {
          val computedValue = compute()
          this.lastComputedValue = computedValue
          return@Callable computedValue
        }))
      }

      // runningComputation can not be null since it's guarded by runningComputationLock
      return runningComputation
    }
  }
}

internal data class ValueWithTimestamp<V>(val value: V, val timestampMs: Long)

/**
 * An [AsyncSupplier] value with a method to calculate the value. This class also provides a convenience timestamp of
 * when the value was originally calculated in case the value does not have an obvious way to determine if
 * it's expired.
 *
 * @param compute The expensive computation that returns the value. This computation will be called on the given
 *  executor.
 * @param isUpToDate Function that receives a value and the creation timestamp of that value and returns whether
 *  is up to date.
 * @param executor The executor to use while running the `compute()` operation.
 * @param timestampSource Clock source for the timestamps.
 */
class CachedAsyncSupplierWithTimestamp<V> @JvmOverloads constructor(compute: () -> V,
                                                                    isUpToDate: (timestampMs: Long, value: V) -> Boolean = { _, _ -> true },
                                                                    private val executor: ExecutorService,
                                                                    timestampSource: () -> Long = { System.currentTimeMillis() }) : AsyncSupplier<V> {
  private val delegate = CachedAsyncSupplier(
    compute = {
      ValueWithTimestamp(value = compute(), timestampMs = timestampSource())
    },
    isUpToDate = { valueWithTimestamp -> isUpToDate(valueWithTimestamp.timestampMs, valueWithTimestamp.value) },
    executor = executor
  )

  override val now: V?
    get() = delegate.now?.value

  override fun get(): ListenableFuture<V> = Futures.transform(delegate.get(),
                                                              com.google.common.base.Function<ValueWithTimestamp<V>, V> { computed -> computed!!.value },
                                                              executor)

}

/**
 * Utility class to auto-refresh [AsyncSupplier] values. This will only refresh the value as long as there are references
 * to it and it will stop if the value is garbage collected.
 *
 * @param asyncSupplier [AsyncSupplier] container that will be auto-refreshed.
 * @param refreshDuration [Duration] specifying the refresh period.
 * @param executor [ScheduledExecutorService] to run the auto-refresh operations.
 */
class AsyncSupplierRefresher<V : AsyncSupplier<*>>(asyncSupplier: V,
                                                   private val refreshDuration: Duration,
                                                   private val executor: ScheduledExecutorService) {
  private val expensiveValueRef = WeakReference<V>(asyncSupplier)
  private val scheduledFutureLock = ReentrantLock()
  @GuardedBy("scheduledFutureLock")
  private var scheduledFuture: ScheduledFuture<*>? = null

  init {
    reschedule()
  }

  private fun reschedule() {
    scheduledFutureLock.withLock {
      if (scheduledFuture?.isCancelled == true) {
        return
      }

      scheduledFuture = executor.schedule(this::refresh, refreshDuration.toMillis(), TimeUnit.MILLISECONDS)
    }
  }

  private fun refresh() {
    val expensiveValue = expensiveValueRef.get()

    if (expensiveValue != null) {
      expensiveValue.get()
      reschedule()
    }
  }

  /**
   * Cancels the auto-refresh of this value.
   */
  fun cancel() {
    scheduledFutureLock.withLock {
      scheduledFuture?.cancel(true)
    }
  }
}