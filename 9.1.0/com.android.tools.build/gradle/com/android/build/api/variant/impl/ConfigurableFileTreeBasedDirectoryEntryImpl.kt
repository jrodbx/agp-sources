/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable

/**
 * Implementation of [DirectoryEntry] based on a [ConfigurableFileTree]. This is for backward compatibility of the old variant API.
 *
 * In order to treat the KSP and KAPT outputs differently, we need to pass the [DirectoryEntry.Kind] to identify those explicitly.
 *
 * Do not use without explicitly stating the reasons as this class should be removed once the old variant API is removed.
 */
class ConfigurableFileTreeBasedDirectoryEntryImpl(
  override val name: String,
  private val configurableFileTree: ConfigurableFileTree,
  override val kind: DirectoryEntry.Kind,
) : DirectoryEntry {

  /**
   * [ConfigurableFileTree] will returns its entries as files, instead of directories but what we need is the top level directory. the
   * [ConfigurableFileTree] is always based on a single directory but we can't return it directly as there is not a [Provider] based API
   * that will keep the producer task dependency. So instead of using [ConfigurableFileTree.getDir], I must use
   * [ConfigurableFileTree.getElements] which does keep the task dependency, yet provide a mapping from files to directories. The mapping
   * will in fact always return the top level directory and we just need to have a single one of them.
   */
  internal fun asListOfDirectories(projectDir: Directory): Provider<List<Directory>> =
    configurableFileTree.elements.map { _ -> listOf(projectDir.dir(configurableFileTree.dir.absolutePath)) }

  override fun addTo(projectDir: Directory, listProperty: ListProperty<Directory>) {
    listProperty.addAll(asListOfDirectories(projectDir))
  }

  override fun addTo(projectDir: Directory, into: ConfigurableFileCollection) {
    into.from(configurableFileTree.elements)
  }

  override val isGenerated: Boolean = true
  override val isUserAdded: Boolean = true
  override val shouldBeAddedToIdeModel: Boolean = true

  override val filter: PatternFilterable?
    get() = null

  override fun asFileTree(fileTreeCreator: () -> ConfigurableFileTree) = configurableFileTree.elements.map { listOf(configurableFileTree) }

  override fun asFileTreeWithoutTaskDependency(fileTreeCreator: () -> ConfigurableFileTree): List<ConfigurableFileTree> =
    listOf(configurableFileTree)

  override fun makeDependentOf(task: Task) {
    task.dependsOn(configurableFileTree.elements)
  }
}
