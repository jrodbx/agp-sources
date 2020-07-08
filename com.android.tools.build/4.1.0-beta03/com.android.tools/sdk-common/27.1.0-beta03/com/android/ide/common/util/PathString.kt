/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("PathStringUtil")
package com.android.ide.common.util

import com.google.common.base.Joiner
import java.io.File
import java.net.URI
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.ProviderNotFoundException
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * An implementation of a [Path]-like data structure that can represent unix or Windows-style path names.
 * Unlike [Path], this is a lightweight data structure with no dependencies so it can be instantiated and
 * manipulated even if there is no matching nio filesystem implementation installed. Unlike [File], this
 * can be used to represent paths on filesystems other than file:// - provided the filesystem uses compatible
 * path names.
 *
 * This class remembers its scheme. It also remembers the exact string used to construct it. For this reason,
 * conversions of [Path] -> [PathString] -> [Path] are lossless. So are conversions of [File] -> [PathString] ->
 * [File]. If the string held a valid pathname, conversions of [String] -> [PathString] -> [String] would also
 * be lossless, even if the string made inconsistent use of separators and case.
 */
class PathString private constructor(
        /**
         * Holds the URI for the filesystem root. For example, this would be `file:///` for local filesystem paths on
         * Unix and `file:///X:\` or `file:///X:/` on Windows.
         */
        val filesystemUri: URI,
        /**
         * String which contains the platform-independent path string for this [PathString]. The used portion of the
         * string is indicated by [startIndex] and [suffixEndIndex], below. Note that we store strings in this way to
         * avoid copying the string in the methods that return a sub-path.
         */
        private val path: String,
        /**
         * Inclusive start index into [path], where the path string starts.
         */
        private val startIndex: Int,
        /**
         * Exclusive index into [path], where the path string ends.
         */
        private val suffixEndIndex: Int,
        /**
         * Exclusive index into "path" containing the last character of the prefix identifier. This would be something like
         * "C:/" on Windows or "/" on Linux.
         */
        private val prefixEndIndex: Int = 0,
        /**
         * Separator to be used when interpreting the [path] string, or [NUL_CHAR] if the [path] string doesn't contain any
         * separators. This might be different from the system separator when manipulating paths intended for use on
         * other filesystems.
         */
        private val separator: Char
) : Comparable<PathString> {
    private var hash: Int = 0

    private constructor(filesystem: URI, path: String, rootLength: Int) :
            this(filesystem, path, rootLength, path.length, rootLength, detectSeparator(path, rootLength))

    /**
     * Creates a [PathString] given a filesystem [protocol] and [path]. This constructor is applicable
     * only to the local filesystem and the filesystems uniquely defined by their protocol, e.g. "jar"
     * and "apk" filesystems. For other filesystems use alternative constructors.
     *
     * @param protocol the filesystem protocol, e.g. "file", "jar", or "apk"
     * @param path the path within the filesystem
     */
    constructor(protocol: String, path: String) : this(getUri(protocol), path, prefixLength(path))

    /**
     * Creates a [PathString] given a filesystem [URI][filesystemUri] and [path].
     *
     * @param filesystemUri the URI used to identify the filesystem. It is intended to contain the shortest
     *     path such that [FileSystems.getFileSystem] will return the correct filesystem. For example, this
     *     would be "file:///" for local filesystem paths on Unix.
     * @param path the path within the filesystem
     */
    constructor(filesystemUri: URI, path: String) : this(filesystemUri, path, prefixLength(path))

    constructor(path: String) : this(defaultFilesystemUri, path)
    constructor(path: File) : this(defaultFilesystemUri, path.path)
    constructor(path: Path) : this(path.fileSystem.getPath(path.fileSystem.separator).toUri(), path.toString())

    /**
     * The original, unmodified, path string.
     */
    val rawPath: String
        get() {
            return if (startIndex == prefixEndIndex) {
                path.substring(0, suffixEndIndex)
            } else {
                path.substring(0, prefixEndIndex) + path.substring(startIndex, suffixEndIndex)
            }
        }

    /**
     * Returns the path string in a platform-independent format (using '/' separators).
     */
    val portablePath: String
        get() = rawPath.replace('\\', '/')

    /**
     * Returns the path string in a platform-dependent format (using platform-specific separators appropriate for the local OS).
     */
    val nativePath: String
        get() = rawPath.withSeparator(File.separatorChar)

    /**
     * Returns a string describing this path. The string describes both the filesystem and the path in use.
     */
    override fun toString(): String {
        var schemeString = filesystemUri.toString()
        if (schemeString.startsWith("file:///") &&
                schemeString.endsWith(":/") &&
                schemeString.length == "file:///".length + ":/".length + 1) {
            // File system URI contains a drive letter ("file:///X:/") - remove it to get just the scheme.
            schemeString = "file:///"
        }
        val path = rawPath
        val buf = StringBuilder(schemeString.length + 1 + path.length)
        if (schemeString.endsWith("///")) {
            buf.append(schemeString, 0, schemeString.length - 1)
        } else {
            buf.append(schemeString)
            if (!schemeString.endsWith('/')) {
                buf.append("!/")
            }
        }
        buf.append(path)
        return buf.toString()
    }

    fun toDebugString(): String {
        return "PathString filesystemUri=\"$filesystemUri\" path=\"$path\" startIndex=$startIndex " +
               "suffixEndIndex=$suffixEndIndex prefixEndIndex=$prefixEndIndex separator='$separator'"
    }

    /**
     * If this path uses the file:// scheme, this method returns the associated [File] object. Returns null if it uses any other
     * scheme.
     */
    fun toFile(): File? {
        if (filesystemUri.scheme == "file") {
            return File(rawPath)
        }
        return null
    }

    /**
     * If this [PathString] can be converted to a [Path], returns the [Path]. Returns null if the
     * [filesystemUri] does not point to a known nio filesystem.
     *
     * Throws [SecurityException] if the filesystem exists but permission is denied to it. Throws
     * [java.nio.file.InvalidPathException] if the filesystem exists but the path string cannot be
     * converted.
     */
    fun toPath(): Path? {
        return try {
            Paths.get(filesystemUri).fileSystem.getPath(rawPath)
        } catch (e: FileSystemNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: ProviderNotFoundException) {
            null
        }
    }

    /**
     * Returns true if and only if this is an absolute path.
     */
    @get:JvmName("isAbsolute")
    val isAbsolute: Boolean
        get() = prefixEndIndex != 0 && isSeparator(path[prefixEndIndex - 1])

    /**
     * Returns the file name that is farthest from the root component. Returns an empty string if
     * this path is empty besides the root component.
     */
    val fileName: String
        get() {
            val end = endIndex
            return path.substring(computeNameStart(end), end)
        }

    /**
     * Returns the name of the given segment of the [PathString]. [index] must be between 0 and [nameCount]-1
     */
    fun segment(index: Int): String {
        if (index < 0) {
            throw IllegalArgumentException("Negative index: $index")
        }

        var separatorCount = 0
        var subRangeStart = startIndex + startIndex.until(endIndex).countUntil {
            if (isSeparator(path[it])) {
                separatorCount++
            }
            separatorCount >= index
        }

        if (subRangeStart >= endIndex - 1) {
            throw IllegalArgumentException("Invalid index $index for path ${toString()}")
        }

        if (isSeparator(path[subRangeStart])) {
            subRangeStart++
        }

        var lengthCount = 0

        val subRangeEnd = subRangeStart + subRangeStart.until(endIndex).countUntil {
            if (isSeparator(path[it])) {
                lengthCount++
            }
            lengthCount >= 1
        }

        if (lengthCount < 1 - 1) {
            throw IllegalArgumentException()
        }

        return path.substring(subRangeStart, subRangeEnd)
    }

    /**
     * Returns the name of the given segment of the [PathString] as a [PathString]. [index] must be
     * between 0 and [nameCount]-1
     */
    operator fun get(index: Int): PathString = subRange(index)

    /**
     * Returns a sub-sequence of name elements from this path as a relative path.
     */
    operator fun get(range: IntRange): PathString {
        if (range.step != 1) {
            throw IllegalArgumentException("Step must be 1")
        }
        return subRange(range.start, range.endInclusive - range.start + 1)
    }

    /**
     * Returns a sub-sequence of name elements from this path between, as a relative path.
     * [beginIndex] is inclusive, [endIndex] is exclusive. Requires that
     * 0 <= [beginIndex] <= [endIndex] <= [nameCount].
     */
    fun subpath(beginIndex: Int, endIndex: Int): PathString = subRange(beginIndex,
            endIndex - beginIndex)

    /**
     * Returns true iff this [PathString] starts with the given prefix. That is, it returns true
     * if this path has the same filesystem and root, and begins with all of the segments
     * in the possible prefix. This is different from checking for a string prefix. For example,
     * foo/bar/baz starts with foo/bar but not foo/b.
     */
    fun startsWith(possiblePrefix: PathString): Boolean {
        val toTest = possiblePrefix.withoutTrailingSeparator()

        if (!sameRootAs(toTest)) {
            return false
        }

        val ourEnd = endIndex
        val thisLength = ourEnd - startIndex
        val prefixLength = toTest.endIndex - toTest.startIndex
        if (prefixLength > thisLength) {
            return false
        }

        if (prefixLength == 0) {
            return true
        }

        val requiredSeparatorPosition = startIndex + prefixLength

        if (requiredSeparatorPosition > ourEnd || (requiredSeparatorPosition < ourEnd && !isSeparator(path[requiredSeparatorPosition]))) {
            return false
        }

        for (idx in 0 until prefixLength) {
            val thisChar = path[startIndex + idx]
            val otherChar = toTest.path[toTest.startIndex + idx]

            if (thisChar == otherChar) {
                continue
            }

            if (!(isSeparator(thisChar) && isSeparator(otherChar))) {
                return false
            }
        }

        return true
    }

    /**
     * Returns the parent of this path, or this path if it is a root path.
     */
    val parentOrRoot: PathString
        get() = parent ?: this

    /**
     * Returns the parent of this path, or null if this is a root path.
     */
    val parent: PathString?
        get() {
            if (endIndex <= startIndex) {
                return null
            }
            val newEnd = computeNameStart(endIndex) - 1
            if (newEnd <= startIndex) {
                return root
            }
            val result = PathString(filesystemUri,
                    path,
                    startIndex,
                    newEnd,
                    prefixEndIndex,
                    separator)

            if (hash != 0) {
                // Compute the hashcode for the parent by subtracting the hashcode for the removed
                // segments.
                var parentHash = hash
                var hashCodeForSegment = 0
                for (i in newEnd until suffixEndIndex) {
                    val nextChar = path[i]
                    if (isSeparator(nextChar)) {
                        parentHash -= hashCodeForSegment
                        hashCodeForSegment = 0
                    }
                    hashCodeForSegment = 31 * hashCodeForSegment + nextChar.toInt()
                }
                parentHash -= hashCodeForSegment
                result.hash = parentHash
            }
            return result
        }

    /**
     * Returns the file name of the parent path, or null if this is a root path.
     * This is a convenience method similar to [File.getParent].
     */
    val parentFileName: String?
        get() {
            return parent?.fileName
        }

    /**
     * Returns the root of this path or null if none.
     */
    val root: PathString?
        get() {
            if (prefixEndIndex == 0) {
                return null
            }
            return PathString(filesystemUri, path, prefixEndIndex, prefixEndIndex, prefixEndIndex, separator)
        }

    /**
     * Returns the number of names in this path, not counting the root element.
     */
    val nameCount: Int
        get() {
            if (endIndex == prefixEndIndex)
                return 0
            return startIndex.until(endIndex).count {
                isSeparator(path[it])
            } + 1
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathString

        if (filesystemUri != other.filesystemUri) return false

        val length = suffixEndIndex - startIndex
        if (length != other.suffixEndIndex - other.startIndex) return false
        if (prefixEndIndex != other.prefixEndIndex) return false

        for (i in 0.until(prefixEndIndex)) {
            if (path[i] != other.path[i]) {
                return false
            }
        }

        for (i in 0.until(length)) {
            if (path[startIndex + i] != other.path[other.startIndex + i]) {
                return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        if (hash == 0) {
            hash = computeHash()
        }
        return hash
    }

    private fun computeHash(): Int {
        var result = filesystemUri.hashCode()

        for (i in 0.until(prefixEndIndex)) {
            result = 31 * result + path[i].toInt()
        }

        // Compute the hashcode as the simple sum of the hashcodes of the individual segments.
        // That way we can compute the hashcode for the parent folder efficiently from the
        // hashcode of one of the child folders by subtracting the hashcode of the last segment.
        var hashCodeForSegment = 0
        for (i in startIndex until suffixEndIndex) {
            val nextChar = path[i]
            if (isSeparator(nextChar)) {
                result += hashCodeForSegment
                hashCodeForSegment = 0
            }
            hashCodeForSegment = 31 * hashCodeForSegment + nextChar.toInt()
        }
        result += hashCodeForSegment

        return result
    }

    /**
     * Returns a path equivalent to this one, but without redundant segments.
     */
    fun normalize(): PathString {
        val absolute = isAbsolute
        val newNames = ArrayDeque<String>()
        segments.forEach {
            if (it == PARENT) {
                val lastName = newNames.peekLast()
                if (lastName != null && lastName != PARENT) {
                    newNames.removeLast()
                } else if (!absolute) {
                    // If there's a root and we have an extra ".." that would go up above the root, ignore it.
                    newNames.add(it)
                }
            } else if (it != SELF) {
                newNames.add(it)
            }
        }

        if (hasTrailingSeparator) {
            newNames.addLast("")
        }

        val rootString = root?.rawPath?.withSeparator(separator) ?: ""

        return PathString(filesystemUri, rootString + Joiner.on(separator).join(newNames))
    }

    /**
     * Returns a [PathString] that is the same as this one, but without a trailing separator.
     * Returns this if it doesn't have a trailing separator.
     */
    fun withoutTrailingSeparator(): PathString {
        return if (hasTrailingSeparator) {
            PathString(filesystemUri, path, startIndex, endIndex, prefixEndIndex, separator)
        } else this
    }

    /**
     * True iff the path contains a trailing separator.
     */
    @get:JvmName("hasTrailingSeparator")
    val hasTrailingSeparator: Boolean
        get() = suffixEndIndex > startIndex && isSeparator(
                path[suffixEndIndex - 1])

    /**
     * Constructs a path which, when resolved against this path, will point to the same location as the original. Returns the original path
     * if there is no simpler way to express a relative path between this path and the other.
     */
    fun relativize(other: PathString): PathString {
        if (isEmptyPath) {
            return other
        }

        if (isAbsolute && other.isEmptyPath) {
            return other
        }

        val otherRoot = other.root
        if (root != otherRoot) {
            val otherRootString = otherRoot?.rawPath ?: ""
            val convertedRootString = root?.rawPath?.withSeparator(other.separator) ?: ""
            if (otherRootString != convertedRootString) {
                var rootString = otherRootString
                val afterDriveSeparator = prefixEndIndex - 0.until(prefixEndIndex).reversed().countUntil { path[it] == ':' }
                if (afterDriveSeparator > 0) {
                    val firstDifference = 0.until(Math.min(afterDriveSeparator,
                            other.prefixEndIndex)).countUntil { path[it] != other.path[it] }
                    if (firstDifference >= afterDriveSeparator) {
                        rootString = other.path.substring(afterDriveSeparator, other.prefixEndIndex)
                    }
                }
                return PathString(filesystemUri,
                        rootString + other.path.substring(other.startIndex, other.suffixEndIndex),
                        rootString.length)
            }
        }

        val segments = this.segments
        val otherSegments = other.segments

        val commonPrefixLength = 0.until(Math.min(segments.count(), otherSegments.count()))
                .countUntil { segments[it] != otherSegments[it] }

        val newSegments = (commonPrefixLength.until(segments.size)).map { PARENT } +
                (commonPrefixLength.until(otherSegments.size)).map { otherSegments[it] }
        val newSeparator = chooseSeparator(other.separator, separator)
        val finalString =
                Joiner.on(newSeparator).join(newSegments) + if (other.hasTrailingSeparator && !newSegments.isEmpty()) newSeparator else ""

        return PathString(filesystemUri,
                finalString,
                0,
                finalString.length,
                0,
                separator)
    }

    /**
     * Returns true iff this is the empty path (an empty path is a path with no names that always returns the other path when resolved
     * against any other path).
     */
    @get:JvmName("isEmptyPath")
    val isEmptyPath: Boolean
        get() = prefixEndIndex == 0 && startIndex == suffixEndIndex

    /**
     * Resolves the given path against this one, given a path on the same filesystem. Resolving a
     * path is a lot like concatenation for relative paths. Absolute paths point to the same place
     * after being resolved. This is best explained by analogy. Imagine you're in a command prompt
     * and this path is your current directory. You type "cd [other]". The resolved path is the
     * directory you'd end up in.
     *
     * @param other a path string on the same filesystem as this. Both relative and absolute paths are
     * supported.
     */
    fun resolve(other: String): PathString
        = resolve(PathString(filesystemUri, other))

    /**
     * Resolves the given path against this one. Resolving a path is a lot like concatenation for
     * relative paths. Absolute paths point to the same place after being resolved.
     * This is best explained by analogy. Imagine you're in a command prompt and this path is your
     * current directory. You type "cd [other]". The resolved path is the directory you'd end up in.
     */
    fun resolve(other: PathString): PathString {
        val otherRoot = other.root

        if (isEmptyPath) {
            return other
        }

        if (other.isAbsolute) {
            // Windows-specific paths: If the other root is just a slash and this root contains a drive specifier,
            // use this drive specifier combined with the slash.
            if (otherRoot != null && otherRoot.prefixEndIndex == 1) {
                val indexOfDriveSeparator = path.lastIndexOf(':', prefixEndIndex)
                val newPath = if (indexOfDriveSeparator > 0) {
                    path.substring(0, indexOfDriveSeparator + 1) + other.rawPath
                } else {
                    other.rawPath
                }
                return PathString(filesystemUri, newPath)
            }
            return other
        }

        if (otherRoot != null && !compatibleRoots(root, otherRoot)) {
            return other
        }

        if (other.isEmptyPath || other.startIndex == other.suffixEndIndex) {
            return this
        }

        val otherRelPath = other.path.substring(other.startIndex, other.suffixEndIndex)
        val newPath = if (separator == NUL_CHAR) {
            // This path only does not contain a separator.
            if (startIndex == path.length) {
                // This path is either empty or consists of a drive letter with a colon.
                path + otherRelPath
            } else {
                val newSeparator = chooseSeparator(other.separator, NUL_CHAR)
                path + newSeparator + otherRelPath.withSeparator((newSeparator))
            }
        } else {
            // This path contains a separator.
            if (startIndex < suffixEndIndex && !isSeparator(path[suffixEndIndex - 1])) {
                rawPath + separator + otherRelPath.withSeparator(separator)
            } else {
                rawPath + otherRelPath.withSeparator(separator)
            }
        }

        return PathString(filesystemUri, newPath)
    }

    /**
     * The segments of this path, as a list of strings.
     */
    val segments: List<String>
        get() {
            val result = ArrayList<String>()

            var lastSegment = startIndex

            startIndex.until(endIndex).forEach {
                if (isSeparator(path[it])) {
                    result.add(path.substring(lastSegment, it))
                    lastSegment = it + 1
                }
            }

            if (lastSegment < endIndex) {
                result.add(path.substring(lastSegment, endIndex))
            }
            return result
        }

    override fun compareTo(other: PathString): Int {
        val schemeResult = filesystemUri.compareTo(other.filesystemUri)

        if (schemeResult != 0) {
            return schemeResult
        }

        val otherPrefixEndIndex = other.prefixEndIndex

        for (i in 0.until(Math.min(otherPrefixEndIndex, prefixEndIndex))) {
            val cmpResult = path[i].compareTo(other.path[i])

            if (cmpResult != 0) {
                return cmpResult
            }
        }

        if (otherPrefixEndIndex != prefixEndIndex) {
            return prefixEndIndex.compareTo(otherPrefixEndIndex)
        }

        val length = suffixEndIndex - startIndex
        val otherLength = other.suffixEndIndex - other.startIndex
        val min = Math.min(length, otherLength)

        for (i in 0.until(min)) {
            val cmpResult = path[startIndex + i].compareTo(other.path[other.startIndex + i])

            if (cmpResult != 0) {
                return cmpResult
            }
        }

        return length.compareTo(otherLength)
    }

    private fun sameRootAs(other: PathString): Boolean {
        if (filesystemUri != other.filesystemUri) {
            return false
        }

        if (prefixEndIndex != other.prefixEndIndex) {
            return false
        }

        for (idx in 0 until prefixEndIndex) {
            if (path[idx] != other.path[idx]
                    && !(isSeparator(path[idx]) && isSeparator(other.path[idx]))) {
                return false
            }
        }
        return true
    }

    private fun compatibleRoots(root1: PathString?, root2: PathString?): Boolean {
        if (root1 == root2) {
            return true
        }
        if (root1 == null || root2 == null) {
            return false
        }
        return driveName(root1.rawPath) == driveName(root2.rawPath)
    }

    private fun driveName(rawPath: String): String =
            rawPath.substring(0, rawPath.countUntil(':')).toUpperCase()

    private fun subRangeOrNull(index: Int,
            length: Int = 1): PathString? {
        if (index < 0 || length < 0) {
            return null
        }

        if (length == 0) {
            return PathString(filesystemUri, "")
        }

        var separatorCount = 0
        var subRangeStart = startIndex + startIndex.until(endIndex).countUntil {
            if (isSeparator(path[it])) {
                separatorCount++
            }
            separatorCount >= index
        }

        if (subRangeStart >= endIndex - 1) {
            return null
        }

        if (isSeparator(path[subRangeStart])) {
            subRangeStart++
        }

        var lengthCount = 0

        val subRangeEnd = subRangeStart + subRangeStart.until(endIndex).countUntil {
            if (isSeparator(path[it])) {
                lengthCount++
            }
            lengthCount >= length
        }

        if (lengthCount < length - 1) {
            return null
        }

        return PathString(filesystemUri,
                path,
                subRangeStart,
                subRangeEnd,
                0,
                separator)
    }

    private fun subRange(index: Int, length: Int = 1): PathString {
        return subRangeOrNull(index, length) ?: throw IllegalArgumentException(
                "beginIndex $index and suffixEndIndex ${index + length} are out of range for path ${toString()}")
    }

    /**
     * The end of the path string which does not include a trailing separator.
     */
    private val endIndex: Int get() = if (hasTrailingSeparator) suffixEndIndex - 1 else suffixEndIndex

    private fun computeNameStart(end: Int): Int =
            end - startIndex.until(end).reversed().countUntil {
                isSeparator(path[it])
            }
}

fun Path.toPathString() : PathString = PathString(this)
fun File.toPathString() : PathString = PathString(this)
fun Collection<File>.toPathStrings() = map { PathString(it) }
fun File?.toPathStringOrNull() : PathString? = this?.toPathString()

const val PARENT = ".."
const val SELF = "."
const val NUL_CHAR = 0.toChar()
val defaultFilesystemUri: URI = FileSystems.getDefault().let { it.getPath(it.separator) }.toUri()

private fun isSeparator(c: Char) = c == '/' || c == '\\'

private fun prefixLength(path: String): Int {
    var firstSlash = path.length
    for (i in 0.until(path.length)) {
        if (isSeparator(path[i])) {
            firstSlash = i
            break
        }
    }
    val firstColon = path.countUntil(':')

    var prefixEnd = firstColon + 1
    if (firstColon >= firstSlash) {
        // This path does not contain a Windows-style drive specifier.
        if (firstSlash > 0) {
            return 0
        }
        prefixEnd = 0
    }

    while (prefixEnd < path.length && isSeparator(path[prefixEnd])) prefixEnd++

    return prefixEnd
}

/**
 * Returns the number of leading elements for which the predicate is true.
 */
private inline fun IntProgression.countUntil(predicate: (Int) -> Boolean): Int {
    var result = 0

    for (item in this) {
        if (predicate(item)) {
            return result
        }
        result++
    }
    return result
}

private fun String.countUntil(char: Char, startIndex: Int = 0, ignoreCase: Boolean = false): Int {
    val result = indexOf(char, startIndex, ignoreCase)
    return if (result == -1) {
        length
    } else result
}

private fun detectSeparator(path: String, rootLength: Int): Char {
    val startIndex = (rootLength - 1).coerceAtLeast(0)
    for (i in startIndex.until(path.length)) {
        val c = path[i]
        if (isSeparator(c)) {
            return c;
        }
    }
    return NUL_CHAR
}

private fun String.withSeparator(sep: Char): String =
        if (sep == NUL_CHAR) this else replace('/', sep).replace('\\', sep)

private fun chooseSeparator(c1: Char, c2: Char) =
    if (c1 == NUL_CHAR) { if (c2 == NUL_CHAR) File.separatorChar else c2 } else c1

/** Keyed by URI scheme. */
private val uriCache = ConcurrentHashMap<String, URI>()

private val APK_URI = createRootUri("apk")
private val JAR_URI = createRootUri("jar")

private fun getUri(scheme: String): URI {
    return when (scheme) {
        defaultFilesystemUri.scheme -> defaultFilesystemUri
        APK_URI.scheme -> APK_URI
        JAR_URI.scheme -> JAR_URI
        else -> uriCache.getOrPut(scheme) { createRootUri(scheme) }
    }
}

private fun createRootUri(scheme: String) = URI(scheme, "", "/", null)
