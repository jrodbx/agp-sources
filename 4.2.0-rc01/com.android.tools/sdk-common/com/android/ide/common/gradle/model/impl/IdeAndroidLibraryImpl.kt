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

import com.android.ide.common.gradle.model.IdeAndroidLibrary
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.Serializable

/**
 * The implementation of IdeLibrary for Android libraries.
 **/
data class IdeAndroidLibraryImpl(
  val core: IdeAndroidLibraryCore,
  override val isProvided: Boolean
) : IdeAndroidLibrary by core, Serializable {
  @VisibleForTesting
  constructor(
    artifactAddress: String,
    folder: File,
    manifest: String,
    jarFile: String,
    compileJarFile: String,
    resFolder: String,
    resStaticLibrary: File?,
    assetsFolder: String,
    localJars: Collection<String>,
    jniFolder: String,
    aidlFolder: String,
    renderscriptFolder: String,
    proguardRules: String,
    lintJar: String,
    externalAnnotations: String,
    publicResources: String,
    artifact: File,
    symbolFile: String,
    isProvided: Boolean
  ) : this(
    IdeAndroidLibraryCore.create(
      artifactAddress,
      folder,
      manifest,
      jarFile,
      compileJarFile,
      resFolder,
      resStaticLibrary,
      assetsFolder,
      localJars,
      jniFolder,
      aidlFolder,
      renderscriptFolder,
      proguardRules,
      lintJar,
      externalAnnotations,
      publicResources,
      artifact,
      symbolFile,
      deduplicate = { this }
    ),
    isProvided
  )
}

data class IdeAndroidLibraryCore(
  override val artifactAddress: String,
  override val folder: File,
  private val _manifest: String,
  private val _jarFile: String,
  private val _compileJarFile: String,
  private val _resFolder: String,
  private val _resStaticLibrary: String?,
  private val _assetsFolder: String,
  private val _localJars: Collection<String>,
  private val _jniFolder: String,
  private val _aidlFolder: String,
  private val _renderscriptFolder: String,
  private val _proguardRules: String,
  private val _lintJar: String,
  private val _externalAnnotations: String,
  private val _publicResources: String,
  private val _artifact: String,
  private val _symbolFile: String
) : IdeAndroidLibrary, Serializable {

  // Used for serialization by the IDE.
  internal constructor() : this(
    artifactAddress = "",
    folder = File(""),
    _manifest = "",
    _jarFile = "",
    _compileJarFile = "",
    _resFolder = "",
    _resStaticLibrary = null,
    _assetsFolder = "",
    _localJars = mutableListOf(),
    _jniFolder = "",
    _aidlFolder = "",
    _renderscriptFolder = "",
    _proguardRules = "",
    _lintJar = "",
    _externalAnnotations = "",
    _publicResources = "",
    _artifact = "",
    _symbolFile = ""
  )

  private fun String.translate(): String = folder.resolve(this).normalize().path

  override val manifest: String get() = _manifest.translate()
  override val jarFile: String get() = _jarFile.translate()
  override val compileJarFile: String get() = _compileJarFile.translate()
  override val resFolder: String get() = _resFolder.translate()
  override val resStaticLibrary: File? get() = _resStaticLibrary?.translate()?.let(::File)
  override val assetsFolder: String get() = _assetsFolder.translate()
  override val localJars: Collection<String> get() = _localJars.map { it.translate() }
  override val jniFolder: String get() = _jniFolder.translate()
  override val aidlFolder: String get() = _aidlFolder.translate()
  override val renderscriptFolder: String get() = _renderscriptFolder.translate()
  override val proguardRules: String get() = _proguardRules.translate()
  override val lintJar: String get() = _lintJar.translate()
  override val externalAnnotations: String get() = _externalAnnotations.translate()
  override val publicResources: String get() = _publicResources.translate()
  override val artifact: File get() = File(_artifact.translate())
  override val symbolFile: String get() = _symbolFile.translate()

  override val variant: String?
    get() = throw unsupportedMethodForAndroidLibrary("getVariant")

  override val buildId: String?
    get() = throw unsupportedMethodForAndroidLibrary("getBuildId")

  override val projectPath: String
    get() = throw unsupportedMethodForAndroidLibrary("getProjectPath")

  override val isProvided: Nothing
    get() = error("abstract")

  companion object {
    fun create(
      artifactAddress: String,
      folder: File,
      manifest: String,
      jarFile: String,
      compileJarFile: String,
      resFolder: String,
      resStaticLibrary: File?,
      assetsFolder: String,
      localJars: Collection<String>,
      jniFolder: String,
      aidlFolder: String,
      renderscriptFolder: String,
      proguardRules: String,
      lintJar: String,
      externalAnnotations: String,
      publicResources: String,
      artifact: File,
      symbolFile: String,
      deduplicate: String.() -> String
    ): IdeAndroidLibraryCore {
      fun String.makeRelative(): String = File(this).relativeToOrSelf(folder).path.deduplicate()
      fun File.makeRelative(): String = this.relativeToOrSelf(folder).path.deduplicate()

      return IdeAndroidLibraryCore(
        artifactAddress = artifactAddress,
        folder = folder,
        _manifest = manifest.makeRelative(),
        _jarFile = jarFile.makeRelative(),
        _compileJarFile = compileJarFile.makeRelative(),
        _resFolder = resFolder.makeRelative(),
        _resStaticLibrary = resStaticLibrary?.makeRelative(),
        _assetsFolder = assetsFolder.makeRelative(),
        _localJars = localJars.map { it.makeRelative() },
        _jniFolder = jniFolder.makeRelative(),
        _aidlFolder = aidlFolder.makeRelative(),
        _renderscriptFolder = renderscriptFolder.makeRelative(),
        _proguardRules = proguardRules.makeRelative(),
        _lintJar = lintJar.makeRelative(),
        _externalAnnotations = externalAnnotations.makeRelative(),
        _publicResources = publicResources.makeRelative(),
        _artifact = artifact.makeRelative(),
        _symbolFile = symbolFile.makeRelative()
      )
    }
  }
}

private fun unsupportedMethodForAndroidLibrary(methodName: String): UnsupportedOperationException =
  UnsupportedOperationException("$methodName() cannot be called when getType() returns ANDROID_LIBRARY")
