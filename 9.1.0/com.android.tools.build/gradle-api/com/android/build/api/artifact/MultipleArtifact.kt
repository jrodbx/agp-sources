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

package com.android.build.api.artifact

import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile

/**
 * Public [Artifact] for Android Gradle plugin.
 *
 * This type inherits [Artifact.Multiple]. For single artifacts, see [SingleArtifact].
 *
 * All methods in [Artifacts] should be supported with any subclass of this class.
 */
sealed class MultipleArtifact<FileTypeT : FileSystemLocation>(kind: ArtifactKind<FileTypeT>, category: Category = Category.INTERMEDIATES) :
  Artifact.Multiple<FileTypeT>(kind, category) {

  /**
   * Text files with additional ProGuard rules to be used to determine which classes are compiled into the main dex file.
   *
   * If set, rules from these files are used in combination with the default rules used by the build system.
   *
   * Initialized from DSL [com.android.build.api.dsl.VariantDimension.multiDexKeepProguard]
   */
  object MULTIDEX_KEEP_PROGUARD : MultipleArtifact<RegularFile>(FILE, Category.SOURCES), Replaceable, Transformable

  /**
   * Directories with native debug metadata
   *
   * If set, the debug metadata files(with extension .dbg) are combined with extracted debug metadata and packaged together.
   */
  object NATIVE_DEBUG_METADATA : MultipleArtifact<Directory>(DIRECTORY), Replaceable, Appendable, Transformable

  /**
   * Directories with debug symbol table
   *
   * If set, the debug symbol table files(with extension .sym) are combined with extracted debug symbol tables and packaged together.
   */
  object NATIVE_SYMBOL_TABLES : MultipleArtifact<Directory>(DIRECTORY), Replaceable, Appendable, Transformable

  /**
   * Directories containing generated code that will be added to compile classpath for the module.
   *
   * It is not possible to register such code generator through the DSL, one must use this Variant API.
   *
   * Classes added to this artifact will be used to compile Java classes and Kotlin classes present in the module. This is however limited
   * to using the built-in kotlin compiler and is not available when using the deprecated KAGP plugin from JetBrains.
   *
   * Classes added to this artifacts will also be added to the [ScopedArtifact.CLASSES] for the
   * [com.android.build.api.variant.ScopedArtifacts.Scope.PROJECT] scope.
   */
  @Incubating object PRE_COMPILATION_CLASSES : MultipleArtifact<Directory>(ArtifactKind.DIRECTORY), Replaceable, Appendable, Transformable
}
