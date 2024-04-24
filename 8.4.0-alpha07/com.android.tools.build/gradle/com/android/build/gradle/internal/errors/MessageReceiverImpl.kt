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

package com.android.build.gradle.internal.errors

import com.android.build.gradle.options.SyncOptions.ErrorFormatMode
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.MessageJsonSerializer
import com.android.ide.common.blame.MessageReceiver
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.parser.JsonEncodedGradleMessageParser.STDOUT_ERROR_TAG
import com.google.common.base.Joiner
import com.google.common.collect.Iterables
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.Logger

class MessageReceiverImpl constructor(
    private val errorFormatMode: ErrorFormatMode,
    private val logger: Logger): MessageReceiver {

    private val mGson: Gson?

    init {
        mGson = if (errorFormatMode == ErrorFormatMode.MACHINE_PARSABLE) {
            val gsonBuilder = GsonBuilder().disableHtmlEscaping()
            MessageJsonSerializer.registerTypeAdapters(gsonBuilder)
            gsonBuilder.create()
        } else {
            null
        }
    }

    override fun receiveMessage(message: Message) {
        when (message.kind) {
            Message.Kind.ERROR -> logger.error(messageToString(message, errorFormatMode))
            Message.Kind.WARNING -> logger.warn(messageToString(message, errorFormatMode))
            Message.Kind.INFO -> logger.info(humanReadableMessage(message))
            Message.Kind.STATISTICS -> logger.trace(humanReadableMessage(message))
            Message.Kind.UNKNOWN -> logger.warn(humanReadableMessage(message))
            Message.Kind.SIMPLE -> logger.warn(humanReadableMessage(message))
        }
    }

    private fun messageToString(message: Message, errorFormatMode: ErrorFormatMode): String =
        if (errorFormatMode == ErrorFormatMode.MACHINE_PARSABLE) {
            machineReadableMessage(message)
        } else {
            humanReadableMessage(message)
        }

    /**
     * Only call if errorFormatMode == [ErrorFormatMode.MACHINE_PARSABLE]
     */
    private fun machineReadableMessage(message: Message): String {
        return STDOUT_ERROR_TAG + mGson!!.toJson(message)
    }

}

fun humanReadableMessage(message: Message): String {
    val errorStringBuilder = StringBuilder()
    errorStringBuilder.append(message.kind.name)
    errorStringBuilder.append(": ")
    val positions = message.sourceFilePositions
    if (positions.size != 1 || SourceFilePosition.UNKNOWN != Iterables.getOnlyElement(positions)) {
        errorStringBuilder.append(Joiner.on(' ').join(positions))
        errorStringBuilder.append(": ")
    }
    if (message.toolName != null) {
        errorStringBuilder.append(message.toolName).append(": ")
    }
    errorStringBuilder.append(message.text)

    val rawMessage = message.rawMessage
    if (message.text != message.rawMessage) {
        val separator = System.lineSeparator()
        errorStringBuilder.append("\n    ")
                .append(rawMessage.replace(separator, separator + "    "))
    }
    return errorStringBuilder.toString()
}

