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

package com.android.utils.cxx.os

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_LINUX
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.utils.StringHelperPOSIX
import com.android.utils.StringHelperWindows
import java.io.File

/**
 * Convenience methods like [exe], [bat], and [which] should be preferred over
 * [os.exe] etc except in cases where the behavior should be overridden/mocked
 * for testing.
 */

/**
 * File extension to use for executable files for the current host OS.
 * Either ".exe" or "".
 */
val exe get() = os.exe

/**
 * A shell script file extension.
 * Either ".bat" or "".
 */
val bat get() = os.bat

/**
 * Given a literal string [argument] quote it such that if [argument] is passed to a program
 * it is treated as a single argument with all text, including special characters, kept unchanged.
 */
fun quoteCommandLineArgument(argument: String) = os.quoteCommandLineArgument(argument)

/**
 * Given a name like 'executable' find the full path by searching on PATH. On Windows, look
 * for .exe, .bat, and .cmd. Return null if not found.
 */
fun which(executable : File) : File? = os.which(executable)

/**
 * Behavior of the current host platform.
 */
private val os : OsBehavior = createOsBehavior()

/**
 * Encapsulates file systen and shell differences between Windows, MacOS, and Posix.
 */
interface OsBehavior {
    val platform : Int

    /**
     * File extension to use for executable files.
     */
    val exe : String

    /**
     * A shell script file extension.
     * Either ".bat" or "".
     */
    val bat : String

    /**
     * Split multiple commands separated by && (or other host-specific separator).
     */
    fun splitCommandLine(commandLine: String) : List<String>

    /**
     * Convert a single command into a List<String> following host-specific rules. Does not
     * convert any escaped characters to their evaluated equivalent.
     */
    fun tokenizeCommandLineToRaw(command: String) : List<String>

    /**
     * Convert a single command into a List<String> following host-specific rules. Convert any
     * escaped characters to their evaluated equivalent.
     */
    fun tokenizeCommandLineToEscaped(command: String) : List<String>

    /**
     * Given a literal string [argument] quote it such that if [argument] is passed to a program
     * it is treated as a single argument all text, including special characters, kept intact.
     */
    fun quoteCommandLineArgument(argument: String) : String

    /**
     * Given a name like 'executable' find the full path by searching on PATH. On Windows, look
     * for .exe, .bat, and .cmd. Return null if not found.
     */
    fun which(executable : File) : File?
}

/**
 * Create a host behavior object that encapsulates differences between Windows, MacOs, and Posix.
 */
fun createOsBehavior(
    platform : Int,
    environmentPaths : List<File> = getEnvironmentPaths(),
    executableExtensions : List<String> = getExecutableExtensions(platform)
) : OsBehavior {
    return if (platform == PLATFORM_WINDOWS) object : OsBehavior {
        override val platform: Int get() = platform
        override val exe = ".exe"
        override val bat = ".bat"
        override fun splitCommandLine(commandLine: String) = StringHelperWindows.splitCommandLine(commandLine)
        override fun tokenizeCommandLineToRaw(command: String) = StringHelperWindows.tokenizeCommandLineToRaw(command)
        override fun tokenizeCommandLineToEscaped(command: String) = StringHelperWindows.tokenizeCommandLineToEscaped(command)
        override fun quoteCommandLineArgument(argument: String) : String {
            // These exclusions were found by experiment. I didn't find a source that defined them.
            if (argument.contains("\u0000")) error("argument had embedded 0x0000")
            if (argument.contains("\u001a")) error("argument had embedded 0x001a") // EOF marker
            if (argument.contains("`")) error("argument had embedded tick-mark (`)")
            if (argument.contains("\r")) error("argument had embedded line-feed (\\r)")
            if (argument.contains("\n")) error("argument had embedded carriage-return (\\n)")
            val escaped = argument.replace("%", "%%")
            return if (listOf(
                    ",", ";", "=", " ", "\u0008", "|", "&",
                    "*", "\"", "<", ">", "^",
                    // Colon (:) is a special case. A normal windows.bat script will accept this as
                    // a single parameter. However, if that script then invokes PowerShell.exe with
                    // %* args passed at the command-line then the argument will be split on the
                    // colon.
                    ":").any { argument.contains(it) }) {
                "\"${escaped
                    .replace("\\", "\\\\")
                    .replace("\"", "\"\"")}\""
            } else escaped
        }

        override fun which(executable: File): File? {

            fun find(file : File) : File? {
                if (file.extension != "" && file.isFile) return file
                for(ext in executableExtensions) {
                    val candidate = File("$file$ext")
                    if (candidate.isFile) return candidate
                }
                return null
            }

            find(executable)?.let { return@which it }

            if (!executable.isRooted) {
                for (path in environmentPaths) {
                    find(path.resolve(executable))?.let { return@which it }
                }
            }
            return null
        }
    }
    else object : OsBehavior {
        override val platform: Int get() = platform
        override val exe = ""
        override val bat = ""
        override fun splitCommandLine(commandLine: String) = StringHelperPOSIX.splitCommandLine(commandLine)
        override fun tokenizeCommandLineToRaw(command: String) = StringHelperPOSIX.tokenizeCommandLineToRaw(command)
        override fun tokenizeCommandLineToEscaped(command: String) = StringHelperPOSIX.tokenizeCommandLineToEscaped(command)
        override fun quoteCommandLineArgument(argument: String) : String {
            // The only character that seems not to work on *nix even with quoting or escaping is 0x00
            if (argument.contains("\u0000")) error("argument had embedded 0x0000")
            return if (listOf(" ", "*", ";", "<", ">", "~", "|", "&", "'", "\n").any { argument.contains(it) }) {
                "\"${argument
                    .replace("\"", "\\\"")}\""
            }
            else {
                argument
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("$", "\\$")
                    .replace("`", "\\`")
            }
        }

        override fun which(executable: File): File? {
            if (executable.isFile) return executable
            if (!executable.isRooted) {
                for (path in environmentPaths) {
                    val candidate = path.resolve(executable)
                    if (candidate.isFile) return candidate
                }
            }
            return null
        }
    }
}

/**
 * Retrieve the file extensions that this [platform] uses for executables.
 */
private fun getExecutableExtensions(platform : Int) = when(platform) {
    PLATFORM_WINDOWS ->
        // See https://ss64.com/nt/path.html
        (System.getenv("PATHEXT") ?: ".COM;.EXE;.BAT;.CMD").split(';')
    else -> listOf()
}

/**
 * @return list of folders (as Files) retrieved from PATH environment variable and from Sdk
 * cmake folder.
 */
fun getEnvironmentPaths(): List<File> {
    val envPath = System.getenv("PATH") ?: ""
    val pathSeparator = System.getProperty("path.separator").toRegex()
    return envPath
        .split(pathSeparator)
        .asSequence()
        .filter { it.isNotEmpty() }
        .map { File(it) }
        .toList()
}

fun createOsBehavior() = createOsBehavior(CURRENT_PLATFORM)
fun createWindowsBehavior() = createOsBehavior(PLATFORM_WINDOWS)
fun createLinuxBehavior() = createOsBehavior(PLATFORM_LINUX)

