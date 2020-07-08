/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.builder.model.AndroidArtifact
import com.android.builder.model.JavaArtifact
import com.android.builder.model.ProductFlavor
import com.android.builder.model.TestedTargetVariant
import com.android.builder.model.Variant
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.ide.common.repository.GradleVersion
import com.google.common.collect.ImmutableList
import java.io.Serializable
import java.util.Objects

/** Creates a deep copy of a [Variant].  */
class IdeVariantImpl : IdeVariant, Serializable {
  override val name: String
  override val displayName: String
  override val mainArtifact: IdeAndroidArtifact
  override val extraAndroidArtifacts: List<IdeAndroidArtifact>
  override val extraJavaArtifacts: List<IdeJavaArtifact>
  override val buildType: String
  override val productFlavors: List<String>
  override val mergedFlavor: IdeProductFlavor
  override val testedTargetVariants: List<IdeTestedTargetVariant>
  private val hashCode: Int
  override val instantAppCompatible: Boolean
  private val desugaredMethods: List<String>

  // Used for serialization by the IDE.
  @Suppress("unused")
  internal constructor() {
    name = ""
    displayName = ""
    mainArtifact = IdeAndroidArtifactImpl()
    extraAndroidArtifacts = mutableListOf()
    extraJavaArtifacts = mutableListOf()
    buildType = ""
    productFlavors = mutableListOf()
    mergedFlavor = IdeProductFlavorImpl()
    testedTargetVariants = mutableListOf()
    instantAppCompatible = false
    desugaredMethods = mutableListOf()
    hashCode = 0
  }

  constructor(variant: Variant, modelCache: ModelCache, dependenciesFactory: IdeDependenciesFactory, modelVersion: GradleVersion?) {
    name = variant.name
    displayName = variant.displayName
    mainArtifact = modelCache.computeIfAbsent(variant.mainArtifact) { artifact: AndroidArtifact ->
      IdeAndroidArtifactImpl(artifact, modelCache, dependenciesFactory, modelVersion)
    }
    extraAndroidArtifacts = IdeModel.copy(variant.extraAndroidArtifacts, modelCache) { artifact: AndroidArtifact ->
      IdeAndroidArtifactImpl(artifact, modelCache, dependenciesFactory, modelVersion)
    }
    extraJavaArtifacts = IdeModel.copy(variant.extraJavaArtifacts, modelCache) { artifact: JavaArtifact ->
      IdeJavaArtifactImpl(artifact, modelCache, dependenciesFactory, modelVersion)
    }
    buildType = variant.buildType
    productFlavors = ImmutableList.copyOf(variant.productFlavors)
    mergedFlavor = modelCache.computeIfAbsent(variant.mergedFlavor) { flavor: ProductFlavor ->
      IdeProductFlavorImpl(
        flavor,
        modelCache
      )
    }
    testedTargetVariants = getTestedTargetVariants(variant, modelCache)
    instantAppCompatible = (modelVersion != null && modelVersion.isAtLeast(3, 3, 0, "alpha", 10, true) && variant.isInstantAppCompatible)
    desugaredMethods = ImmutableList.copyOf(IdeModel.copyNewPropertyNonNull({ variant.desugaredMethods }, emptyList()))
    hashCode = calculateHashCode()
  }

  override val testArtifacts: List<IdeBaseArtifact>
    get() = ImmutableList.copyOf(
      (extraAndroidArtifacts.asSequence() + extraJavaArtifacts.asSequence()).filter { it.isTestArtifact }.asIterable())

  override val androidTestArtifact: IdeAndroidArtifact? get() = extraAndroidArtifacts.firstOrNull { it.isTestArtifact }

  override val unitTestArtifact: IdeJavaArtifact? get() = extraJavaArtifacts.firstOrNull { it.isTestArtifact }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is IdeVariantImpl) {
      return false
    }
    return (name == other.name
            && displayName == other.displayName
            && mainArtifact == other.mainArtifact
            && extraAndroidArtifacts == other.extraAndroidArtifacts
            && extraJavaArtifacts == other.extraJavaArtifacts
            && buildType == other.buildType
            && productFlavors == other.productFlavors
            && mergedFlavor == other.mergedFlavor
            && testedTargetVariants == other.testedTargetVariants
            && instantAppCompatible == other.instantAppCompatible
            && desugaredMethods == other.desugaredMethods)
  }

  override fun hashCode(): Int = hashCode

  private fun calculateHashCode(): Int {
    return Objects.hash(
      name,
      displayName,
      mainArtifact,
      extraAndroidArtifacts,
      extraJavaArtifacts,
      buildType,
      productFlavors,
      mergedFlavor,
      testedTargetVariants,
      instantAppCompatible,
      desugaredMethods)
  }

  override fun toString(): String {
    return ("IdeVariant{name='$name', displayName='$displayName', mainArtifact=$mainArtifact, " +
            "extraAndroidArtifacts=$extraAndroidArtifacts, extraJavaArtifacts=$extraJavaArtifacts, buildType='$buildType', " +
            "productFlavors=$productFlavors, mergedFlavor=$mergedFlavor, testedTargetVariants=$testedTargetVariants, " +
            "instantAppCompatible=$instantAppCompatible, desugaredMethods=$desugaredMethods}")
  }

  companion object {
    // Increase the value when adding/removing fields or when changing the
    // serialization/deserialization mechanism.
    private const val serialVersionUID = 4L
    private fun getTestedTargetVariants(variant: Variant, modelCache: ModelCache): List<IdeTestedTargetVariantImpl> {
      return try {
        IdeModel.copy(variant.testedTargetVariants, modelCache) { targetVariant: TestedTargetVariant ->
          IdeTestedTargetVariantImpl(targetVariant)
        }
      }
      catch (e: UnsupportedOperationException) {
        emptyList()
      }
    }
  }
}
