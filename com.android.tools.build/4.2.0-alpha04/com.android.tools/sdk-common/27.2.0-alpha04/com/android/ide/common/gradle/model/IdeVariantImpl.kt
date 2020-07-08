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
  private val name: String
  private val displayName: String
  private val mainArtifact: IdeAndroidArtifact
  private val extraAndroidArtifacts: Collection<IdeAndroidArtifact>
  private val extraJavaArtifacts: Collection<IdeJavaArtifact>
  private val buildType: String
  private val productFlavors: List<String>
  private val mergedFlavor: ProductFlavor
  private val testedTargetVariants: Collection<TestedTargetVariant>
  private val hashCode: Int
  private val instantAppCompatible: Boolean
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
    mergedFlavor = IdeProductFlavor()
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
    mergedFlavor = modelCache.computeIfAbsent(variant.mergedFlavor) { flavor: ProductFlavor -> IdeProductFlavor(flavor, modelCache) }
    testedTargetVariants = getTestedTargetVariants(variant, modelCache)
    instantAppCompatible = (modelVersion != null && modelVersion.isAtLeast(3, 3, 0, "alpha", 10, true) && variant.isInstantAppCompatible)
    desugaredMethods = ImmutableList.copyOf(IdeModel.copyNewPropertyNonNull({ variant.desugaredMethods }, emptyList()))
    hashCode = calculateHashCode()
  }

  override fun getName(): String = name

  override fun getDisplayName(): String = displayName

  override fun getMainArtifact(): IdeAndroidArtifact = mainArtifact

  override fun getExtraAndroidArtifacts(): Collection<IdeAndroidArtifact> = extraAndroidArtifacts

  override fun getExtraJavaArtifacts(): Collection<IdeJavaArtifact> = extraJavaArtifacts

  override fun getBuildType(): String = buildType

  override fun getProductFlavors(): List<String> = productFlavors

  override fun getMergedFlavor(): ProductFlavor = mergedFlavor

  override fun getTestedTargetVariants(): Collection<TestedTargetVariant> = testedTargetVariants

  override val testArtifacts: Collection<IdeBaseArtifact>
    get() = ImmutableList.copyOf(
      (extraAndroidArtifacts.asSequence() + extraJavaArtifacts.asSequence()).filter { it.isTestArtifact }.asIterable())

  override val androidTestArtifact: IdeAndroidArtifact? get() = extraAndroidArtifacts.firstOrNull { it.isTestArtifact }

  override val unitTestArtifact: IdeJavaArtifact? get() = extraJavaArtifacts.firstOrNull { it.isTestArtifact }

  override fun isInstantAppCompatible(): Boolean = instantAppCompatible

  override fun getDesugaredMethods(): List<String> = desugaredMethods

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
    private fun getTestedTargetVariants(variant: Variant, modelCache: ModelCache): Collection<TestedTargetVariant> {
      return try {
        IdeModel.copy(variant.testedTargetVariants, modelCache) { targetVariant: TestedTargetVariant ->
          IdeTestedTargetVariant(targetVariant)
        }
      }
      catch (e: UnsupportedOperationException) {
        emptyList()
      }
    }
  }
}
