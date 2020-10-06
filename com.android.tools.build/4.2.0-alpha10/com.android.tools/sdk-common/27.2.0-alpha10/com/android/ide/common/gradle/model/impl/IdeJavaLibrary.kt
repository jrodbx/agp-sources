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
import java.io.Serializable

/**
 * The implementation of IdeLibrary for Java libraries.
 **/
data class IdeJavaLibrary(
  val core: IdeJavaLibraryCore,
  override val isProvided: Boolean
) : IdeLibrary by core, Serializable {
  @VisibleForTesting
  constructor(
    artifactAddress: String,
    artifact: File,
    isProvided: Boolean
  ) : this(IdeJavaLibraryCore(artifactAddress, artifact), isProvided)
}

data class IdeJavaLibraryCore(
  override val artifactAddress: String,
  override val artifact: File
) : IdeLibrary, Serializable {
  // Used for serialization by the IDE.
  internal constructor() : this(
    artifactAddress = "",
    artifact = File("")
  )

  override val type: IdeLibrary.LibraryType
    get() = IdeLibrary.LibraryType.LIBRARY_JAVA

  override val variant: String?
    get() = throw unsupportedMethodForJavaLibrary("getVariant")

  override val buildId: String?
    get() = throw unsupportedMethodForJavaLibrary("getBuildId")

  override val projectPath: String
    get() = throw unsupportedMethodForJavaLibrary("getProjectPath")

  override val folder: File?
    get() = null

  override val manifest: String
    get() = throw unsupportedMethodForJavaLibrary("getManifest")

  override val jarFile: String
    get() = throw unsupportedMethodForJavaLibrary("getJarFile")

  override val compileJarFile: String
    get() = throw unsupportedMethodForJavaLibrary("getCompileJarFile")

  override val resFolder: String
    get() = throw unsupportedMethodForJavaLibrary("getResFolder")

  override val resStaticLibrary: File?
    get() = throw unsupportedMethodForJavaLibrary("getResStaticLibrary")

  override val assetsFolder: String
    get() = throw unsupportedMethodForJavaLibrary("getAssetsFolder")

  override val localJars: Collection<String>
    get() = throw unsupportedMethodForJavaLibrary("getLocalJars")

  override val jniFolder: String
    get() = throw unsupportedMethodForJavaLibrary("getJniFolder")

  override val aidlFolder: String
    get() = throw unsupportedMethodForJavaLibrary("getAidlFolder")

  override val renderscriptFolder: String
    get() = throw unsupportedMethodForJavaLibrary("getRenderscriptFolder")

  override val proguardRules: String
    get() = throw unsupportedMethodForJavaLibrary("getProguardRules")

  override val lintJar: String?
    get() = null

  override val externalAnnotations: String
    get() = throw unsupportedMethodForJavaLibrary("getExternalAnnotations")

  override val publicResources: String
    get() = throw unsupportedMethodForJavaLibrary("getPublicResources")

  override val symbolFile: String
    get() = throw unsupportedMethodForJavaLibrary("getSymbolFile")

  override val isProvided: Nothing
    get() = error("abstract")
}

private fun unsupportedMethodForJavaLibrary(methodName: String): UnsupportedOperationException {
  return UnsupportedOperationException("$methodName() cannot be called when getType() returns LIBRARY_JAVA")
}
