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
@file:JvmName("Aapt2ErrorUtils")

package com.android.build.gradle.internal.res

import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.errors.humanReadableMessage
import com.android.build.gradle.internal.tasks.manifest.findOriginalManifestFilePosition
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser
import com.android.ide.common.blame.parser.aapt.AbstractAaptOutputParser
import com.android.ide.common.resources.CompileResourceRequest
import com.android.utils.StdLogger
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Rewrite exceptions to point to their original files.
 *
 * Returns the same exception as is if it could not be rewritten.
 *
 * This is expensive, so should only be used if the build is going to fail anyway.
 * The merging log is used directly from memory, as this only is needed within the resource merger.
 */
fun rewriteCompileException(
    e: Aapt2Exception,
    request: CompileResourceRequest,
    errorFormatMode: SyncOptions.ErrorFormatMode,
    enableBlame: Boolean,
    logger: Logger
): Aapt2Exception {
    if (!enableBlame) {
        return rewriteException(e, errorFormatMode, false, logger) {
            it
        }
    }
    if (request.blameMap.isEmpty()) {
        if (request.mergeBlameFolder != null) {
            val mergingLog = MergingLog(request.mergeBlameFolder!!)
            return rewriteException(e, errorFormatMode, true, logger) {
                mergingLog.find(it)
            }
        }

        val originalException =
            if (request.inputFile == request.originalInputFile) {
                e
            } else {
                Aapt2Exception.create(
                    description = "Failed to compile android resource " +
                            "'${request.originalInputFile.absolutePath}'.",
                    cause = e,
                    output = e.output?.replace(
                        request.inputFile.absolutePath,
                        request.originalInputFile.absolutePath
                    ),
                    processName = e.processName,
                    command = e.command
                )
            }

        return rewriteException(originalException, errorFormatMode, false, logger) {
            it
        }
    }
    return rewriteException(e, errorFormatMode, true, logger) {
        if (it.file.sourceFile == request.originalInputFile) {
            MergingLog.find(it.position, request.blameMap) ?: it
        } else {
            it
        }
    }
}

/**
 * Rewrite exceptions to point to their original files.
 *
 * Returns the same exception as is if it could not be rewritten.
 *
 * This is expensive, so should only be used if the build is going to fail anyway.
 * The merging log is loaded from files lazily.
 */
fun rewriteLinkException(
    e: Aapt2Exception,
    errorFormatMode: SyncOptions.ErrorFormatMode,
    mergeBlameFolder: File?,
    manifestMergeBlameFile: File?,
    logger: Logger
): Aapt2Exception {
    if (mergeBlameFolder == null && manifestMergeBlameFile == null) {
        return rewriteException(e, errorFormatMode, false, logger) {
            it
        }
    }
    var mergingLog: MergingLog? = null
    if (mergeBlameFolder != null) {
        mergingLog = MergingLog(mergeBlameFolder)
    }

    var manifestMergeBlameContents: List<String>? = null
    if (manifestMergeBlameFile != null) {
        manifestMergeBlameContents = manifestMergeBlameFile.readLines(Charsets.UTF_8)
    }

    return rewriteException(e, errorFormatMode, true, logger) {
        var newFile = it
        if (mergingLog != null) {
            newFile = mergingLog.find(it)
        }
        // If the merging log fails to find the original position, then try the manifest merge blame
        if (it == newFile && manifestMergeBlameContents != null) {
            newFile = findOriginalManifestFilePosition(manifestMergeBlameContents, it)
        }
        newFile
    }
}

/** Attempt to rewrite the given exception using the lookup function. */
private fun rewriteException(
    e: Aapt2Exception,
    errorFormatMode: SyncOptions.ErrorFormatMode,
    rewriteFilePositions: Boolean,
    logger: Logger,
    blameLookup: (SourceFilePosition) -> SourceFilePosition
): Aapt2Exception {
    try {
        var messages =
                ToolOutputParser(
                        Aapt2OutputParser(),
                        Message.Kind.SIMPLE,
                        StdLogger(StdLogger.Level.INFO)
                ).parseToolOutput(e.output ?: "", true)
        if (messages.isEmpty()) {
            // No messages were parsed, create a dummy message.
            messages = listOf(
                Message(
                    Message.Kind.ERROR,
                    e.output ?: "",
                    "",
                    AbstractAaptOutputParser.AAPT_TOOL_NAME,
                    SourceFilePosition.UNKNOWN
                )
            )
        }

        if (rewriteFilePositions) {
            messages = messages.map { message ->
                message.copy(
                    sourceFilePositions = rewritePositions(
                        message.sourceFilePositions,
                        blameLookup
                    )
                )
            }
        }

        val detailedMessage = messages.joinToString("\n") {
            humanReadableMessage(it)
        }

        // Log messages in a json format so parsers can parse and show them in the build output
        // window.
        if (errorFormatMode == SyncOptions.ErrorFormatMode.MACHINE_PARSABLE) {
            MessageReceiverImpl(errorFormatMode, logger).run {
                messages.map { message ->
                    message.copy(
                        text = e.description,
                        rawMessage = humanReadableMessage(message)
                    )
                }.forEach(this::receiveMessage)
            }
        }

        return Aapt2Exception.create(
            description = e.description,
            cause = e.cause,
            output = detailedMessage,
            processName = e.processName,
            command = e.command
        )
    } catch (e2: Exception) {
        // Something went wrong, report the original error with the error reporting error suppressed
        return e.apply { addSuppressed(e2) }
    }
}

private fun rewritePositions(
    sourceFilePositions: List<SourceFilePosition>,
    blameLookup: (SourceFilePosition) -> SourceFilePosition
): ImmutableList<SourceFilePosition> =
    ImmutableList.builder<SourceFilePosition>().apply {
        sourceFilePositions.forEach { add(blameLookup.invoke(it)) }
    }.build()