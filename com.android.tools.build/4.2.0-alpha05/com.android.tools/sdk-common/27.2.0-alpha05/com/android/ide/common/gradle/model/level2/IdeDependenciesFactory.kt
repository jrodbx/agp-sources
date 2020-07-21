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
package com.android.ide.common.gradle.model.level2

import com.android.builder.model.AndroidLibrary
import com.android.builder.model.BaseArtifact
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaLibrary
import com.android.ide.common.gradle.model.IdeLibraries
import com.google.common.collect.ImmutableList
import java.io.File

/** Creates [IdeDependencies] from [BaseArtifact].  */
class IdeDependenciesFactory {
  private val libraryFactory = IdeLibraryFactory()
  private val buildFolderPaths = BuildFolderPaths()

  /**
   * Stores the [buildFolder] path for the given [moduleGradlePath] and [buildId].
   */
  fun addBuildFolderPath(buildId: String, moduleGradlePath: String, buildFolder: File) {
    buildFolderPaths.addBuildFolderMapping(buildId, moduleGradlePath, buildFolder)
  }

  /**
   * Set the build identifier of root project.
   */
  fun setRootBuildId(rootBuildId: String) {
    buildFolderPaths.setRootBuildId(rootBuildId)
  }

  /**
   * Create [IdeDependencies] from [BaseArtifact].
   */
  fun create(artifact: BaseArtifact): IdeDependencies {
    return createFromDependencies(artifact.dependencies)
  }

  /** Call this method on level 1 Dependencies model.  */
  private fun createFromDependencies(dependencies: Dependencies): IdeDependencies {
    val worker = Worker(dependencies)
    return worker.createInstance()
  }

  private inner class Worker(private val dependencies: Dependencies) {
    // Map from unique artifact address to level2 library instance. The library instances are
    // supposed to be shared by all artifacts. When creating IdeLevel2Dependencies, check if current library is available in this map,
    // if it's available, don't create new one, simple add reference to it. If it's not available, create new instance and save
    // to this map, so it can be reused the next time when the same library is added.
    private val librariesById = mutableMapOf<String, IdeLibrary>()

    fun createInstance(): IdeDependencies {
      val visited = mutableSetOf<String>()
      populateAndroidLibraries(dependencies.libraries, visited)
      populateJavaLibraries(dependencies.javaLibraries, visited)
      populateModuleDependencies(dependencies, visited)
      val jars: Collection<File> = try {
        dependencies.runtimeOnlyClasses
      }
      catch (e: UnsupportedOperationException) {
        // Gradle older than 3.4.
        emptyList()
      }
      return createInstance(visited, jars)
    }

    private fun populateModuleDependencies(dependencies: Dependencies, visited: MutableSet<String>) {
      try {
        for (identifier in dependencies.javaModules) {
          createModuleLibrary(
            visited,
            identifier.projectPath,
            IdeLibraries.computeAddress(identifier),
            identifier.buildId)
        }
      }
      catch (ignored: UnsupportedOperationException) {
        // Dependencies::getJavaModules is available for AGP 3.1+. Use
        // Dependencies::getProjects for the old plugins.
        for (projectPath in dependencies.projects) {
          createModuleLibrary(visited, projectPath, projectPath, null)
        }
      }
    }

    private fun createModuleLibrary(
      visited: MutableSet<String>,
      projectPath: String,
      artifactAddress: String,
      buildId: String?
    ) {
      if (!visited.contains(artifactAddress)) {
        visited.add(artifactAddress)
        librariesById.computeIfAbsent(artifactAddress) { libraryFactory.create(projectPath, artifactAddress, buildId) }
      }
    }

    private fun populateAndroidLibraries(
      androidLibraries: Collection<AndroidLibrary>,
      visited: MutableSet<String>
    ) {
      for (androidLibrary in androidLibraries) {
        val address = IdeLibraries.computeAddress(androidLibrary)
        if (!visited.contains(address)) {
          visited.add(address)
          librariesById.computeIfAbsent(address) { libraryFactory.create(androidLibrary, buildFolderPaths) }
          populateAndroidLibraries(androidLibrary.libraryDependencies, visited)
          populateJavaLibraries(getJavaDependencies(androidLibrary), visited)
        }
      }
    }

    private fun getJavaDependencies(androidLibrary: AndroidLibrary): Collection<JavaLibrary> {
      return try {
        androidLibrary.javaDependencies
      }
      catch (e: UnsupportedOperationException) {
        emptyList()
      }
    }

    private fun populateJavaLibraries(
      javaLibraries: Collection<JavaLibrary>,
      visited: MutableSet<String>) {
      for (javaLibrary in javaLibraries) {
        val address = IdeLibraries.computeAddress(javaLibrary)
        if (!visited.contains(address)) {
          visited.add(address)
          librariesById.computeIfAbsent(address) { libraryFactory.create(javaLibrary) }
          populateJavaLibraries(javaLibrary.dependencies, visited)
        }
      }
    }

    private fun createInstance(
      artifactAddresses: Collection<String>,
      runtimeOnlyJars: Collection<File>
    ): IdeDependencies {
      val androidLibraries = ImmutableList.builder<IdeLibrary?>()
      val javaLibraries = ImmutableList.builder<IdeLibrary?>()
      val moduleDependencies = ImmutableList.builder<IdeLibrary?>()
      for (address in artifactAddresses) {
        val library = librariesById[address]!!
        when (library.type) {
          IdeLibrary.LibraryType.LIBRARY_ANDROID -> androidLibraries.add(library)
          IdeLibrary.LibraryType.LIBRARY_JAVA -> javaLibraries.add(library)
          IdeLibrary.LibraryType.LIBRARY_MODULE -> moduleDependencies.add(library)
          else -> throw UnsupportedOperationException("Unknown library type " + library.type)
        }
      }
      return IdeDependenciesImpl(
        androidLibraries.build(),
        javaLibraries.build(),
        moduleDependencies.build(),
        ImmutableList.copyOf(runtimeOnlyJars))
    }
  }
}