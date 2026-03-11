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

package com.android.build.gradle.internal.dependency

import org.gradle.api.Project
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.DocsType

// some androidx sources in the past published with this attribute
private const val FAKE_SOURCE = "fake-sources"

fun configureSourceTypeAttribute(project: Project) {
  project.dependencies.attributesSchema.attribute(DocsType.DOCS_TYPE_ATTRIBUTE).also {
    it.compatibilityRules.add(SourceTypeCompatibilityRule::class.java)
  }
}

class SourceTypeCompatibilityRule : AttributeCompatibilityRule<DocsType> {
  override fun execute(details: CompatibilityCheckDetails<DocsType>) =
    with(details) { if (consumerValue?.name == DocsType.SOURCES && producerValue?.name == FAKE_SOURCE) compatible() }
}
