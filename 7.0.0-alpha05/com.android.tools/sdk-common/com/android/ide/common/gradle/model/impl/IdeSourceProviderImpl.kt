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

import com.android.ide.common.gradle.model.IdeSourceProvider
import java.io.File
import java.io.Serializable

data class IdeSourceProviderImpl(
  private val myName: String,
  private val myFolder: File?,
  private val myManifestFile: String,
  private val myJavaDirectories: Collection<String>,
  private val myResourcesDirectories: Collection<String>,
  private val myAidlDirectories: Collection<String>,
  private val myRenderscriptDirectories: Collection<String>,
  private val myResDirectories: Collection<String>,
  private val myAssetsDirectories: Collection<String>,
  private val myJniLibsDirectories: Collection<String>,
  private val myShadersDirectories: Collection<String>,
  private val myMlModelsDirectories: Collection<String>
) : Serializable, IdeSourceProvider {
  private fun String.translate(): File = (myFolder?.resolve(this) ?: File(this)).normalize()
  private fun Collection<String>.translate(): Collection<File> = map { it.translate() }

  // Used for serialization by the IDE.
  constructor() : this(
    myName = "",
    myFolder = File(""),
    myManifestFile = "",
    myJavaDirectories = mutableListOf(),
    myResourcesDirectories = mutableListOf(),
    myAidlDirectories = mutableListOf(),
    myRenderscriptDirectories = mutableListOf(),
    myResDirectories = mutableListOf(),
    myAssetsDirectories = mutableListOf(),
    myJniLibsDirectories = mutableListOf(),
    myShadersDirectories = mutableListOf(),
    myMlModelsDirectories = mutableListOf()
  )

  override val name: String get() = myName
  override val manifestFile: File get() = myManifestFile.translate()
  override val javaDirectories: Collection<File> get() = myJavaDirectories.translate()
  override val resourcesDirectories: Collection<File> get() = myResourcesDirectories.translate()
  override val aidlDirectories: Collection<File> get() = myAidlDirectories.translate()
  override val renderscriptDirectories: Collection<File> get() = myRenderscriptDirectories.translate()
  override val resDirectories: Collection<File> get() = myResDirectories.translate()
  override val assetsDirectories: Collection<File> get() = myAssetsDirectories.translate()
  override val jniLibsDirectories: Collection<File> get() = myJniLibsDirectories.translate()
  override val shadersDirectories: Collection<File> get() = myShadersDirectories.translate()
  override val mlModelsDirectories: Collection<File> get() = myMlModelsDirectories.translate()
}
