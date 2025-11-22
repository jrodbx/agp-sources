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

package com.android.build.gradle.internal.cxx.caching

import com.android.build.gradle.internal.cxx.string.StringDecoder
import com.android.build.gradle.internal.cxx.string.StringEncoder

/**
 * Methods in this class convert between decoded and encoded versions
 * of C/C++ build cache protocol buffers.
 * Encoded means strings and lists of strings have been converted to
 * their int counterparts.
 */

/**
 * Encode a [ObjectFileCacheEvent] to [EncodedObjectFileCacheEvent]
 */
fun ObjectFileCacheEvent.encode(encoder: StringEncoder): EncodedObjectFileCacheEvent {
    return EncodedObjectFileCacheEvent.newBuilder()
        .setOutcome(outcome)
        .setKeyDisplayNameId(encoder.encode(keyDisplayName))
        .setKeyHashCodeId(encoder.encode(keyHashCode))
        .setCompilation(compilation.encode(encoder))
        .setHashedCompilation(hashedCompilation.encode(encoder))
        .build()
}

/**
 * Encode a [EncodedObjectFileCacheEvent] to [ObjectFileCacheEvent]
 */
fun EncodedObjectFileCacheEvent.decode(decoder: StringDecoder): ObjectFileCacheEvent {
    return ObjectFileCacheEvent.newBuilder()
        .setOutcome(outcome)
        .setKeyDisplayName(decoder.decode(keyDisplayNameId))
        .setKeyHashCode(decoder.decode(keyHashCodeId))
        .setCompilation(compilation.decode(decoder))
        .setHashedCompilation(hashedCompilation.decode(decoder))
        .build()
}

/**
 * Call EncodedObjectFileCacheEvent#decode in a pattern that can be
 * used directly with [readStructuredLogs].
 */
fun decodeObjectFileCacheEvent(
    encoded : EncodedObjectFileCacheEvent,
    decoder : StringDecoder) = encoded.decode(decoder)

/**
 * Encode a [Compilation] to [EncodedCompilation]
 */
fun Compilation.encode(encoder : StringEncoder) : EncodedCompilation {
    return EncodedCompilation.newBuilder()
        .setWorkingDirectoryId(encoder.encode(workingDirectory))
        .setObjectFileKey(objectFileKey.encode(encoder))
        .setObjectFileId(encoder.encode(objectFile))
        .build()
}

/**
 * Decode a [EncodedCompilation] to [Compilation]
 */
fun EncodedCompilation.decode(decoder: StringDecoder): Compilation {
    return Compilation.newBuilder()
        .setWorkingDirectory(decoder.decode(workingDirectoryId))
        .setObjectFileKey(objectFileKey.decode(decoder))
        .setObjectFile(decoder.decode(objectFileId))
        .build()
}

/**
 * Encode a [ObjectFileKey] to [EncodedObjectFileKey]
 */
fun ObjectFileKey.encode(encoder : StringEncoder) : EncodedObjectFileKey {
    return EncodedObjectFileKey.newBuilder()
        .setDependencyKey(dependencyKey.encode(encoder))
        .addAllDependencyIds(dependenciesList.map { encoder.encode(it) })
        .build()
}

/**
 * Decode a [EncodedObjectFileKey] to [ObjectFileKey]
 */
fun EncodedObjectFileKey.decode(decoder: StringDecoder): ObjectFileKey {
    return ObjectFileKey.newBuilder()
        .setDependencyKey(dependencyKey.decode(decoder))
        .addAllDependencies(dependencyIdsList.map { decoder.decode(it) })
        .build()
}

/**
 * Encode a [DependenciesKey] to [EncodedDependenciesKey]
 */
fun DependenciesKey.encode(encoder : StringEncoder) : EncodedDependenciesKey {
    return EncodedDependenciesKey.newBuilder()
        .setSourceFileId(encoder.encode(sourceFile))
        .setCompilerFlagsId(encoder.encodeList(compilerFlagsList))
        .build()
}

/**
 * Decode a [EncodedDependenciesKey] to [DependenciesKey]
 */
fun EncodedDependenciesKey.decode(decoder: StringDecoder): DependenciesKey {
    return DependenciesKey.newBuilder()
        .setSourceFile(decoder.decode(sourceFileId))
        .addAllCompilerFlags(decoder.decodeList(compilerFlagsId))
        .build()
}
