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

package com.android.tools.lint.model

import java.io.File
import java.io.IOException

/**
 * A set of variable definitions and methods to convert between files and paths using these
 * variables.
 */
class PathVariables {
  private data class PathVariable(val name: String, val dir: File) {
    override fun toString(): String {
      return "$name=$dir"
    }
  }

  private val pathVariables: MutableList<PathVariable> = mutableListOf()

  /** Adds the given [name] and [dir] pair as a path variable */
  @JvmOverloads
  fun add(name: String, dir: File, sort: Boolean = true) {
    checkPathVariableName(name)
    val variable = PathVariable(name, dir)
    pathVariables.add(variable)
    if (sort) {
      sort()
    }
  }

  /** Returns true if this path variable list contains a path variable named [name]. */
  fun contains(name: String): Boolean = get(name) != null

  /** Returns the path associated with a given path variable, if found. */
  operator fun get(name: String): File? = pathVariables.firstOrNull { it.name == name }?.dir

  /** Sorts the variables such that the most specific match is found first */
  fun sort() {
    pathVariables.sortWith(PATH_COMPARATOR)
  }

  /** Removes all the variables */
  fun clear() {
    pathVariables.clear()
  }

  /**
   * For a given [file], produce a path with variables which applies the path variable mapping and
   * root file. If [relativeTo] is specified, it will also consider that as a potential root to make
   * the path relative to (without a path variable). This allows some paths to have a local "root"
   * (this is for example useful for libraries where all the various files (classes, lint jar, etc)
   * are relative to the library root.). If [unix] is true, it will always use / as the path
   * separator.
   */
  fun toPathString(file: File, relativeTo: File? = null, unix: Boolean = false): String =
    toPathString(file.path, relativeTo?.path, unix)

  /**
   * Like [toPathString], but if the file is not inside any path variable's directory, returns null
   * instead of the absolute path.
   */
  fun toPathStringIfMatched(file: File, relativeTo: File? = null, unix: Boolean = false): String? =
    toPathStringIfMatched(file.path, relativeTo?.path, unix)

  /**
   * For a given file's full path, produces a path with variables which applies to path variable
   * mapping.
   */
  fun toPathString(fullPath: String, relativeTo: String? = null, unix: Boolean = false): String {
    return toPathStringIfMatched(fullPath, relativeTo, unix)
      ?: fullPath.let { if (unix) it.replace('\\', '/') else it }
  }

  /**
   * For a given file's full path, produces a path with variables which applies to path variable
   * mapping, unless none of the path variables match; in that case, it will return null.
   */
  fun toPathStringIfMatched(
    fullPath: String,
    relativeTo: String? = null,
    unix: Boolean = false
  ): String? {
    for ((prefix, root) in pathVariables) {
      if (fullPath.startsWith(root.path)) {
        if (fullPath == root.path) {
          return "\$${prefix.removeSuffix(CANONICALIZED)}"
        } else if (
          fullPath.length > root.path.length && fullPath[root.path.length] == File.separatorChar
        ) {
          val relative = fullPath.substring(root.path.length)
          return "\$${prefix.removeSuffix(CANONICALIZED)}$relative".let {
            if (unix) it.replace('\\', '/') else it
          }
        }
      }
    }

    if (
      relativeTo != null &&
        fullPath.startsWith(relativeTo) &&
        fullPath.length > relativeTo.length &&
        fullPath[relativeTo.length] == File.separatorChar
    ) {
      return fullPath.substring(relativeTo.length + 1).let {
        if (unix) it.replace('\\', '/') else it
      }
    }

    return null
  }

  /** Reverses the path string computed by [toPathString] */
  fun fromPathString(
    path: String,
    relativeTo: File? = null,
    allowMissingPathVariable: Boolean = false
  ): File {
    if (path.startsWith("$")) {
      val hasBraces = path.length > 1 && path[1] == '{'
      for (i in 1 until path.length) {
        val c = path[i]
        if ((hasBraces && path[i - 1] == '}') || (!hasBraces && !c.isJavaIdentifierPart())) {
          val varName = path.substring(1, i)
          val dir =
            pathVariables.firstOrNull { it.name == varName }?.dir
              ?: if (allowMissingPathVariable) {
                File("\$$varName")
              } else {
                error("Path variable \$$varName referenced in $path not provided to serialization")
              }
          val relativeStart = if (c == '/' || c == '\\') i + 1 else i
          return File(dir, path.substring(relativeStart))
        }
      }
      val name = path.substring(1)
      return pathVariables.firstOrNull { it.name == name }?.dir
        ?: if (allowMissingPathVariable) {
          File("\$$name")
        } else {
          error("Path variable \$$name referenced in $path not provided to serialization")
        }
    }

    val file = File(path)
    if (relativeTo != null && !file.isAbsolute) {
      // Don't create paths like foo/bar/../something -- instead create foo/something
      if (path.startsWith("../") || path.startsWith("..\\")) {
        val parentFile = relativeTo.parentFile
        if (parentFile != null) {
          return File(parentFile, path.substring(3))
        }
      }
      return File(relativeTo, path)
    } else {
      return file
    }
  }

  /** Adds all the [other] path variables into this list, replacing any duplicates. */
  fun add(other: PathVariables) {
    for (variable in other.pathVariables) {
      val name = variable.name
      checkPathVariableName(name)
      pathVariables.firstOrNull { it.name == name }?.let { pathVariables.remove(it) }
      pathVariables.add(variable)
    }
    sort()
  }

  /**
   * Computes the canonical path for all the path variables and (if different from their current
   * paths) adds these as an additional set of path variables. This isn't done implicitly when you
   * add each variable because we want all the canonical paths to have lower priority than other
   * variables.
   */
  fun normalize() {
    if (pathVariables.isEmpty()) {
      return
    }
    for (variable in pathVariables.toList()) {
      try {
        assert(!variable.name.endsWith(CANONICALIZED)) // unlikely -- reserved suffix
        val canonical = variable.dir.canonicalFile
        if (canonical.path != variable.dir.path) {
          add(variable.name + CANONICALIZED, canonical, false)
        }
      } catch (ignore: IOException) {}
    }
    sort()
  }

  /** Returns true if there is at least one path variable */
  fun any(): Boolean {
    return pathVariables.isNotEmpty()
  }

  /** Creates a new [PathVariables] list by filtering the current set. */
  fun filter(predicate: (String) -> Boolean): PathVariables {
    val filtered = PathVariables()
    for (variable in pathVariables) {
      if (predicate(variable.name)) {
        filtered.pathVariables.add(variable)
      }
    }
    return filtered
  }

  override fun toString(): String = pathVariables.joinToString(separator = "\n")

  companion object {
    /** Internal suffix for canonicalized versions of the paths */
    private const val CANONICALIZED = "_canonical"

    /**
     * Returns true if the given [path] is a *private* path variable (which should not show up in
     * user visible files, such as baselines or report files)
     */
    fun isPrivatePathVariable(path: String): Boolean = !path.startsWith("{")

    /**
     * Sort variables such that sub directories are listed before their parents, all canonicalized
     * variables are listed last, and other than that, alphabetical order.
     */
    private val PATH_COMPARATOR: Comparator<PathVariable> =
      compareBy(
        { it.name.endsWith(CANONICALIZED) },
        { -it.dir.path.length },
        { it.dir.path },
        { it.name }
      )

    /**
     * Parses a path variable descriptor and returns a corresponding [PathVariables] object. The
     * format of the string is a semi-colon separated list of name=path pairs, such as
     * HOME=/Users/demo;GRADLE_USER=/Users/demo/.gradle;
     *
     * If the string contains multiple pairs with the same name, the first pair takes precedence.
     */
    fun parse(s: String): PathVariables {
      val variables = PathVariables()
      val names = mutableSetOf<String>()
      for (pair in s.split(';')) {
        if (pair.isBlank()) {
          // trailing ;
          continue
        }
        val index = pair.indexOf('=')
        if (index <= 0 || index == pair.length - 1 || pair.startsWith("$$")) {
          error("Invalid path descriptor $pair, expected name=path-prefix")
        }
        val name = pair.substring(0, index).trim()
        if (names.add(name)) {
          val prefix = File(pair.substring(index + 1).trim())
          variables.add(name, prefix, sort = false)
        }
      }
      variables.sort()
      return variables
    }

    /** Check that the path variable name is allowed */
    private fun checkPathVariableName(name: String) {
      when {
        name.startsWith('{') ->
          if (!name.endsWith('}') && !name.endsWith("}$CANONICALIZED")) {
            error("Invalid path variable name $name, missing ending \"}\".")
          }
        name.asSequence().any { !it.isJavaIdentifierPart() } ->
          error(
            "Invalid path variable name $name. Contains illegal character \"" +
              "${name.asSequence().first { !it.isJavaIdentifierPart() }}\"."
          )
      }
    }

    /**
     * Returns true if the given [path] appears to use a path variable, e.g. `$VAR/foo/bar` does,
     * `${VAR}/foo/bar` does, and `/foo/bar` does not.
     */
    fun startsWithVariable(path: String, variable: String): Boolean {
      if (path.startsWith("$")) {
        val hasBraces = path.length > 1 && path[1] == '{'
        for (i in 1 until path.length) {
          val c = path[i]
          if ((hasBraces && path[i - 1] == '}') || (!hasBraces && !c.isJavaIdentifierPart())) {
            return i - 1 == variable.length && path.regionMatches(1, variable, 0, variable.length)
          }
        }
      }

      return false
    }
  }
}
