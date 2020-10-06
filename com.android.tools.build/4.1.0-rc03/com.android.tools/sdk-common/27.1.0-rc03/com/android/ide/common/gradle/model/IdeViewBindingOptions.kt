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
package com.android.ide.common.gradle.model

import com.android.builder.model.ViewBindingOptions
import java.io.Serializable
import java.util.Objects

class IdeViewBindingOptions : ViewBindingOptions, Serializable {
  val enabled : Boolean
  val hashCode : Int

  constructor(model: ViewBindingOptions) {
    enabled = model.isEnabled
    hashCode = calculateHashCode()
  }

  // Used for serialization by the IDE.
  constructor() {
    enabled = false
    hashCode = 0
  }

  override fun isEnabled(): Boolean = enabled

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    if (other !is IdeViewBindingOptions) return false

    return Objects.equals(enabled, other.enabled)
  }

  override fun hashCode(): Int = hashCode

  override fun toString(): String = "IdeViewBindingOptions{enabled=$enabled}"

  private fun calculateHashCode() : Int = Objects.hash(enabled)
}