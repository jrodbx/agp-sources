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

/** XML element attributes found in JaCoCo reports. */
internal const val ATTR_COVERED = "covered"
internal const val ATTR_COVERED_BRANCHES = "cb"
internal const val ATTR_COVERED_INSTRUCTIONS = "ci"
internal const val ATTR_DESC = "desc"
internal const val ATTR_LINE = "line"
internal const val ATTR_LINE_NUMBER = "nr"
internal const val ATTR_MISSED = "missed"
internal const val ATTR_MISSED_BRANCHES = "mb"
internal const val ATTR_MISSED_INSTRUCTIONS = "mi"
internal const val ATTR_NAME = "name"
internal const val ATTR_PATH = "path"
internal const val ATTR_SOURCE_FILENAME = "sourcefilename"
internal const val ATTR_TYPE = "type"
internal const val ATTR_VALUE = "value"

/** Values for the 'type' attribute of a `<counter>` element. */
internal const val COUNTER_TYPE_BRANCH = "BRANCH"
internal const val COUNTER_TYPE_INSTRUCTION = "INSTRUCTION"
internal const val COUNTER_TYPE_LINE = "LINE"

/** Keys for `<property>` tags within the `<properties>` section of the report. */
internal const val KEY_MODULE_NAME = "moduleName"
internal const val KEY_TEST_SUITE_NAME = "testSuiteName"
internal const val KEY_VARIANT_NAME = "testedVariantName"

/** XML element tags found in JaCoCo reports. */
internal const val TAG_CLASS = "class"
internal const val TAG_COUNTER = "counter"
internal const val TAG_FILE = "file"
internal const val TAG_LINE = "line"
internal const val TAG_METHOD = "method"
internal const val TAG_PACKAGE = "package"
internal const val TAG_PROPERTIES = "properties"
internal const val TAG_PROPERTY = "property"
internal const val TAG_SOURCE_FILE = "sourcefile"
internal const val TAG_SOURCES = "sources"

/** Specific string values used within the report's content or for default naming. */
internal const val VALUE_AGGREGATED = "Aggregated"
internal const val VALUE_DEFAULT = "default"
internal const val VALUE_UNKNOWN = "unknown"
