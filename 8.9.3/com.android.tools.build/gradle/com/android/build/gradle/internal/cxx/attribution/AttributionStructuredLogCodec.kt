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

package com.android.build.gradle.internal.cxx.attribution

import com.android.build.gradle.internal.cxx.string.StringDecoder
import com.android.build.gradle.internal.cxx.string.StringEncoder

fun BuildTaskAttribution.encode(encoder : StringEncoder) : EncodedBuildTaskAttribution {
    return EncodedBuildTaskAttribution.newBuilder()
        .setOutputFileId(encoder.encode(outputFile))
        .setStartTimeOffsetMs(startTimeOffsetMs)
        .setEndTimeOffsetMs(endTimeOffsetMs)
        .build()
}

fun EncodedBuildTaskAttribution.decode(decoder: StringDecoder): BuildTaskAttribution {
    return BuildTaskAttribution.newBuilder()
        .setOutputFile(decoder.decode(outputFileId))
        .setStartTimeOffsetMs(startTimeOffsetMs)
        .setEndTimeOffsetMs(endTimeOffsetMs)
        .build()
}

fun AttributionKey.encode(encoder : StringEncoder) : EncodedAttributionKey {
    return EncodedAttributionKey.newBuilder()
        .setModuleId(encoder.encode(module))
        .setVariantId(encoder.encode(variant))
        .setAbiId(encoder.encode(abi))
        .build()
}

fun EncodedAttributionKey.decode(decoder: StringDecoder): AttributionKey {
    return AttributionKey.newBuilder()
        .setModule(decoder.decode(moduleId))
        .setVariant(decoder.decode(variantId))
        .setAbi(decoder.decode(abiId))
        .build()
}

fun BuildTaskAttributions.encode(encoder : StringEncoder) : EncodedBuildTaskAttributions {
    return EncodedBuildTaskAttributions.newBuilder()
        .setBuildFolderId(encoder.encode(buildFolder))
        .setKey(key.encode(encoder))
        .setNinjaLogStartLine(ninjaLogStartLine)
        .setBuildStartTimeMs(buildStartTimeMs)
        .addAllLibraryId(libraryList.map { encoder.encode(it) })
        .addAllAttribution(attributionList.map { it.encode(encoder) })
        .build()
}

fun EncodedBuildTaskAttributions.decode(decoder : StringDecoder) : BuildTaskAttributions {
    return BuildTaskAttributions.newBuilder()
        .setBuildFolder(decoder.decode(buildFolderId))
        .setKey(key.decode(decoder))
        .setNinjaLogStartLine(ninjaLogStartLine)
        .setBuildStartTimeMs(buildStartTimeMs)
        .addAllLibrary(libraryIdList.map { decoder.decode(it) })
        .addAllAttribution(attributionList.map { it.decode(decoder) })
        .build()
}

fun decodeBuildTaskAttributions(
    encoded : EncodedBuildTaskAttributions,
    decoder : StringDecoder) = encoded.decode(decoder)

