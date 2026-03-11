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

import com.sun.management.OperatingSystemMXBean
import java.awt.GraphicsEnvironment
import java.lang.management.*
import kotlin.reflect.KProperty

/** Entry point to various host data classes such as MxBeans and GraphicsEnvironment. Used to allow stubbing these out in tests. */
object HostData {

  @JvmStatic var osBean: OperatingSystemMXBean? by stubbable { ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean }

  @JvmStatic var runtimeBean: RuntimeMXBean? by stubbable { ManagementFactory.getRuntimeMXBean() }

  @JvmStatic var graphicsEnvironment: GraphicsEnvironment? by stubbable { GraphicsEnvironment.getLocalGraphicsEnvironment() }

  @JvmStatic var memoryBean: MemoryMXBean? by stubbable { ManagementFactory.getMemoryMXBean() }

  @JvmStatic var classLoadingBean: ClassLoadingMXBean? by stubbable { ManagementFactory.getClassLoadingMXBean() }

  @JvmStatic var garbageCollectorBeans: List<GarbageCollectorMXBean>? by stubbable { ManagementFactory.getGarbageCollectorMXBeans() }

  @JvmStatic var threadBean: ThreadMXBean? by stubbable { ManagementFactory.getThreadMXBean() }
}

/**
 * stubbable is like lazy but provides a setter so the property can be stubbed out in tests
 *
 * TODO(jvg): consider moving this to a utility library so other parts of our codebase can benefit from this pattern.
 */
internal fun <T> stubbable(initializer: () -> T): StubbableLazy<T> = StubbableLazy(initializer)

internal class StubbableLazy<T>(val initializer: () -> T) : Lazy<T> {
  private var _value: T? = null

  override val value: T
    get() {
      val current = _value
      if (current != null) {
        return current
      }

      return synchronized(this) {
        _value = initializer()
        _value!!
      }
    }

  override fun isInitialized(): Boolean = _value !== null

  operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

  operator fun setValue(hisRef: Any?, property: KProperty<*>, t: T) {
    _value = t
  }
}
