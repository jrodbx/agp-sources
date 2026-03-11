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

package com.android.build.gradle.internal.coverage.renderer.xmlparser.utils

import com.android.build.gradle.internal.coverage.renderer.data.CoverageInfo
import org.w3c.dom.Element
import org.w3c.dom.NodeList

internal val NodeList.elements: List<Element>
  get() = (0 until length).mapNotNull { item(it) as? Element }

internal fun findProperty(propertiesNode: Element?, key: String): String? {
  if (propertiesNode == null) return null
  return propertiesNode.getElementsByTagName(TAG_PROPERTY).elements.find { it.getAttribute(ATTR_NAME) == key }?.getAttribute(ATTR_VALUE)
}

internal fun parseSingleCounter(counters: List<Element>, type: String): CoverageInfo {
  val counter = counters.find { it.getAttribute(ATTR_TYPE) == type } ?: return CoverageInfo(0, 0, 0)
  val missed = counter.getAttribute(ATTR_MISSED).toInt()
  val covered = counter.getAttribute(ATTR_COVERED).toInt()
  val total = missed + covered
  return CoverageInfo(calculatePercent(covered, total), covered, total)
}

internal fun calculatePercent(covered: Int, total: Int): Int {
  if (total == 0) return 0
  return (covered * 100) / total
}
