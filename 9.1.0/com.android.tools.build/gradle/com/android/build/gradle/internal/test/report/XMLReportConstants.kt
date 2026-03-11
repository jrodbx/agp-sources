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

package com.android.build.gradle.internal.test.report

/** XML element attributes found in test reports. */
internal const val ATTR_NAME = "name"
internal const val ATTR_VALUE = "value"
internal const val ATTR_CLASSNAME = "classname"

/** Keys for `<property>` tags within the report. */
internal const val KEY_MODULE_PATH = "modulePath"
internal const val KEY_TEST_SUITE_NAME = "testSuiteName"
internal const val KEY_TESTED_VARIANT_NAME = "testedVariantName"

/** XML element tags found in test reports. */
internal const val TAG_PROPERTY = "property"
internal const val TAG_TESTCASE = "testcase"
internal const val TAG_SKIPPED = "skipped"
internal const val TAG_FAILURE = "failure"
internal const val TAG_ERROR = "error"

/** Test result status values. */
internal const val STATUS_PASS = "pass"
internal const val STATUS_SKIPPED = "skipped"
internal const val STATUS_FAIL = "fail"

/** File extensions. */
internal const val EXT_XML = "xml"
