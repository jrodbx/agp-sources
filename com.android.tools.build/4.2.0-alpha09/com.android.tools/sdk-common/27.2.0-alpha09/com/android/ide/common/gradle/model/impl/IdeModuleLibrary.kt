/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl

import com.android.ide.common.gradle.model.IdeLibrary
import com.google.common.annotations.VisibleForTesting
import java.io.File

/**
 * The implementation of IdeLibrary for modules.
 **/
data class IdeModuleLibrary(
  val core: IdeModuleLibraryCore,
  override val isProvided: Boolean
) : IdeLibrary by core {
  @VisibleForTesting
  constructor(
    projectPath: String,
    artifactAddress: String,
    buildId: String?
  ) : this(IdeModuleLibraryCore(projectPath, artifactAddress, buildId), isProvided = false)
}

data class IdeModuleLibraryCore(
  override val artifactAddress: String,
  override val buildId: String?,
  override val projectPath: String?,
  override val variant: String?,
  override val folder: File?,
  override val lintJar: String?

) : IdeLibrary {

  // Used for serialization by the IDE.
  constructor() : this(
    artifactAddress = "",
    buildId = null,
    projectPath = null,
    variant = null,
    folder = null,
    lintJar = null
  )

  constructor(
    projectPath: String,
    artifactAddress: String,
    buildId: String?
  ) : this(
    artifactAddress = artifactAddress,
    buildId = buildId,
    projectPath = projectPath,
    variant = null,
    folder = null,
    lintJar = null
  )

  override val type: IdeLibrary.LibraryType
    get() = IdeLibrary.LibraryType.LIBRARY_MODULE

  override val artifact: File
    get() = throw unsupportedMethodForModuleLibrary("getArtifact()")

  override val manifest: String
    get() = throw unsupportedMethodForModuleLibrary("getManifest")

  override val jarFile: String
    get() = throw unsupportedMethodForModuleLibrary("getJarFile")

  override val compileJarFile: String
    get() = throw unsupportedMethodForModuleLibrary("getCompileJarFile")

  override val resFolder: String
    get() = throw unsupportedMethodForModuleLibrary("getResFolder")

  override val resStaticLibrary: File?
    get() = throw unsupportedMethodForModuleLibrary("getResStaticLibrary")

  override val assetsFolder: String
    get() = throw unsupportedMethodForModuleLibrary("getAssetsFolder")

  override val localJars: Collection<String>
    get() = throw unsupportedMethodForModuleLibrary("getLocalJars")

  override val jniFolder: String
    get() = throw unsupportedMethodForModuleLibrary("getJniFolder")

  override val aidlFolder: String
    get() = throw unsupportedMethodForModuleLibrary("getAidlFolder")

  override val renderscriptFolder: String
    get() = throw unsupportedMethodForModuleLibrary("getRenderscriptFolder")

  override val proguardRules: String
    get() = throw unsupportedMethodForModuleLibrary("getProguardRules")

  override val externalAnnotations: String
    get() = throw unsupportedMethodForModuleLibrary("getExternalAnnotations")

  override val publicResources: String
    get() = throw unsupportedMethodForModuleLibrary("getPublicResources")

  override val symbolFile: String
    get() = throw unsupportedMethodForModuleLibrary("getSymbolFile")

  override val isProvided: Nothing
    get() = error("abstract")
}

private fun unsupportedMethodForModuleLibrary(methodName: String): UnsupportedOperationException {
  return UnsupportedOperationException("$methodName() cannot be called when getType() returns LIBRARY_MODULE")
}
