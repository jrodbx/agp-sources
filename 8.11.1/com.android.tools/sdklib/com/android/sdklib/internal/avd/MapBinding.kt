/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.sdklib.internal.avd

import com.android.resources.ResourceEnum
import com.android.sdklib.devices.Storage
import kotlin.reflect.KMutableProperty1

/** An object that allows reading and writing an object of type T (or part of it) to a Map. */
internal interface MapBinding<T> {
  fun read(t: T, source: Map<String, String>)

  fun write(t: T, destination: MutableMap<String, String>)
}

/**
 * Constructs a MapBinding that binds a single property within a class T. It uses a ValueConverter,
 * for converting between values and their string representation; a key, for identifying the map
 * element to read / write; and a KMutableProperty, for reading and writing the value to the T
 * object.
 */
internal class PropertyMapBinding<T, V>(
  val converter: ValueConverter<V>,
  val key: String,
  val property: KMutableProperty1<T, V>,
) : MapBinding<T> {
  override fun read(t: T, source: Map<String, String>) {
    source[key]?.let { converter.fromString(it) }?.let { property.set(t, it) }
  }

  override fun write(t: T, destination: MutableMap<String, String>) {
    when (val value = property.get(t)) {
      null -> destination.remove(key)
      else -> destination[key] = converter.toString(value)
    }
  }
}

/**
 * Constructs a MapBinding for the receiver property, using its type to determine the necessary
 * converter.
 */
@JvmName("bindToKeyString")
internal infix fun <T> KMutableProperty1<T, String>.bindToKey(key: String) =
  PropertyMapBinding(StringConverter, key, this)

@JvmName("bindToKeyInt")
internal infix fun <T> KMutableProperty1<T, Int>.bindToKey(key: String) =
  PropertyMapBinding(IntConverter, key, this)

@JvmName("bindToKeyBoolean")
internal infix fun <T> KMutableProperty1<T, Boolean>.bindToKey(key: String) =
  PropertyMapBinding(BooleanConverter, key, this)

@JvmName("bindToKeyConfigEnum")
internal inline infix fun <T, reified E> KMutableProperty1<T, E>.bindToKey(key: String) //
where E : Enum<E>, E : ConfigEnum = PropertyMapBinding(enumConverter<E>(), key, this@bindToKey)

@JvmName("bindToKeyResourceEnum")
internal inline infix fun <T, reified E> KMutableProperty1<T, E>.bindToKey(key: String) //
where E : Enum<E>, E : ResourceEnum = PropertyMapBinding(resourceEnumConverter<E>(), key, this)

/**
 * Constructs a MapBinding for the receiver property, based on an explicitly supplied
 * ValueConverter.
 */
internal infix fun <T, V> KMutableProperty1<T, V>.bindVia(
  converter: ValueConverter<V>
): BindVia<T> =
  object : BindVia<T> {
    override fun toKey(key: String) = PropertyMapBinding(converter, key, this@bindVia)
  }

internal interface BindVia<T> {
  infix fun toKey(key: String): MapBinding<T>
}

internal class CompositeBinding<T>(vararg val bindings: MapBinding<T>) : MapBinding<T> {
  override fun read(destination: T, source: Map<String, String>) {
    for (serializer in bindings) {
      serializer.read(destination, source)
    }
  }

  override fun write(source: T, destination: MutableMap<String, String>) {
    for (serializer in bindings) {
      serializer.write(source, destination)
    }
  }
}

/** Defines a conversion of a type to and from String. */
internal interface ValueConverter<V> {
  /**
   * Parses the given String as an instance of type V. Returns null if it cannot be parsed as a V.
   */
  fun fromString(string: String): V?

  /** Converts the given V to a String. */
  fun toString(value: V): String
}

internal object BooleanConverter : ValueConverter<Boolean> {
  override fun fromString(string: String): Boolean = string == "yes"

  override fun toString(value: Boolean): String = if (value) "yes" else "no"
}

internal object IntConverter : ValueConverter<Int> {
  override fun fromString(string: String): Int? = string.toIntOrNull()

  override fun toString(value: Int): String = value.toString()
}

internal object StringConverter : ValueConverter<String> {
  override fun fromString(string: String): String = string

  override fun toString(value: String): String = value
}

/**
 * Converts Storage to/from strings.
 *
 * @param defaultUnit the unit to assume if no suffix is present when reading; when writing, this
 *   unit will be left implicit
 * @param allowUnitSuffix if false, all values will be converted into [defaultUnit] and no suffix
 *   will be written; otherwise, the suffix will be determined by [Storage.getAppropriateUnits].
 */
internal class StorageConverter(
  val defaultUnit: Storage.Unit = Storage.Unit.MiB,
  val allowUnitSuffix: Boolean = true,
) : ValueConverter<Storage> {
  init {
    check(defaultUnit != Storage.Unit.TiB)
  }

  override fun fromString(string: String): Storage? =
    Storage.getStorageFromString(string, defaultUnit)

  override fun toString(value: Storage): String {
    val unit =
      if (allowUnitSuffix) value.appropriateUnits.coerceAtMost(Storage.Unit.GiB) else defaultUnit
    val unitString = if (unit == defaultUnit) "" else unit.toString().substring(0, 1)
    return "${value.getSizeAsUnit(unit)}$unitString"
  }
}

internal inline fun <reified T> enumConverter() where T : Enum<T>, T : ConfigEnum =
  EnumConverter(T::class.java, ConfigEnum::getAsParameter)

internal inline fun <reified T> resourceEnumConverter() where T : Enum<T>, T : ResourceEnum =
  EnumConverter(T::class.java, ResourceEnum::getShortDisplayValue)

internal class EnumConverter<E : Enum<E>>(val enumClass: Class<E>, val parameter: E.() -> String) :
  ValueConverter<E> {
  override fun fromString(string: String): E? {
    val lowercase = string.lowercase()
    return enumClass.enumConstants.firstOrNull { it.parameter().lowercase() == lowercase }
  }

  override fun toString(value: E): String = value.parameter().lowercase()
}
