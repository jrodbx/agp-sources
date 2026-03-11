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

import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.attributes.ProductFlavorAttr.Companion.of
import com.android.build.gradle.internal.core.dsl.MultiVariantComponentDslInfo
import com.android.builder.errors.IssueReporter
import com.google.common.collect.Maps
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.model.ObjectFactory

open class VariantAwareDependenciesBuilder(
  val project: Project,
  val issueReporter: IssueReporter,
  val dslInfo: MultiVariantComponentDslInfo,
) {

  protected fun getConsumptionFlavorAttributes(
    flavorSelection: Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr>?
  ): Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> {
    return getFlavorAttributes(flavorSelection, false)
  }

  /**
   * Returns a map of Configuration attributes containing all the flavor values.
   *
   * @param flavorSelection a list of override for flavor matching or for new attributes.
   * @param addCompatibilityUnprefixedFlavorDimensionAttributes when true also add the previous un-prefixed flavor dimension attributes for
   *   compatibility
   */
  private fun getFlavorAttributes(
    flavorSelection: Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr>?,
    addCompatibilityUnprefixedFlavorDimensionAttributes: Boolean,
  ): Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> {
    val productFlavors = dslInfo.productFlavorList
    val map: MutableMap<Attribute<ProductFlavorAttr>, ProductFlavorAttr> = Maps.newHashMapWithExpectedSize(productFlavors.size)

    // during a sync, it's possible that the flavors don't have dimension names because
    // the variant manager is lenient about it.
    // In that case we're going to avoid resolving the dependencies anyway, so we can just
    // skip this.
    if (issueReporter.hasIssue(IssueReporter.Type.UNNAMED_FLAVOR_DIMENSION)) {
      return map
    }

    val objectFactory: ObjectFactory = project.getObjects()

    // first go through the product flavors and add matching attributes
    for (f in productFlavors) {
      f.dimension?.let { dimension ->
        map[of(dimension)] = objectFactory.named(ProductFlavorAttr::class.java, f.name)
        // Compatibility for e.g. the hilt plugin creates its own configuration with the
        // old-style attributes
        if (addCompatibilityUnprefixedFlavorDimensionAttributes) {
          map[Attribute.of(dimension, ProductFlavorAttr::class.java)] = objectFactory.named(ProductFlavorAttr::class.java, f.name)
        }
      }
    }

    // then go through the override or new attributes.
    if (flavorSelection != null) {
      map.putAll(flavorSelection)
    }

    return map
  }
}
