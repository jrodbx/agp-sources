/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.DependencySelection
import com.android.build.api.dsl.ProductFlavorDimensionSpec
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty

abstract class DependencySelectionImpl
@Inject
constructor(internal val dslServices: DslServices, internal val objectFactory: ObjectFactory) : DependencySelection {

  val productFlavorsMap: MapProperty<String, List<String>> =
    objectFactory.mapProperty(String::class.java, List::class.java as Class<List<String>>)

  override val selectBuildTypeFrom: ListProperty<String> =
    objectFactory.listProperty(String::class.java).also {
      it.value(listOf("release"))
      it.finalizeValueOnRead()
    }

  override fun productFlavorDimension(dimension: String, action: Action<ProductFlavorDimensionSpec>) {
    val spec = ProductFlavorDimensionSpecImpl(objectFactory)
    action.execute(spec)
    productFlavorsMap.put(dimension, spec.selectFrom)
  }

  fun getDimensions(): Map<String, List<String>> = productFlavorsMap.get().toMap()
}

class ProductFlavorDimensionSpecImpl(objectFactory: ObjectFactory) : ProductFlavorDimensionSpec {
  override val selectFrom: ListProperty<String> = objectFactory.listProperty(String::class.java)
}
