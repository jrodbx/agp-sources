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

package com.android.build.gradle.internal.cxx.logging

import com.android.build.gradle.internal.cxx.logging.StructuredLogRecord.RecordCase.NEW_LIST
import com.android.build.gradle.internal.cxx.logging.StructuredLogRecord.RecordCase.NEW_STRING
import com.android.build.gradle.internal.cxx.logging.StructuredLogRecord.RecordCase.PAYLOAD_HEADER
import com.android.build.gradle.internal.cxx.string.StringDecoder
import com.android.build.gradle.internal.cxx.string.StringEncoder
import com.android.build.gradle.internal.cxx.string.StringTable
import com.google.common.base.Preconditions
import com.google.protobuf.CodedInputStream
import com.google.protobuf.GeneratedMessageV3
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.Method

/**
 * Define and implement a structured log file format. This is used to diagnose
 * complex issues in the field (in particular, caching) and to write tests that
 * validate correct caching or other behaviors where observable side-effects
 * are intentionally concealed.
 *
 * This format is designed to support larger numbers of granular messages
 * because it needs to support message-per-source-file events. For this reason,
 * it supports an embedded string table that allows strings to be store as
 * int32.
 *
 * Calling code can use it as follows:
 *
 *      CxxStructuredLogEncoder("log-file.bin") { log ->
 *          log.write(ProtoBufMessage.newBuilder()
 *              .setStringId(log.encode("string-value"))
 *              .build()
 *          )
 *      }
 *
 * Additionally, messages are only recorded if the user manually creates a
 * folder at the project root called:
 *
 *  $PROJECT/.cxx/structured-log
 *
 * If that directory doesn't exist then no per-source-file work is done.
 *
 * Reflective Payloads
 * -------------------
 * Structured logs support payloads of arbitrary protocol buffer type.
 * This is accomplished by encoding the type name the first time it is
 * seen. When the structured log is read back, the type is loaded by
 * name and its 'parseDelimitedFrom' function is invoked using the
 * stream at the current position.
 *
 * If a type was serialized that is not recognized by the current
 * class loader then [UnknownMessage] is generated and the stream is
 * forwarded to the next position after that message.
 *
 * File format
 * -----------
 * header:
 * - magic: C/C++ Structured Log{EOF} [MAGIC]
 * sequence of protobuf record of discriminated union type [StructuredLogRecord]:
 * - [NewString]: a new string. Its ID is just one greater than the last ID (zero relative).
 * - [NewList]: a new list of string. Its ID is just one greater than the last ID (zero relative).
 * - [PayloadHeader]: indicates a message, in the protobuf delimited format, is coming next.
 */
private val MAGIC = "C/C++ Structured Log\u001a".toByteArray(Charsets.UTF_8)
private const val FIRST_ID = 1 // Reserve 0 for error case

/**
 * Utility class for encoding file changes.
 */
class CxxStructuredLogEncoder(
    val file : File,
) : StringEncoder, AutoCloseable {
    val output: DataOutputStream
    val strings = StringTable(nextId = FIRST_ID)
    init {
        // Create the file if it doesn't already exist.
        if (!file.exists()) {
            Preconditions.checkArgument(file.parentFile.isDirectory)
            DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { output ->
                output.write(MAGIC)
            }
        }
        // Read any existing strings into the string table.
        if (file.exists()) {
            streamCxxStructuredLogUntyped(file, strings) { input,_ ->
                skipPayload(input)
            }
        }
        output = DataOutputStream(BufferedOutputStream(FileOutputStream(file, true)))
    }

    /**
     * Get the integer ID for [string]. If [string] hasn't been seen before
     * then allocate a new ID for it and write a string record.
     */
    override fun encode(string : String) : Int {
        return strings.getIdCreateIfAbsent(string) {
            // If this is a new string then send the [NewString] message.
            StructuredLogRecord
                .newBuilder()
                .setNewString(
                    NewString
                        .newBuilder()
                        .setData(string)
                        .build())
                .build()
                .writeDelimitedTo(output)
        }
    }

    /**
     * Encode a string list as a single int by first encoding each element's
     * string value and then that whole list of ints is encoded as a single
     * int.
     */
    override fun encodeList(list: List<String>): Int {
        val intList = list.map { encode(it) }
        return strings.getIdCreateIfAbsent(intList) {
            // If this is a new list then send the [NewList] message first.
            StructuredLogRecord
                .newBuilder()
                .setNewList(
                    NewList
                        .newBuilder()
                        .addAllData(intList)
                        .build())
                .build()
                .writeDelimitedTo(output)
        }
    }

    /**
     * Write [message] to the structured log stream.
     * First, a [PayloadHeader] with the current time and the type [message]
     * is sent.
     * Then, [message] itself is sent.
     * Both are encoded as delimited (size first) protobuf messages.
     */
    fun write(message : GeneratedMessageV3) {
        StructuredLogRecord
            .newBuilder()
            .setPayloadHeader(
                PayloadHeader.newBuilder()
                .setTimeStampMs(System.currentTimeMillis())
                .setTypeId(encode(message.javaClass.name))
                .build())
            .build()
            .writeDelimitedTo(output)
        message.writeDelimitedTo(output)
    }

    /**
     * Flush and close.
     */
    override fun close() {
        output.flush()
        output.close()
    }
}

/**
 * Utility class for decoding file changes.
 */
class CxxStructuredLogDecoder(private val input : DataInputStream) {

    init {
        readHeader()
    }

    /**
     * Read the header and throw an exception if this doesn't look
     * like a file change file.
     */
    private fun readHeader() {
        MAGIC.forEach { expected ->
            val actual = input.read()
            if (actual != expected.toInt()) {
                error("Was not a C/C++ structured log file")
            }
        }
    }

    /**
     * Read the next [StructuredLogRecord]. Returns null at the end.
     */
    fun read() : StructuredLogRecord? {
        if (input.available() == 0) return null
        return StructuredLogRecord.parseDelimitedFrom(input)
    }
}

/**
 * Skip a payload starting at the current point in [input].
 * Return the size of the payload.
 */
private fun skipPayload(input : InputStream) : Int {
    val firstByte = input.read()
    val size = CodedInputStream.readRawVarint32(firstByte, input)
    input.skip(size.toLong())
    return size
}

/**
 * Stream all structured log records in [file].
 */
fun streamCxxStructuredLog(
    file : File,
    consumer : (StringDecoder, Long, GeneratedMessageV3) -> Unit) {
    val strings = StringTable(nextId = FIRST_ID)
    val idToClass = mutableMapOf<Int, Method?>()
    streamCxxStructuredLogUntyped(file, strings) { input, header ->
        val parseMethod = idToClass.computeIfAbsent(header.typeId) {
            val typename = strings.decode(header.typeId)
            val result = try {
                val type = Class.forName(typename) as Class<GeneratedMessageV3>
                type.getMethod("parseDelimitedFrom", InputStream::class.java)
            } catch (e: ClassNotFoundException) {
                null
            }
            result
        }
        if (parseMethod == null) {
            consumer(
                strings,
                header.timeStampMs,
                UnknownMessage
                    .newBuilder()
                    .setTypeId(header.typeId)
                    .setSizeBytes(skipPayload(input))
                    .build()
            )

        } else {
            val payload = parseMethod.invoke(null, input) as GeneratedMessageV3
            consumer(strings, header.timeStampMs, payload)
        }
    }
}

/**
 * Stream all record payload headers without instantiating the payloads.
 * The [consumer] function is expected to read or skip all of the payload
 * bytes from [InputStream].
 */
private fun streamCxxStructuredLogUntyped(
    file : File,
    strings : StringTable,
    consumer : (InputStream, PayloadHeader) -> Unit) {
    DataInputStream(FileInputStream(file)).use { input ->
        val reader = CxxStructuredLogDecoder(input)
        var record = reader.read()
        while(record != null) {
            when(record.recordCase) {
                NEW_STRING -> strings.encode(record.newString.data)
                NEW_LIST -> strings.encodeList(record.newList.dataList.map { strings.decode(it) })
                PAYLOAD_HEADER -> consumer(input, record.payloadHeader)
                else -> error("unrecognized ${record.recordCase}")
            }
            record = reader.read()
        }
    }
}

/**
 * Given a [logFolder], read combined structured log files and return, in
 * chronological order, the records of a particular type (the type returned
 * by [decode] function).
 */
inline fun <reified Encoded, Decoded> readStructuredLogs(
    logFolder : File,
    crossinline decode: (Encoded, StringDecoder) -> Decoded) : List<Decoded> {
    val logs = logFolder.walk().asIterable()
        .filter { it.isFile }
        .filter { it.extension == "bin" }
        .sortedBy { it.name }
    val events = mutableListOf<Pair<Long, Decoded>>()
    for(log in logs) {
        streamCxxStructuredLog(log) {
                strings,
                timestamp,
                event ->
            if (event is Encoded) {
                events.add(timestamp to decode(event, strings))
            }
        }
    }
    return events.sortedBy { it.first }.map { it.second }
}

inline fun <reified Encoded, Decoded> readStructuredLog(
    logFile : File,
    crossinline decode: (Encoded, StringDecoder) -> Decoded) : List<Decoded> {
    val events = mutableListOf<Pair<Long, Decoded>>()
    streamCxxStructuredLog(logFile) {
            strings,
            timestamp,
            event ->
        if (event is Encoded) {
            events.add(timestamp to decode(event, strings))
        }
    }
    return events.sortedBy { it.first }.map { it.second }
}



