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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable

/**
 * Specialization of [FlatSourceDirectoriesImpl] for Java and Kotlin source set.
 *
 * The KAPT and KSP generators need to be stored in dedicated storage so we can provide all user's source generated folders without creating
 * a circular dependency.
 */
open class FlatSourceDirectoriesForJavaImpl(name: String, variantServices: VariantServices, variantDslFilters: PatternFilterable?) :
  FlatSourceDirectoriesImpl(name, variantServices, variantDslFilters) {

  /**
   * Internal API to retrieve all registered source generators filtering out the KSP and KAPT generators. This is used by the KSP task and
   * hence filtering prevents a circular dependency (KAPT also needs the KSP java generated sources so it can creates bindings)
   */
  fun allButKspAndKaptGenerators(): Provider<List<Directory>> {
    return directories
  }

  // Storage for ksp generated java source folders
  private val kspGenerator = variantServices.newListPropertyForInternalUse(Directory::class.java)
  // Storage for KAPT generated java source folders
  private val kaptGenerator = variantServices.newListPropertyForInternalUse(Directory::class.java)

  /** Provide ksp and kapt generators as a zipped provider. */
  private val internalGenerators: Provider<List<Directory>> by lazy {
    // zip requires that the two providers are not null or empty, therefore force some
    // initialization of the ksp Provider instance in case it is not set so that the merging
    // happens in all cases even when KSP processing is not active in this build/variant.
    kaptGenerator.zip(kspGenerator) { kaptDirectories: List<Directory>, kspDirectories: List<Directory> ->
      // check if ksp is active. If it is set to the project build directory, that means
      // that KSP is not active in this build.
      return@zip kaptDirectories + kspDirectories
    }
  }

  /** Provide all generators, that's the user's registered generators and the KAPT/KSP ones. */
  override val all: Provider<out Collection<Directory>> by lazy {
    directories.zip(internalGenerators) { genericDirectories: List<Directory>, generatorDirectories: List<Directory> ->
      // Create a new list containing all elements from the directories and the internal
      // generators.
      genericDirectories + generatorDirectories
    }
  }

  //
  // Internal APIs.
  //
  override fun addSource(directoryEntry: DirectoryEntry) {
    variantSources.add(directoryEntry)
    // depending on the kind of generators, store it in the right location.
    when (directoryEntry.kind) {
      DirectoryEntry.Kind.KSP -> {
        kspGenerator.add((directoryEntry as TaskProviderBasedDirectoryEntryImpl).directoryProvider)
      }
      DirectoryEntry.Kind.KAPT -> {
        // because KAPT uses the old variant API which mean that the generated source
        // folder is provided as a ConfigurableFileTree, therefore we need to extract the
        // folder as a mapped Provider to keep the task dependency embedded.
        kaptGenerator.set(
          (directoryEntry as ConfigurableFileTreeBasedDirectoryEntryImpl).asListOfDirectories(variantServices.projectInfo.projectDirectory)
        )
      }
      DirectoryEntry.Kind.GENERIC -> {
        directoryEntry.addTo(variantServices.projectInfo.projectDirectory, directories)
      }
    }
  }
}
