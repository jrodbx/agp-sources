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

package com.android.utils.cxx.process

import com.android.utils.cxx.process.NativeBuildOutputClassifier.Message.Diagnostic
import com.android.utils.cxx.process.NativeBuildOutputClassifier.State.IN_ERROR_OR_WARNING
import com.android.utils.cxx.process.NativeBuildOutputClassifier.State.NOT_IN_ERROR_OR_WARNING
import com.android.utils.cxx.process.NativeBuildOutputClassifier.Message.Generic
import com.android.utils.cxx.process.NativeToolLineClassification.CLANG_COMMAND_LINE
import com.android.utils.cxx.process.NativeToolLineClassification.CLANG_FATAL_LINKER_ERROR_BUG_124104842
import com.android.utils.cxx.process.NativeToolLineClassification.Kind.ERROR
import com.android.utils.cxx.process.NativeToolLineClassification.Kind.INFO
import com.android.utils.cxx.process.NativeToolLineClassification.Kind.WARNING
import com.android.utils.cxx.process.NativeToolLineClassification.NDK_BUILD_BEGIN_ABI
import com.android.utils.cxx.process.NativeToolLineClassification.NINJA_ENTERING_DIRECTORY
import com.android.utils.cxx.process.NativeToolLineClassification.NONE
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/**
 * Groups stdout from native tools (mainly clang) in to separate logical chunks so that they can
 * eventually be presented to the user in Android Studio. Android Studio presents a tree of errors
 * and warning and each node must know which lines from STDOUT should be attributed to each.
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~[AGP Process ↓]
 *   +--------------------------+              +------------------------+
 *   | AGP Executes Native Tool | ---stdout--->| NativeOutputClassifier | -----------+
 *   +--------------------------+              |     running in AGP     |            |
 *                                             +------------------------+  NativeOutputClassifier
 *                                                                               Message(s)
 *                           +-----------------------------+                  subset of stdout
 *                           |   AGP elevates lines to     |                         |
 *           +---------------|    'lifecycle' level in     | <-----------------------+
 *           |               | DefaultProcessOutputHandler |
 *           |               +-----------------------------+
 *         Gradle
 * ~~~~~ lifecycle ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~[AGP/AS Process Boundary ↕]
 *         lines
 *           |                +---------------------------+
 *           +--------------->|  NativeOutputClassifier   |--------------------------+
 *                            | running in Android Studio |                          |
 *                            +---------------------------+                NativeOutputClassifier
 *                                                                             Diagnostic and
 *                       +-------------------------------------+            Generic Messages(s)
 *                       |       ClangOutputParser uses        |                     |
 *                       |   NativeOutputClassifier messages   |<--------------------+
 *                       | to generate build output tree nodes |
 *                       +-------------------------------------+
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~[AS Process ↑]
 *
 *  NativeOutputClassifier runs twice for each native process executed. The first time  is in
 *  Android Gradle Plugin (AGP) to elevate the relevant lines to 'lifecycle' so that they are
 *  visible by default when the user executes 'gradle assemble' without the --info flag. The
 *  second time in Android Studio produces Build Output tree nodes by reading AGP lifecycle lines.
 */
class NativeBuildOutputClassifier(val send: (Message) -> Unit) : LineOutputStream {

    /**
     * Define message to be received by caller. When the caller is AGP, just 'lines' are used.
     * These are elevated ti 'lifecycle'.
     *
     * When the caller is Android Studio, the 'Diagnostic' Messages are turned in to build tree
     * nodes.
     */
    sealed interface Message {
        val lines : List<String>
        val classification : NativeToolLineClassification

        /**
         * Error, warning, or information that should be a node in the build output tree.
         *
         * Example,
         *
         *        +-------------------------------- lines
         *        | +------------------------------ file
         *        | |                +------------- line
         *        | |                | +----------- column
         *        | |                | |         +- body
         *        | v                v v         v
         *  main -> /path/to/app.cpp:5:3: error: no matching function for call to 'bar'
         *        >   bar(b);
         *        >     ^~~
         *  info -> /path/to/app.cpp:1:13: note: candidate not viable: no known conversion from...
         *        > extern void bar(const int*);
         *
         */
        class Diagnostic(
            override val lines : List<String>,
            override val classification : NativeToolLineClassification,
            /**
             * (optional) is the ABI for this diagnostic.
             */
            val abi : String?,
            /**
             * file (optional) is the file associated with this diagnostic.
             */
            val file : String?,
            /**
             * body the body line of the message.
             */
            val body : String,
            /**
             * (optional) source line number.
             */
            val line : Int?,
            /**
             * column (optional) source column number.
             */
            val column : Int?,
            /**
             * command (optional) the clang command-line that generated this diagnostic.
             */
            val command : String?
        ) : Message

        /**
         * Non-diagnostic lines that should still be sent to lifecycle in AGP so that Android
         * Studio has ir available for parsing purposes.
         *
         * Examples,
         *
         *   ninja: Entering directory `/path/to/app/.cxx/Debug/4c2h4t3a/arm64-v8a'
         *                                                               ^
         *                                 Used for ABI in Diagnostic ---+
         *
         *   1 warnings and 2 errors.
         *   ^
         *   +--- Used to terminate the final error or warning.
         */
        class Generic(
            line : String,
            override val classification : NativeToolLineClassification
        ) : Message {
            override val lines = listOf(line)
        }
    }

    /**
     * State of the parser. Whether inside an error or warning or not.
     */
    private enum class State {
        IN_ERROR_OR_WARNING,
        NOT_IN_ERROR_OR_WARNING
    }

    /**
     * Details recorded for the next diagnostic.
     */
    private data class DiagnosticDetails(
        val classification: NativeToolLineClassification,
        val file: String?,
        val line: Int?,
        val column: Int?,
        val body: String
    )

    /**
     * Record of messages to send if a diagnostic appears later. If no diagnostic appears then
     * these messages are not sent.
     */
    private var messagesBeforeDiagnostic = mutableListOf<Message>()

    /**
     * Raw diagnostic lines to be sent in the next Diagnostic message.
     */
    private var diagnosticLines = mutableListOf<String>()

    /**
     * The current working directory. Used to make full paths from relative paths in diagnostics.
     */
    private var workingDirectory : String? = null

    /**
     * Details for the next diagnostic. File, Lines, column, etc.
     */
    private var details : DiagnosticDetails? = null

    /**
     * The ABI for the next diagnostic.
     */
    private var abi : String? = null

    /**
     * The clang command line for the next diagnostic.
     */
    private var commandLine : String? = null

    /**
     * The count of diagnostics sent so far.
     */
    private var diagnosticsSent : Int = 0

    /**
     * Current state of the parser.
     */
    private var state = NOT_IN_ERROR_OR_WARNING

    /**
     * Processes the given line, and calls send() for any current/past line(s) that are terminated
     * (e.g., finished diagnostic statement or block).
     */
    override fun consume(rawLine : String) {
        // Remove "C/C++: " that may have been prepended by AGP.
        val line = rawLine.substringAfter("C/C++: ")

        // Classify the type of this line.
        val (classification, match) = classifyLine(line)

        // Send the current message if one exists.
        if (classification.terminatesPriorDiagnostic) {
            sendCurrent()
        }

        // Check whether this is a main error line and, if it is, record it.
        if (
            // If we're currently in an error and this line looks like an error that can't be
            // attached to an existing error, then record this as the new main error.
            (state == IN_ERROR_OR_WARNING
                    && classification.mayBeMainDiagnostic
                    && !classification.mayComeAfterMainDiagnostic)
            // If we're not currently in an error but this line can be a main error then record this
            // as the new main error.
            || (state == NOT_IN_ERROR_OR_WARNING
                    && classification.mayBeMainDiagnostic)) {
                match ?: error("Expected non-null match")
                details = DiagnosticDetails(
                    classification = classification,
                    file = match.regexFieldOrNull("file")?.let {
                        makePathRelativeToWorkingDirectory(it, match)
                    },
                    line = match.regexFieldOrNull("line")?.toInt(),
                    column = match.regexFieldOrNull("column")?.toInt(),
                    body = match.regexField("body")
                )
        }

        // Record raw lines to attach to the message that will eventually be sent,
        if (
            // Record raw line when the current line is an error or information about the
            // current error
            classification.mayBeMainDiagnostic
            // Record raw line speculatively if it looks like it could precede an error
            || classification.mayPrecedeMainDiagnostic
            // If we're definitely in an error then record unmatched (NONE) lines as well
            || (state == IN_ERROR_OR_WARNING && classification == NONE)) {
            diagnosticLines.add(makePathRelativeToWorkingDirectory(line, match))
        }

        // Handle special purpose matches.
        when (classification) {
            NINJA_ENTERING_DIRECTORY -> {
                workingDirectory = match?.regexField("dir")
                abi = File(workingDirectory!!).name
                if (!abis.contains(abi!!)) {
                    abi = null
                }
                messagesBeforeDiagnostic.add(Generic(line, classification))
            }
            NDK_BUILD_BEGIN_ABI -> {
                abi = match!!.regexField("abi")
                messagesBeforeDiagnostic.add(Generic(line, classification))
            }
            CLANG_COMMAND_LINE -> {
                commandLine = line
                messagesBeforeDiagnostic.add(Generic(line, classification))
            }
            else -> { }
        }

        // If this line terminates STDOUT and some errors or warnings were seen, then send
        // this line so that Android Studio can recognize it and end any running diagnostics.
        if (classification.terminatesStdout) {
            if (diagnosticsSent > 0) {
                send(Generic(line, classification))
                diagnosticsSent = 0
            }
        }

        // Set state for next lines.
        state = if (classification.mayBeMainDiagnostic) {
            IN_ERROR_OR_WARNING
        } else if (classification.terminatesPriorDiagnostic || classification.mayPrecedeMainDiagnostic) {
            NOT_IN_ERROR_OR_WARNING
        } else state
    }

    /**
     * When possible, convert relative paths in diagnostics into absolute paths that are easier
     * for the user to reason about.
     */
    private fun makePathRelativeToWorkingDirectory(line : String, matchResult : MatchResult?) : String {
        if (workingDirectory == null) return line
        if (matchResult == null) return line
        val file = matchResult.regexFieldOrNull("file") ?: return line
        if (File(file).isRooted) return line
        val resolved = try {
                Paths.get(File(this.workingDirectory!!).resolve(file).path)
            } catch (e : InvalidPathException) {
                // Handle unlikely case found by fuzzing
                return line
            }
        val absolutePath =
            try {
                resolved.toRealPath()
            } catch (e: IOException) {
                // Handle unlikely case found by fuzzing
                resolved.normalize()
            }.toString()
        return line.replace(file, absolutePath)
    }

    /**
     * Send the current accumulated Diagnostic if there is one.
     */
    private fun sendCurrent() {
        details?.apply {
            messagesBeforeDiagnostic.forEach { send(it) }
            applyLockedOutputFileMessage(diagnosticLines) // See b/124104842
            send(Diagnostic(diagnosticLines, classification, abi, file, body, line, column, commandLine))
            ++diagnosticsSent
            messagesBeforeDiagnostic = mutableListOf()
            diagnosticLines = mutableListOf()
            details = null
        }
    }

    /**
     * Finish work by sending last accumulated Diagnostic.
     */
    fun flush() = sendCurrent()

    /**
     * Close this parser.
     */
    override fun close() = flush()

    companion object {
        private const val MESSAGE_BUG_124104842 =
            "This may be caused by insufficient permissions or files being locked " +
                    "by other processes. For example, LLDB may lock .so files while debugging."

        private fun DiagnosticDetails.applyLockedOutputFileMessage(lines : MutableList<String>) {
            if (classification == CLANG_FATAL_LINKER_ERROR_BUG_124104842
                && !lines.any { it.contains(MESSAGE_BUG_124104842) }) {
                lines += listOf("", "File $file could not be written. $MESSAGE_BUG_124104842")
            }
        }
    }
}

private const val errorTags = "error|fatal error"
private const val warningTags = "warn|warning"
private const val informationalTags = "ignored|note|remark"
private const val abis = "x86|x86_64|arm64-v8a|armeabi-v7a"

/**
 * Classification of lines from clang tool STDOUT.
 */
enum class NativeToolLineClassification(
    /**
     * ERROR, WARNING or INFO
     */
    val kind: Kind,
    /**
     * Regex that identifies lines matching this classification.
     */
    val regex: Regex,
    /**
     * Whether matching lines should terminate existing accumulated diagnostic.
     */
    val terminatesPriorDiagnostic: Boolean,
    /**
     * Whether matching lines indicate that the entire STDOUT is ending.
     */
    val terminatesStdout: Boolean,
    /**
     * True if this line is a candidate to be a main error or warning. The main diagnostic
     * is the error or warning that should be shown as the node title text of the build output
     * tree. It is typically contains ": error:" or ": warning:".
     *
     * For example,
     *   /path/to/app.cpp:5:3: error: no matching function for call to 'bar'
     */
    val mayBeMainDiagnostic: Boolean,
    /**
     * True if this line is a candidate to be appended to a main diagnostic.
     *
     * For example,
     *   /path/to/app.cpp:1:13: note: candidate not viable: no known conversion from...
     */
    val mayComeAfterMainDiagnostic: Boolean,
    /**
     * Whether this is a line that can come before the main diagnostic.
     * For example, the first two lines of:
     *   In file included from /path/to/app.cpp:3:
     *   In file included from /path/tp/include1.h:7:
     *   /path/to/include2.h:7:1: error: unknown type name 'snake'
     */
    val mayPrecedeMainDiagnostic: Boolean
) {
    CLANG_FILES_INCLUDED_FROM(
        kind = INFO,
        regex = Regex("In file included from (?<file>.+):(?<line>\\d+):"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = false,
        mayBeMainDiagnostic = false,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = true
    ),
    CLANG_COMPILER_ERROR(
        kind = ERROR,
        regex = Regex("(?<file>(?:[A-Z]:)?[^\\s][^:]+):(?<line>\\d+):(?<column>\\d+): ($errorTags): (?<body>.*)"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = false,
        mayBeMainDiagnostic = true,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    CLANG_COMPILER_WARNING(
        kind = WARNING,
        regex = Regex("(?<file>(?:[A-Z]:)?[^\\s][^:]+):(?<line>\\d+):(?<column>\\d+): ($warningTags): (?<body>.*)"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = false,
        mayBeMainDiagnostic = true,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    CLANG_FATAL_LINKER_ERROR_BUG_124104842(
        kind = ERROR,
        regex = Regex(".*ld: fatal error: (?<file>.*): (?<body>open: Invalid argument)"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = false,
        mayBeMainDiagnostic = true,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    CLANG_LINKER_ERROR(
        kind = ERROR,
        regex = Regex("((?:[A-Z]:)?[^\\s][^:]+)(?::(\\d+))?: ($errorTags)?: (?<body>.+)"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = false,
        mayBeMainDiagnostic = true,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    CLANG_LINKER_WARNING(
        kind = WARNING,
        regex = Regex("((?:[A-Z]:)?[^\\s][^:]+)(?::(\\d+))?: ($warningTags)?: (?<body>.+)"),
        terminatesPriorDiagnostic = false,
        terminatesStdout = false,
        mayBeMainDiagnostic = true,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    CLANG_COMPILER_INFORMATIONAL(
        kind = INFO,
        regex = Regex("(?<file>(?:[A-Z]:)?[^\\s][^:]+):(\\d+):(\\d+): ($informationalTags): (?<body>.*)"),
        terminatesPriorDiagnostic = false,
        terminatesStdout = false,
        mayBeMainDiagnostic = true,
        mayComeAfterMainDiagnostic = true,
        mayPrecedeMainDiagnostic = false
    ),
    CLANG_LINKER_INFORMATIONAL(
        kind = INFO,
        regex = Regex("((?:[A-Z]:)?[^\\s][^:]+)(?::(\\d+))?: ($informationalTags)?: (?<body>.+)"),
        terminatesPriorDiagnostic = false,
        terminatesStdout = false,
        mayBeMainDiagnostic = true,
        mayComeAfterMainDiagnostic = true,
        mayPrecedeMainDiagnostic = false
    ),
    CLANG_ERRORS_GENERATED(
        kind = INFO,
        regex = Regex("([0-9]* warnings?)?( and )?([0-9]* errors?)? generated."),
        terminatesPriorDiagnostic = true,
        terminatesStdout = true,
        mayBeMainDiagnostic = false,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    NINJA_ENTERING_DIRECTORY(
        kind = INFO,
        regex = Regex("ninja: Entering directory [`'](?<dir>[^']+)'"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = false,
        mayBeMainDiagnostic = false,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    NINJA_BUILD_STOPPED(
        kind = INFO,
        regex = Regex("ninja: build stopped.*"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = true,
        mayBeMainDiagnostic = false,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    NDK_BUILD_BEGIN_ABI(
        kind = INFO,
        regex = Regex("\\[(?<abi>$abis)] Compile.*"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = false,
        mayBeMainDiagnostic = false,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    CLANG_COMMAND_LINE(
        kind = INFO,
        regex = Regex(".*clang(\\+\\+)?(\\.exe)? .*--target.*android.*"),
        terminatesPriorDiagnostic = true,
        terminatesStdout = false,
        mayBeMainDiagnostic = false,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    ),
    NONE(
        kind = INFO,
        regex = Regex(".*"),
        terminatesPriorDiagnostic = false,
        terminatesStdout = false,
        mayBeMainDiagnostic = false,
        mayComeAfterMainDiagnostic = false,
        mayPrecedeMainDiagnostic = false
    );

    enum class Kind {
        ERROR,
        WARNING,
        INFO
    }
}

private fun classifyLine(message: String) : Pair<NativeToolLineClassification, MatchResult?> {
    // Quick short-circuit to avoid checking all regex against all lines
    if (!message.contains(':') && !message.contains("error") && !message.contains("warning"))
        return NONE to null
    for (classification in NativeToolLineClassification.values()) {
        val matchResult = classification.regex.matchEntire(message)
        if (matchResult != null) return classification to matchResult
    }
    return NONE to null
}

private fun MatchResult.regexFieldOrNull(field : String) =
    try {
       groups[field]?.value
    } catch(e : Throwable) {
        null
    }

fun MatchResult.regexField(field : String) = regexFieldOrNull(field) ?: error(field)

interface LineOutputStream : AutoCloseable {
    fun consume(line : String)
}
