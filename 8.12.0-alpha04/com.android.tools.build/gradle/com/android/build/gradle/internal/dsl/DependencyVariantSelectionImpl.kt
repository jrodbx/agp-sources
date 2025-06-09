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

import com.android.build.api.dsl.DependencyVariantSelection
import com.android.build.api.dsl.LocalDependencySelection
import com.android.build.api.dsl.ProductFlavorDimensionSpec
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import javax.inject.Inject

/**
 * Implementation of the deprecated dependencyVariantSelection block in KMP-Android DSL
 * It just delegates to the given replacement block [LocalDependencySelectionImpl]
 */
abstract class DependencyVariantSelectionImpl@Inject constructor(
    internal val dslServices: DslServices,
    internal val delegate: LocalDependencySelectionImpl,
    internal val objectFactory: ObjectFactory
): DependencyVariantSelection {
    @Deprecated("Replaced by LocalDependencySelection.selectBuildTypeFrom")
    override val buildTypes: ListProperty<String>
        get() = delegate.selectBuildTypeFrom

    @Deprecated("Replaced by LocalDependencySelection.productFlavorDimension")
    override val productFlavors: MapProperty<String, List<String>>
        get() = delegate.productFlavorsMap
}

abstract class LocalDependencySelectionImpl@Inject constructor(
    internal val dslServices: DslServices,
    internal val objectFactory: ObjectFactory
): LocalDependencySelection {

    val productFlavorsMap: MapProperty<String, List<String>> = objectFactory.mapProperty(
        String::class.java,
        List::class.java as Class<List<String>>
    )

    override val selectBuildTypeFrom: ListProperty<String> = objectFactory.listProperty(String::class.java).also {
        it.value(listOf("release"))
        it.finalizeValueOnRead()
    }

    override fun productFlavorDimension(
        dimension: String,
        action: Action<ProductFlavorDimensionSpec>
    ) {
        val spec =  ProductFlavorDimensionSpecImpl(objectFactory)
        action.execute(spec)
        productFlavorsMap.put(dimension, spec.selectFrom)
    }

    fun getDimensions(): Map<String, List<String>> = productFlavorsMap.get().toMap()
}

class ProductFlavorDimensionSpecImpl(
    objectFactory: ObjectFactory
) : ProductFlavorDimensionSpec {
    override val selectFrom: ListProperty<String> = objectFactory.listProperty(String::class.java)
}
