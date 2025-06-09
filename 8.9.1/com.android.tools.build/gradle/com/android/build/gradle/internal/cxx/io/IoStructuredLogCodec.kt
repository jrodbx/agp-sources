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

package com.android.build.gradle.internal.cxx.io

import com.android.build.gradle.internal.cxx.string.StringDecoder
import com.android.build.gradle.internal.cxx.string.StringEncoder

/**
 * Transform a [SynchronizeFile] into an [EncodedSynchronizeFile] by encoding
 * strings with [StringEncoder].
 */
fun SynchronizeFile.encode(encoder : StringEncoder) : EncodedSynchronizeFile {
    val encoded = EncodedSynchronizeFile.newBuilder()
    encoded.workingDirectoryId = encoder.encode(workingDirectory)
    encoded.sourceFileId = encoder.encode(sourceFile)
    encoded.destinationFileId = encoder.encode(destinationFile)
    encoded.initialFileComparison = initialFileComparison
    encoded.outcome = outcome
    return encoded.build()
}

/**
 * Transform a [EncodedSynchronizeFile] into an [SynchronizeFile] by decoding
 * strings with [StringDecoder].
 */
fun EncodedSynchronizeFile.decode(decoder : StringDecoder) : SynchronizeFile {
    val decoded = SynchronizeFile.newBuilder()
    decoded.workingDirectory = decoder.decode(workingDirectoryId)
    decoded.sourceFile = decoder.decode(sourceFileId)
    decoded.destinationFile = decoder.decode(destinationFileId)
    decoded.initialFileComparison = this.initialFileComparison
    decoded.outcome = this.outcome
    return decoded.build()
}

/**
 * Helper function for calling decode for this [EncodedSynchronizeFile]
 */
fun decodeSynchronizeFile(
    encoded : EncodedSynchronizeFile,
    decoder : StringDecoder
) = encoded.decode(decoder)

/**
 * Transform a [FileFingerPrint] into an [EncodedFileFingerPrint] by encoding
 * strings with [StringEncoder].
 */
fun FileFingerPrint.encode(encoder : StringEncoder) : EncodedFileFingerPrint {
    val encoded = EncodedFileFingerPrint.newBuilder()
    encoded.fileName = encoder.encode(fileName)
    encoded.isFile = isFile
    encoded.lastModified = lastModified
    encoded.length = length
    return encoded.build()
}

/**
 * Transform a [EncodedFileFingerPrint] into an [FileFingerPrint] by decoding
 * strings with [StringDecoder].
 */
fun EncodedFileFingerPrint.decode(decoder: StringDecoder) : FileFingerPrint {
    val decoded = FileFingerPrint.newBuilder()
    decoded.fileName = decoder.decode(fileName)
    decoded.isFile = isFile
    decoded.lastModified = lastModified
    decoded.length = length
    return decoded.build()
}

/**
 * Helper function for calling decode for this [EncodedFileFingerPrint]
 */
fun decodeFileFingerPrint(
    encoded : EncodedFileFingerPrint,
    decoder : StringDecoder
) = encoded.decode(decoder)

