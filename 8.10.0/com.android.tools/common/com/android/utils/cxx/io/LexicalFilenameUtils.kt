/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.utils.cxx.io

/**
 * Methods for dealing with file names without first converting to [java.io.File].
 * The purpose is for high-volume cases where convenience is less important than throughput.
 * Generally, the File or Path equivalent of these functions should be used.
 */

typealias LexicalFilename = CharSequence

/**
 * Check whether the filename has the [extension]. Ignore case.
 */
fun LexicalFilename.hasExtensionIgnoreCase(expectedExtension : String) : Boolean {
    val lastIndexOfName = lastIndexOfName()
    val firstIndexOfName = firstIndexOfName(lastIndexOfName = lastIndexOfName)
    return areEqualIgnoreCase(
        extension(
            firstIndexOfName = firstIndexOfName,
            lastIndexOfName = lastIndexOfName
        ),
        expectedExtension
    )
}

/**
 * Check whether the file has any of [extensions]. Ignore case.
 */
fun LexicalFilename.hasExtensionIgnoreCase(vararg extensions : String) : Boolean {
    val lastIndexOfName = lastIndexOfName()
    val firstIndexOfName = firstIndexOfName(lastIndexOfName = lastIndexOfName)
    val actualExtension = extension(
        firstIndexOfName = firstIndexOfName,
        lastIndexOfName = lastIndexOfName
    )
    return extensions.any { candidate -> areEqualIgnoreCase(actualExtension, candidate) }
}

/**
 * Check whether the name of the file starts with any of [suffixes]. Ignore case.
 */
fun LexicalFilename.filenameEndsWithIgnoreCase(vararg suffixes : String) : Boolean {
    val lastIndexOfName = lastIndexOfName()
    val firstIndexOfName = firstIndexOfName(lastIndexOfName = lastIndexOfName)
    val indexOfExtensionDot = indexOfExtensionDot(
        firstIndexOfName = firstIndexOfName,
        lastIndexOfName = lastIndexOfName
    )
    val name = nameWithoutExtension(
        firstIndexOfName = firstIndexOfName,
        indexOfExtensionDot = indexOfExtensionDot
    )
    return suffixes.any { prefix -> name.endsWithIgnoreCase(prefix) }
}

/**
 * Check whether the name of the file starts with [prefix]. Ignore case.
 */
fun LexicalFilename.filenameStartsWithIgnoreCase(prefix : String) : Boolean {
    val lastIndexOfName = lastIndexOfName()
    val firstIndexOfName = firstIndexOfName(lastIndexOfName = lastIndexOfName)
    val name = name(
        firstIndexOfName = firstIndexOfName,
        lastIndexOfName = lastIndexOfName
    )
    return name.startsWithIgnoreCase(prefix)
}

/**
 * Remove the given extension [ext] from the file name if it is present. Otherwise, leave the
 * file name unchanged.
 */
fun LexicalFilename.removeExtensionIfPresent(ext : String) : String {
    val lastIndexOfName = lastIndexOfName()
    val firstIndexOfName = firstIndexOfName(lastIndexOfName = lastIndexOfName)
    if (!areEqualIgnoreCase(
            extension(
                firstIndexOfName = firstIndexOfName,
                lastIndexOfName = lastIndexOfName
            ),
            ext)) {
        return toString()
    }
    val indexOfExtensionDot = indexOfExtensionDot(
        firstIndexOfName = firstIndexOfName,
        lastIndexOfName = lastIndexOfName
    )
    return nameWithoutExtension(
        firstIndexOfName = firstIndexOfName,
        indexOfExtensionDot = indexOfExtensionDot
    ).toString()
}

/**
 * Return the first index of the file name in the path.
 */
private fun LexicalFilename.firstIndexOfName(
    lastIndexOfName : Int
) : Int  {
    var index = lastIndexOfName
    while(index in indices) {
        if (get(index).isSlash)
            return index + 1
        --index
    }
    return 0
}

/**
 * Return the last index of the file name in the path.
 */
private fun LexicalFilename.lastIndexOfName() : Int {
    var index = lastIndex
    while(index > 0) {
        if (!get(index).isSlash) return index
        --index
    }
    return 0
}

/**
 * Return the index of the extension dot ('.'). If there is no extension then -1.
 */
private fun LexicalFilename.indexOfExtensionDot(
    lastIndexOfName : Int,
    firstIndexOfName : Int
) : Int {
    var index = lastIndexOfName
    while(index in firstIndexOfName until length) {
        if (get(index) == '.') return index
        --index
    }
    return -1
}

/**
 * Check whether the given character is a forward slash or backward slash.
 */
inline val Char.isSlash : Boolean get() = this == '/' || this == '\\'

/**
 * Get the extension of the file name. Should behave the same as File::extension
 */
private fun LexicalFilename.extension(
    lastIndexOfName : Int,
    firstIndexOfName : Int
) : CharSequence  {
    val lastDot = indexOfExtensionDot(
        firstIndexOfName = firstIndexOfName,
        lastIndexOfName = lastIndexOfName
    )
    if (lastDot == -1) return ""
    return subSequence(
        startIndex = lastDot + 1,
        endIndex = lastIndexOfName + 1)
}

/**
 * Get the extension of the file name. Should behave the same as File::name.
 */
private fun LexicalFilename.name(
    firstIndexOfName : Int,
    lastIndexOfName : Int
) : CharSequence  {
    return subSequence(firstIndexOfName, lastIndexOfName + 1)
}

/**
 * Get the name of the file without extension. Should behave the same as File::nameWithoutExtension.
 */
private fun LexicalFilename.nameWithoutExtension(
    firstIndexOfName : Int,
    indexOfExtensionDot : Int
) : CharSequence  {
    return if (indexOfExtensionDot == -1) subSequence(firstIndexOfName, lastIndex + 1)
    else subSequence(firstIndexOfName, indexOfExtensionDot)
}

/**
 * Check whether two char sequences match ignoring case.
 */
private fun areEqualIgnoreCase(
    first : CharSequence,
    second : CharSequence
) : Boolean {
    if (first.length != second.length) return false
    for(i in first.indices) {
        if (!first[i].equals(second[i], ignoreCase = true)) return false
    }
    return true
}

/**
 * Check whether this sequences starts with [value]. Ignore case.
 */
private fun CharSequence.startsWithIgnoreCase(
    value : CharSequence
) : Boolean {
    if (length < value.length) return false
    for(i in value.indices) {
        if (!get(i).equals(value[i], ignoreCase = true)) return false
    }
    return true
}

/**
 * Check whether this sequences ends with [value]. Ignore case.
 */
private fun CharSequence.endsWithIgnoreCase(
    value : CharSequence
) : Boolean {
    val difference = length - value.length
    if (difference < 0) return false
    for(i in value.indices) {
        if (!get(i + difference).equals(value[i], ignoreCase = true)) return false
    }
    return true
}
