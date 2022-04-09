package com.android.tools.profgen

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.experimental.or


/** Serialization encoding for an inline cache which misses type definitions.  */
private const val INLINE_CACHE_MISSING_TYPES_ENCODING = 6

/** Serialization encoding for a megamorphic inline cache.  */
private const val INLINE_CACHE_MEGAMORPHIC_ENCODING = 7

private const val MAX_NUM_CLASS_IDS = 1 shl 16

internal fun profileKey(dexName: String, apkName: String, separator: String): String {
    if (apkName.isEmpty()) return enforceSeparator(dexName, separator)
    if (dexName == "classes.dex") return apkName
    if (dexName.contains("!") || dexName.contains(":")) return enforceSeparator(dexName, separator)
    if (dexName.endsWith(".apk")) return dexName
    assert(apkName.indexOf(separator) == -1)
    assert(dexName.indexOf(separator) == -1)
    return "$apkName$separator$dexName"
}

private fun enforceSeparator(value: String, separator: String): String {
    return when (separator) {
        "!" -> value.replace(":", "!")
        ":" -> value.replace("!", ":")
        else -> value
    }
}

enum class ArtProfileSerializer(
    internal val versionBytes: ByteArray,
    internal val magicBytes: ByteArray = MAGIC
) {
    /**
     * 0.0.1 Metadata Serialization format (N):
     * =============================================
     *
     * [profile_header, zipped[[dex_data_header1, dex_data_header2...],[dex_data1,
     *    dex_data2...], global_aggregation_count]]
     * profile_header:
     *   magic,version,number_of_dex_files,uncompressed_size_of_zipped_data,compressed_data_size
     * dex_data_header:
     *   dex_location,number_of_classes
     * dex_data:
     *   class_id1,class_id2...
     */
    METADATA_FOR_N(
            byteArrayOf('0', '0', '1', '\u0000'),
            byteArrayOf('p', 'r', 'm', '\u0000')
    ) {
        override fun write(
                os: OutputStream,
                profileData: Map<DexFile, DexFileData>,
                apkName: String
        ) = with(os) {
            // Write the profile data in a byte array first. The array will need to be compressed before
            // writing it in the final output stream.
            val profileBytes = createCompressibleBody(
                    profileData.entries.sortedBy { it.key.name },
                    apkName
            )
            writeUInt8(profileData.size) // number of dex files
            writeUInt32(profileBytes.size.toLong())
            writeCompressed(profileBytes)
        }

        /**
         * Serializes the profile data in a byte array. This methods only serializes the actual
         * profile content and not the necessary headers.
         */
        private fun createCompressibleBody(profileData: List<Map.Entry<DexFile, DexFileData>>, apkName: String): ByteArray {
            // Start by creating a couple of caches for the data we re-use during serialization.

            // The required capacity in bytes for the uncompressed profile data.
            var requiredCapacity = 0
            // Maps dex file to the size their method region will occupy. We need this when computing the
            // overall size requirements and for serializing the dex file data. The computation is
            // expensive as it walks all methods recorded in the profile.
            for ((dexFile, dexFileData) in profileData) {
                val lineHeaderSize =
                        ( UINT_16_SIZE // classes set size
                                + UINT_16_SIZE) // dex location size
                requiredCapacity += (lineHeaderSize
                        + profileKey(dexFile.name, apkName, "!").utf8Length
                        + dexFileData.classIndexes.size * UINT_16_SIZE)
            }

            // Start serializing the data.
            val dataBos = ByteArrayOutputStream(requiredCapacity)

            // Dex files must be written in the order of their profile index. This
            // avoids writing the index in the output file and simplifies the parsing logic.
            // Write profile line headers.

            // Write dex file line headers.
            for ((dexFile, dexData) in profileData) {
                with(dataBos) {
                    val profileKey = profileKey(dexFile.name, apkName, "!")
                    writeUInt16(profileKey.utf8Length)
                    writeUInt16(dexData.classIndexes.size)
                    writeString(profileKey)
                }
            }

            // Write dex file data.
            for ((_, data) in profileData) {
                dataBos.writeIntsAsDeltas(
                        data.classIndexes,
                )
            }

            check(dataBos.size() == requiredCapacity) {
                ("The bytes saved do not match expectation. actual="
                        + dataBos.size() + " expected=" + requiredCapacity)
            }
            return dataBos.toByteArray()
        }

        override fun read(src: InputStream): Map<DexFile, DexFileData> = with(src) {
            val numberOfDexFiles = readUInt8()
            val uncompressedDataSize = readUInt32()
            val compressedDataSize = readUInt32()
            val uncompressedData = readCompressed(
                    compressedDataSize.toInt(),
                    uncompressedDataSize.toInt()
            )
            if (read() > 0) error("Content found after the end of file")

            val dataStream = uncompressedData.inputStream()

            dataStream.readUncompressedBody(numberOfDexFiles)
        }

        private fun InputStream.readUncompressedBody(numberOfDexFiles: Int): Map<DexFile, DexFileData> {
            // If the uncompressed profile data stream is empty then we have nothing more to do.
            if (available() == 0) {
                return emptyMap()
            }
            // Read the dex file line headers.
            val dexFileData = Array(numberOfDexFiles) {
                val profileKeySize = readUInt16()
                val classSetSize = readUInt16()
                val profileKey = readString(profileKeySize)
                val dexFile = DexFile(
                        header = DexHeader(
                                stringIds = Span.Empty,
                                typeIds = Span.Empty,
                                prototypeIds = Span.Empty,
                                methodIds = Span.Empty,
                                classDefs = Span.Empty,
                                data = Span.Empty,
                        ),
                        dexChecksum = 0,
                        name = profileKey,
                )
                MutableDexFileData(
                        classIdSetSize = classSetSize,
                        typeIdSetSize = 0,
                        hotMethodRegionSize = 0,
                        numMethodIds = 0,
                        dexFile = dexFile,
                        classIdSet = mutableSetOf(),
                        typeIdSet = mutableSetOf(),
                        methods = mutableMapOf(),
                )
            }

            // Load data for each discovered dex file.
            for (data in dexFileData) {
                // Load the classes.
                readIntsAsDeltas(data.classIdSetSize, data.classIdSet)
            }
            return dexFileData.map {
                it.dexFile to it.asDexFileData()
            }.toMap()
        }

    },

    /**
     * 0.1.0 Serialization format (P/Android 9.0.0):
     * =============================================
     *
     * [profile_header, zipped[[dex_data_header1, dex_data_header2...],[dex_data1,
     *    dex_data2...], global_aggregation_count]]
     * profile_header:
     *   magic,version,number_of_dex_files,uncompressed_size_of_zipped_data,compressed_data_size
     * dex_data_header:
     *   dex_location,number_of_classes,methods_region_size,dex_location_checksum,num_method_ids
     * dex_data:
     *   method_encoding_1,method_encoding_2...,class_id1,class_id2...,startup/post startup bitmap,
     *   aggregation_counters_for_classes, aggregation_counters_for_methods.
     * The method_encoding is:
     *    method_id,number_of_inline_caches,inline_cache1,inline_cache2...
     * The inline_cache is:
     *    dex_pc,[M|dex_map_size], dex_profile_index,class_id1,class_id2...,dex_profile_index2,...
     *    dex_map_size os the number of dex_indices that follows.
     *       Classes are grouped per their dex files and the line
     *       `dex_profile_index,class_id1,class_id2...,dex_profile_index2,...` encodes the
     *       mapping from `dex_profile_index` to the set of classes `type_id1,type_id2...`
     *    M stands for megamorphic or missing types and it's encoded as either
     *    the byte kIsMegamorphicEncoding or kIsMissingTypesEncoding.
     *    When present, there will be no class ids following.
     * The aggregation_counters_for_classes is stored only for 5.0.0 version and its format is:
     *   num_classes,count_for_class1,count_for_class2....
     * The aggregation_counters_for_methods is stored only for 5.0.0 version and its format is:
     *   num_methods,count_for_method1,count_for_method2....
     * The aggregation counters are sorted based on the index of the class/method.
     */
    V0_1_0_P(byteArrayOf('0', '1', '0', '\u0000')) {
        override fun write(
                os: OutputStream,
                profileData: Map<DexFile, DexFileData>,
                apkName: String
        ) = with(os) {
            // Write the profile data in a byte array first. The array will need to be compressed before
            // writing it in the final output stream.
            val profileBytes = createCompressibleBody(
                    profileData.entries.sortedBy { it.key.name },
                    apkName
            )
            writeUInt8(profileData.size) // number of dex files
            writeUInt32(profileBytes.size.toLong())
            writeCompressed(profileBytes)
        }

        /**
         * Serializes the profile data in a byte array. This methods only serializes the actual
         * profile content and not the necessary headers.
         */
        private fun createCompressibleBody(profileData: List<Map.Entry<DexFile, DexFileData>>, apkName: String): ByteArray {
            // Start by creating a couple of caches for the data we re-use during serialization.

            // The required capacity in bytes for the uncompressed profile data.
            var requiredCapacity = 0
            // Maps dex file to the size their method region will occupy. We need this when computing the
            // overall size requirements and for serializing the dex file data. The computation is
            // expensive as it walks all methods recorded in the profile.
            val dexFileToHotMethodRegionSize: MutableMap<DexFile, Int> = HashMap()
            for ((dexFile, dexFileData) in profileData) {
                val hotMethodRegionSize: Int = getHotMethodRegionSize(dexFileData)
                val lineHeaderSize =
                    ( UINT_16_SIZE // classes set size
                            + UINT_16_SIZE // dex location size
                            + UINT_32_SIZE // method map size
                            + UINT_32_SIZE // checksum
                            + UINT_32_SIZE) // number of method ids
                requiredCapacity += (lineHeaderSize
                        + profileKey(dexFile.name, apkName, "!").utf8Length
                        + dexFileData.typeIndexes.size * UINT_16_SIZE + hotMethodRegionSize
                        + getMethodBitmapStorageSize(dexFile.header.methodIds.size))
                dexFileToHotMethodRegionSize[dexFile] = hotMethodRegionSize
            }

            // Start serializing the data.
            val dataBos = ByteArrayOutputStream(requiredCapacity)

            // Dex files must be written in the order of their profile index. This
            // avoids writing the index in the output file and simplifies the parsing logic.
            // Write profile line headers.

            // Write dex file line headers.
            for ((dexFile, dexFileData) in profileData) {
                dataBos.writeLineHeader(
                    dexFile,
                    apkName,
                    dexFileData,
                    dexFileToHotMethodRegionSize[dexFile]!!
                )
            }

            // Write dex file data.
            for ((dexFile, dexFileData) in profileData) {
                dataBos.writeLineData(
                    dexFile,
                    dexFileData,
                )
            }

            check(dataBos.size() == requiredCapacity) {
                ("The bytes saved do not match expectation. actual="
                        + dataBos.size() + " expected=" + requiredCapacity)
            }
            return dataBos.toByteArray()
        }

        override fun read(src: InputStream): Map<DexFile, DexFileData> = with(src) {
            val numberOfDexFiles = readUInt8()
            val uncompressedDataSize = readUInt32()
            val compressedDataSize = readUInt32()
            val uncompressedData = readCompressed(
                compressedDataSize.toInt(),
                uncompressedDataSize.toInt()
            )
            if (read() > 0) error("Content found after the end of file")

            val dataStream = uncompressedData.inputStream()

            dataStream.readUncompressedBody(numberOfDexFiles)
        }

        private fun InputStream.readUncompressedBody(numberOfDexFiles: Int): Map<DexFile, DexFileData> {
            // If the uncompressed profile data stream is empty then we have nothing more to do.
            if (available() == 0) {
                return emptyMap()
            }
            // Read the dex file line headers.
            val dexFileData = Array(numberOfDexFiles) {
                val profileKeySize = readUInt16()
                val typeIdSetSize = readUInt16()
                val hotMethodRegionSize = readUInt32()
                val dexChecksum = readUInt32()
                val numMethodIds = readUInt32()
                val profileKey = readString(profileKeySize)
                val dexFile = DexFile(
                    header = DexHeader(
                        stringIds = Span.Empty,
                        typeIds = Span.Empty,
                        prototypeIds = Span.Empty,
                        methodIds = Span(numMethodIds.toInt(), 0),
                        classDefs = Span.Empty,
                        data = Span.Empty,
                    ),
                    dexChecksum = dexChecksum,
                    name = profileKey,
                )
                MutableDexFileData(
                    classIdSetSize = 0,
                    typeIdSetSize = typeIdSetSize,
                    hotMethodRegionSize = hotMethodRegionSize.toInt(),
                    numMethodIds = numMethodIds.toInt(),
                    dexFile = dexFile,
                    classIdSet = mutableSetOf(),
                    typeIdSet = mutableSetOf(),
                    methods = mutableMapOf(),
                )
            }

            // Load data for each discovered dex file.
            for (data in dexFileData) {
                // Load the hot methods with inline caches.
                // Keep a copy of the stream hot methods. We need it in case this is a merge so we
                // do not increase the aggregation counter again when we parse the method bitmap.
                // If this is a non merge operation the set will be empty.
                readHotMethodRegion(data)

                // Load the classes.
                readIntsAsDeltas(data.typeIdSetSize, data.typeIdSet)

                // Load the method bitmap (startup & post startup).
                readMethodBitmap(data)
            }
            return dexFileData.map {
                it.dexFile to it.asDexFileData()
            }.toMap()
        }

    },
    /**
     * 0.0.9 Serialization format (O MR1/Android 8.1.0):
     * =============================================
     *
     * Serialization format:
     *    magic,version,number_of_dex_files,uncompressed_size_of_zipped_data,compressed_data_size,
     *    zipped[dex_location1,number_of_classes1,methods_region_size,dex_location_checksum1
     *        num_method_ids,
     *        method_encoding_11,method_encoding_12...,type_id1,type_id2...
     *        startup/post startup bitmap,
     *    dex_location2,number_of_classes2,methods_region_size,dex_location_checksum2, num_method_ids,
     *        method_encoding_21,method_encoding_22...,,type_id1,type_id2...
     *        startup/post startup bitmap,
     *    .....]
     * The method_encoding is:
     *    method_id,number_of_inline_caches,inline_cache1,inline_cache2...
     * The inline_cache is:
     *    dex_pc,[M|dex_map_size], dex_profile_index,class_id1,class_id2...,dex_profile_index2,...
     *    dex_map_size is the number of dex_indices that follows.
     *       Classes are grouped per their dex files and the line
     *       `dex_profile_index,class_id1,class_id2...,dex_profile_index2,...` encodes the
     *       mapping from `dex_profile_index` to the set of classes `class_id1,class_id2...`
     *    M stands for megamorphic or missing types and it's encoded as either
     *    the byte kIsMegamorphicEncoding or kIsMissingTypesEncoding.
     *    When present, there will be no class ids following.
    **/
    V0_0_9_OMR1(byteArrayOf('0', '0', '9', '\u0000')) {
        override fun write(
                os: OutputStream,
                profileData: Map<DexFile, DexFileData>,
                apkName: String
        ) = with(os) {
            // Write the profile data in a byte array first. The array will need to be compressed before
            // writing it in the final output stream.
            val profileBytes = createCompressibleBody(
                    profileData.entries.sortedBy { it.key.name },
                    apkName
            )
            writeUInt8(profileData.size) // number of dex files
            writeUInt32(profileBytes.size.toLong())
            writeCompressed(profileBytes)
        }


        /**
         * Serializes the profile data in a byte array. This methods only serializes the actual
         * profile content and not the necessary headers.
         */
        private fun createCompressibleBody(profileData: List<Map.Entry<DexFile, DexFileData>>, apkName: String): ByteArray {
            // Start by creating a couple of caches for the data we re-use during serialization.

            // The required capacity in bytes for the uncompressed profile data.
            var requiredCapacity = 0
            // Maps dex file to the size their method region will occupy. We need this when computing the
            // overall size requirements and for serializing the dex file data. The computation is
            // expensive as it walks all methods recorded in the profile.
            val dexFileToHotMethodRegionSize: MutableMap<DexFile, Int> = HashMap()
            for ((dexFile, dexFileData) in profileData) {
                val hotMethodRegionSize: Int = getHotMethodRegionSize(dexFileData)
                val lineHeaderSize =
                    ( UINT_16_SIZE // classes set size
                            + UINT_16_SIZE // dex location size
                            + UINT_32_SIZE // method map size
                            + UINT_32_SIZE // checksum
                            + UINT_32_SIZE) // number of method ids
                requiredCapacity += (lineHeaderSize
                    + profileKey(dexFile.name, apkName, "!").utf8Length
                    + dexFileData.typeIndexes.size * UINT_16_SIZE + hotMethodRegionSize
                    + getMethodBitmapStorageSize(dexFile.header.methodIds.size))
                dexFileToHotMethodRegionSize[dexFile] = hotMethodRegionSize
            }

            // Start serializing the data.
            val dataBos = ByteArrayOutputStream(requiredCapacity)

            // Dex files must be written in the order of their profile index. This
            // avoids writing the index in the output file and simplifies the parsing logic.
            // Write profile line headers.

            // Write dex file line headers.
            for ((dexFile, dexFileData) in profileData) {
                dataBos.writeLineHeader(
                    dexFile,
                    apkName,
                    dexFileData,
                    dexFileToHotMethodRegionSize[dexFile]!!
                )
                dataBos.writeLineData(
                    dexFile,
                    dexFileData,
                )
            }

            check(dataBos.size() == requiredCapacity) {
                ("The bytes saved do not match expectation. actual="
                        + dataBos.size() + " expected=" + requiredCapacity)
            }
            return dataBos.toByteArray()
        }

        override fun read(src: InputStream): Map<DexFile, DexFileData> = with(src) {
            val numberOfDexFiles = readUInt8()
            val uncompressedDataSize = readUInt32()
            val compressedDataSize = readUInt32()
            val uncompressedData = readCompressed(
                    compressedDataSize.toInt(),
                    uncompressedDataSize.toInt()
            )
            if (read() > 0) error("Content found after the end of file")

            val dataStream = uncompressedData.inputStream()

            dataStream.readUncompressedBody(numberOfDexFiles)
        }

        private fun InputStream.readUncompressedBody(numberOfDexFiles: Int): Map<DexFile, DexFileData> {
            // If the uncompressed profile data stream is empty then we have nothing more to do.
            if (available() == 0) {
                return emptyMap()
            }
            // Read the dex file line headers.
            val dexFileData = Array(numberOfDexFiles) {
                val profileKeySize = readUInt16()
                val typeIdSetSize = readUInt16()
                val hotMethodRegionSize = readUInt32()
                val dexChecksum = readUInt32()
                val numMethodIds = readUInt32()
                val profileKey = readString(profileKeySize)
                val dexFile = DexFile(
                    header = DexHeader(
                        stringIds = Span.Empty,
                        typeIds = Span.Empty,
                        prototypeIds = Span.Empty,
                        methodIds = Span(numMethodIds.toInt(), 0),
                        classDefs = Span.Empty,
                        data = Span.Empty,
                    ),
                    dexChecksum = dexChecksum,
                    name = profileKey,
                )
                val data = MutableDexFileData(
                    classIdSetSize = 0,
                    typeIdSetSize = typeIdSetSize,
                    hotMethodRegionSize = hotMethodRegionSize.toInt(),
                    numMethodIds = numMethodIds.toInt(),
                    dexFile = dexFile,
                    classIdSet = mutableSetOf(),
                    typeIdSet = mutableSetOf(),
                    methods = mutableMapOf(),
                )
                // Load the hot methods with inline caches.
                // Keep a copy of the stream hot methods. We need it in case this is a merge so we
                // do not increase the aggregation counter again when we parse the method bitmap.
                // If this is a non merge operation the set will be empty.
                readHotMethodRegion(data)

                // Load the classes.
                readIntsAsDeltas(data.typeIdSetSize, data.typeIdSet)

                // Load the method bitmap (startup & post startup).
                readMethodBitmap(data)
                data
            }
            return dexFileData.map {
                it.dexFile to it.asDexFileData()
            }.toMap()
        }

    },
    /**
     * 0.0.5 Serialization format (O/Android 8.0.0):
     * =============================================
     *
     *    magic,version,number_of_dex_files
     *    dex_location1,number_of_classes1,methods_region_size,dex_location_checksum1, \
     *        method_encoding_11,method_encoding_12...,type_id1,type_id2...
     *    dex_location2,number_of_classes2,methods_region_size,dex_location_checksum2, \
     *        method_encoding_21,method_encoding_22...,,type_id1,type_id2...
     *    .....
     * The method_encoding is:
     *    method_id,number_of_inline_caches,inline_cache1,inline_cache2...
     * The inline_cache is:
     *    dex_pc,[M|dex_map_size], dex_profile_index,class_id1,class_id2...,dex_profile_index2,...
     *    dex_map_size is the number of dex_indices that follows.
     *       Classes are grouped per their dex files and the line
     *       `dex_profile_index,class_id1,class_id2...,dex_profile_index2,...` encodes the
     *       mapping from `dex_profile_index` to the set of classes `class_id1,class_id2...`
     *    M stands for megamorphic or missing types and it's encoded as either
     *    the byte kIsMegamorphicEncoding or kIsMissingTypesEncoding.
     *    When present, there will be no class ids following.
     **/
    V0_0_5_O(byteArrayOf('0', '0', '5', '\u0000')) {
        override fun write(
                os: OutputStream,
                profileData: Map<DexFile, DexFileData>,
                apkName: String
        ) = with(os) {
            writeUInt8(profileData.size) // number of dex files
            for ((dex, data) in profileData.toSortedMap(DexFile)) {
                val profileKey = profileKey(dex.name, apkName, ":")
                val hotMethodRegionSize = data.methods.size * (
                        UINT_16_SIZE + // method id
                        UINT_16_SIZE ) // inline cache size (should always be 0 for us
                writeUInt16(profileKey.utf8Length)
                writeUInt16(data.typeIndexes.size)
                writeUInt32(hotMethodRegionSize.toLong())
                writeUInt32(dex.dexChecksum)
                writeString(profileKey)
                for ((id, _) in data.methods.toSortedMap()) {
                    writeUInt16(id)
                    writeUInt16(0)
                }

                for (id in data.typeIndexes.sorted()) {
                    writeUInt16(id)
                }
            }
        }

        override fun read(src: InputStream): Map<DexFile, DexFileData> = with(src) {
            val numberOfDexFiles = readUInt8()
            // If the uncompressed profile data stream is empty then we have nothing more to do.
            if (available() == 0) {
                return emptyMap()
            }
            // Read the dex file line headers.
            val dexFileData = Array(numberOfDexFiles) {
                // dex_location1,number_of_classes1,methods_region_size,dex_location_checksum1,
                val profileKeySize = readUInt16()
                val typeIdSetSize = readUInt16()
                val hotMethodRegionSize = readUInt32()
                val dexChecksum = readUInt32()
                val profileKey = readString(profileKeySize)
                val dexFile = DexFile(
                    header = DexHeader.Empty,
                    dexChecksum = dexChecksum,
                    name = profileKey,
                )
                val data = MutableDexFileData(
                    classIdSetSize = 0,
                    typeIdSetSize = typeIdSetSize,
                    hotMethodRegionSize = hotMethodRegionSize.toInt(),
                    numMethodIds = 0,
                    dexFile = dexFile,
                    classIdSet = mutableSetOf(),
                    typeIdSet = mutableSetOf(),
                    methods = mutableMapOf(),
                )
                println("DexFile: $profileKey, $hotMethodRegionSize, $dexChecksum, $typeIdSetSize")
                readHotMethods(data)
                readClassesForO(data)
                data
            }

            // Load data for each discovered dex file.
            dexFileData.map {
                it.dexFile to it.asDexFileData()
            }.toMap()
        }
        private fun InputStream.readHotMethods(data: MutableDexFileData) {
            val expectedBytesAvailableAfterRead = available() - data.hotMethodRegionSize
            println("readHotMethods: $expectedBytesAvailableAfterRead")

            var i = 0

            // Read one method at a time until we reach the end of the method region.
            while (available() > expectedBytesAvailableAfterRead) {
                val methodIndex = readUInt16()
                if (i++ % 100 == 0) {
                    println("methodIndex: $methodIndex")
                }
                val methodData = data.methods.computeIfAbsent(methodIndex) { MethodData(0) }
                methodData.flags = methodData.flags or MethodFlags.HOT

                // Read the inline caches.
                var inlineCacheSize = readUInt16()
                while (inlineCacheSize > 0) {
                    skipInlineCache()
                    --inlineCacheSize
                }
            }

            // Check that we read exactly the amount of bytes specified by the method region size.
            if (available() != expectedBytesAvailableAfterRead) error("Read too much data during profile line parse")
        }
        private fun InputStream.readClassesForO(data: MutableDexFileData) {
            var i = 0
            println("readClassesForO")
            for (k in 0 until data.typeIdSetSize) {
                val classDexIndex = readUInt16()
                if (i++ % 100 == 0) {
                    println("classId: $classDexIndex")
                }
                data.typeIdSet.add(classDexIndex)
            }
        }
    },
    /**
     * 0.0.1 Serialization format (N/Android 7.0.0):
     * =============================================
     *
     *    magic,version,number_of_lines
     *    dex_location1,number_of_methods1,number_of_classes1,dex_location_checksum1, \
     *        method_id11,method_id12...,class_id1,class_id2...
     *    dex_location2,number_of_methods2,number_of_classes2,dex_location_checksum2, \
     *        method_id21,method_id22...,,class_id1,class_id2...
     *    .....
     **/
    V0_0_1_N(byteArrayOf('0', '0', '1', '\u0000')) {
        override fun write(
                os: OutputStream,
                profileData: Map<DexFile, DexFileData>,
                apkName: String
        ) = with(os) {
            writeUInt16(profileData.size) // one line is one dex
            for ((dex, data) in profileData.toSortedMap(DexFile)) {
                val profileKey = profileKey(dex.name, apkName, ":")
                writeUInt16(profileKey.utf8Length)
                writeUInt16(data.methods.size)
                writeUInt16(data.classIndexes.size)
                writeUInt32(dex.dexChecksum)
                writeString(profileKey)

                if (data.classIndexes.isEmpty() && data.typeIndexes.isNotEmpty()) {
                    error(
                            "Attempting to write a 0.0.1 profile with type ids and no class ids." +
                                    " This is likely an error."
                    )
                }

                for (id in data.methods.keys.sorted()) {
                    writeUInt16(id)
                }

                for (id in data.classIndexes.sorted()) {
                    writeUInt16(id)
                }
            }
        }

        override fun read(src: InputStream): Map<DexFile, DexFileData> = with(src) {
            val numberOfDexFiles = readUInt16()
            // If the uncompressed profile data stream is empty then we have nothing more to do.
            if (available() == 0) {
                return emptyMap()
            }
            // Read the dex file line headers.
            val dexFileData = Array(numberOfDexFiles) {
                val profileKeySize = readUInt16()
                val numMethodIds = readUInt16()
                val classSetSize = readUInt16()
                val dexChecksum = readUInt32()
                val profileKey = readString(profileKeySize)
                val dexFile = DexFile(
                    header = DexHeader.Empty,
                    dexChecksum = dexChecksum,
                    name = profileKey,
                )
                val data = MutableDexFileData(
                    classIdSetSize = classSetSize,
                    typeIdSetSize = 0,
                    hotMethodRegionSize = 0,
                    numMethodIds = numMethodIds,
                    dexFile = dexFile,
                    classIdSet = mutableSetOf(),
                    typeIdSet = mutableSetOf(),
                    methods = mutableMapOf(),
                )
                for (i in 0 until data.numMethodIds) {
                    val methodId = readUInt16()
                    val methodData = data.methods.computeIfAbsent(methodId) { MethodData(0) }
                    methodData.flags = methodData.flags or MethodFlags.HOT
                }

                for (i in 0 until data.classIdSetSize) {
                    val classId = readUInt16()
                    data.classIdSet.add(classId)
                }
                data
            }
            return dexFileData.map {
                it.dexFile to it.asDexFileData()
            }.toMap()
        }
    };

    internal abstract fun write(os: OutputStream, profileData: Map<DexFile, DexFileData>, apkName: String)
    internal abstract fun read(src: InputStream): Map<DexFile, DexFileData>

    /**
     * Skips the data for an single method's inline cache, since we do not need incline cache data for profgen.
     * This method is valid to use for P/O but not N
     */
    internal fun InputStream.skipInlineCache() {
        /* val dexPc = */readUInt16()
        var dexPcMapSize = readUInt8()

        // Check for missing type encoding.
        if (dexPcMapSize == INLINE_CACHE_MISSING_TYPES_ENCODING) {
            return
        }
        // Check for megamorphic encoding.
        if (dexPcMapSize == INLINE_CACHE_MEGAMORPHIC_ENCODING) {
            return
        }

        // The inline cache is not missing types and it's not megamorphic. Read the types available
        // for each dex pc.
        while (dexPcMapSize > 0) {
            /* val profileIndex = */readUInt8()
            var numClasses = readUInt8()
            while (numClasses > 0) {
                /* val classDexIndex = */readUInt16()
                --numClasses
            }
            --dexPcMapSize
        }
    }

    /**
     * Writes the dex data header for the given dex file into the output stream.
     *
     * @param dexFile the dex file to which the data belongs
     * @param dexData the dex data to which the header belongs
     * @param hotMethodRegionSize the size (in bytes) for the method region that will be serialized as
     * part of the dex data
     */
    internal fun OutputStream.writeLineHeader(
            dexFile: DexFile,
            apkName: String,
            dexData: DexFileData,
            hotMethodRegionSize: Int,
    ) {
        val profileKey = profileKey(dexFile.name, apkName, "!")
        writeUInt16(profileKey.utf8Length)
        writeUInt16(dexData.typeIndexes.size)
        writeUInt32(hotMethodRegionSize.toLong())
        writeUInt32(dexFile.dexChecksum)
        writeUInt32(dexFile.header.methodIds.size.toLong())
        writeString(profileKey)
    }

    /**
     * Writes the given dex file data into the stream.
     *
     * Note that we allow dex files without any methods or classes, so that
     * inline caches can refer to valid dex files.
     *
     * @param dexFile the dex file to which the data belongs
     * @param dexFileData the dex data that should be serialized
     */
    internal fun OutputStream.writeLineData(
            dexFile: DexFile,
            dexFileData: DexFileData,
    ) {
        writeMethodsWithInlineCaches(dexFileData)
        writeIntsAsDeltas(dexFileData.typeIndexes)
        writeMethodBitmap(dexFile, dexFileData)
    }

    /**
     * Writes the methods with inline caches to the output stream.
     *
     * @param dexFileData the dex data containing the methods that should be serialized
     */
    private fun OutputStream.writeMethodsWithInlineCaches(
            dexFileData: DexFileData
    ) {
        // The profile stores the first method index, then the remainder are relative
        // to the previous value.
        var lastMethodIndex = 0
        val sortedMethods = dexFileData.methods.toSortedMap()
        for ((methodIndex, methodData) in sortedMethods) {
            if (!methodData.isHot) {
                continue
            }
            val diffWithTheLastMethodIndex = methodIndex - lastMethodIndex
            writeUInt16(diffWithTheLastMethodIndex)
            writeUInt16(0) // no inline cache data
            lastMethodIndex = methodIndex
        }
    }

    /**
     * Writes the dex file classes to the output stream.
     *
     * @param ids the dex data containing the classes that should be serialized
     */
    internal fun OutputStream.writeIntsAsDeltas(ids: Set<Int>) {
        // The profile stores the first class index, then the remainder are relative
        // to the previous value.
        var lastClassIndex = 0
        // class ids must be sorted ascending so that each id is greater than the last since we
        // are writing unsigned ints and cannot represent negative values
        for (classIndex in ids.sorted()) {
            val diffWithTheLastClassIndex = classIndex - lastClassIndex
            writeUInt16(diffWithTheLastClassIndex)
            lastClassIndex = classIndex
        }
    }

    /**
     * Writes the methods flags as a bitmap to the output stream.
     *
     * @param dexFile the dex file to which the data belongs
     * @param dexFileData the dex data that should be serialized
     */
    private fun OutputStream.writeMethodBitmap(
            dexFile: DexFile,
            dexFileData: DexFileData,
    ) {
        val lastFlag = MethodFlags.LAST_FLAG_REGULAR
        val bitmap = ByteArray(getMethodBitmapStorageSize(dexFile.header.methodIds.size))
        for ((methodIndex, methodData) in dexFileData.methods) {
            var flag = MethodFlags.FIRST_FLAG
            while (flag <= lastFlag) {
                if (flag == MethodFlags.HOT) {
                    flag = flag shl 1
                    continue
                }
                if (methodData.isFlagSet(flag)) {
                    setMethodBitmapBit(bitmap, flag, methodIndex, dexFile)
                }
                flag = flag shl 1
            }
        }
        write(bitmap)
    }

    /**
     * Returns the size necessary to encode the region of methods with inline caches.
     */
    internal fun getHotMethodRegionSize(dexFileData: DexFileData): Int {
        var size = 0
        for (method in dexFileData.methods.values) {
            if (!method.isHot) continue
            size += 2 * UINT_16_SIZE // method index + inline cache size;
        }
        return size
    }

    /**
     * Returns the size needed for the method bitmap storage of the given dex file.
     */
    internal fun getMethodBitmapStorageSize(numMethodIds: Int): Int {
        val methodBitmapBits = numMethodIds * 2 /* 2 bits per method */
        return roundUpUsingAPowerOf2(methodBitmapBits, java.lang.Byte.SIZE) / java.lang.Byte.SIZE
    }

    /**
     * Sets the bit corresponding to the {@param isStartup} flag in the method bitmap.
     *
     * @param bitmap the method bitmap
     * @param flag whether or not this is the startup bit
     * @param methodIndex the method index in the dex file
     * @param dexFile the method dex file
     */
    private fun setMethodBitmapBit(
            bitmap: ByteArray,
            flag: Int,
            methodIndex: Int,
            dexFile: DexFile,
    ) {
        val bitIndex = methodFlagBitmapIndex(flag, methodIndex, dexFile.header.methodIds.size)
        val bitmapIndex = bitIndex / Byte.SIZE_BITS
        val value = bitmap[bitmapIndex] or (1 shl (bitIndex % Byte.SIZE_BITS)).toByte()
        bitmap[bitmapIndex] = value
    }

    /** Returns the absolute index in the flags bitmap of a method.  */
    private fun methodFlagBitmapIndex(flag: Int, methodIndex: Int, numMethodIds: Int): Int {
        // The format is [startup bitmap][post startup bitmap][AmStartup][...]
        // This compresses better than ([startup bit][post startup bit])*
        return methodIndex + flagBitmapIndex(flag) * numMethodIds
    }

    /** Returns the position on which the flag is encoded in the bitmap.  */
    private fun flagBitmapIndex(methodFlag: Int): Int {
        require(isValidMethodFlag(methodFlag))
        require(methodFlag != MethodFlags.HOT)
        // We arrange the method flags in order, starting with the startup flag.
        // The kFlagHot is not encoded in the bitmap and thus not expected as an
        // argument here. Since all the other flags start at 1 we have to subtract
        // one from the power of 2.
        return whichPowerOf2(methodFlag) - 1
    }

    /** Returns true iff the methodFlag is valid and encodes a single value flag  */
    private fun isValidMethodFlag(methodFlag: Int): Boolean {
        return (isPowerOfTwo(methodFlag)
                && methodFlag >= MethodFlags.FIRST_FLAG && methodFlag <= MethodFlags.LAST_FLAG_REGULAR)
    }

    private fun roundUpUsingAPowerOf2(value: Int, powerOfTwo: Int): Int {
        return value + powerOfTwo - 1 and -powerOfTwo
    }

    private fun isPowerOfTwo(x: Int): Boolean {
        return x > 0 && x and x - 1 == 0
    }

    /**
     * Returns ln2(x) assuming tha x is a power of 2.
     */
    private fun whichPowerOf2(x: Int): Int {
        require(isPowerOfTwo(x))
        return Integer.numberOfTrailingZeros(x)
    }

    internal fun InputStream.readHotMethodRegion(data: MutableDexFileData) {
        val expectedBytesAvailableAfterRead = available() - data.hotMethodRegionSize
        var lastMethodIndex = 0

        // Read one method at a time until we reach the end of the method region.
        while (available() > expectedBytesAvailableAfterRead) {
            // The profile stores the first method index, then the remainder are relative to the previous
            // value.
            val diffWithLastMethodDexIndex = readUInt16()
            val methodDexIndex = lastMethodIndex + diffWithLastMethodDexIndex

            val methodData = data.methods.computeIfAbsent(methodDexIndex) { MethodData(0) }
            methodData.flags = methodData.flags or MethodFlags.HOT

            // Read the inline caches.
            var inlineCacheSize = readUInt16()
            while (inlineCacheSize > 0) {
                skipInlineCache()
                --inlineCacheSize
            }
            // Update the last method index.
            lastMethodIndex = methodDexIndex
        }

        // Check that we read exactly the amount of bytes specified by the method region size.
        if (available() != expectedBytesAvailableAfterRead) error("Read too much data during profile line parse")
    }

    internal fun InputStream.readIntsAsDeltas(size: Int, set: MutableSet<Int>) {
        var lastClassIndex = 0
        for (k in 0 until size) {
            val diffWithTheLastClassIndex = readUInt16()
            val classDexIndex = lastClassIndex + diffWithTheLastClassIndex
            if (classDexIndex >= MAX_NUM_CLASS_IDS) {
                error("Value exceeded max class index. Value=$classDexIndex, Max=$MAX_NUM_CLASS_IDS")
            }
            set.add(classDexIndex)
            lastClassIndex = classDexIndex
        }
    }

    internal fun InputStream.readMethodBitmap(data: MutableDexFileData) {
        val methodBitmapStorageSize = getMethodBitmapStorageSize(data.numMethodIds)
        val methodBitmap = read(methodBitmapStorageSize)
        val bs = BitSet.valueOf(methodBitmap)
        for (methodIndex in 0 until data.numMethodIds) {
            val newFlags = bs.readFlagsFromBitmap(methodIndex, data.numMethodIds)
            if (newFlags != 0) {
                val methodData = data.methods.computeIfAbsent(methodIndex) { MethodData(0) }
                methodData.flags = methodData.flags or newFlags
            }
        }
    }

    /** Reads all the method flags for a given method from a bit set. This is only relevant for P. */
    private fun BitSet.readFlagsFromBitmap(
            methodIndex: Int,
            numMethodIds: Int,
    ): Int {
        var result = 0
        val lastFlag = MethodFlags.LAST_FLAG_REGULAR
        var flag = MethodFlags.FIRST_FLAG
        while (flag <= lastFlag) {
            if (flag == MethodFlags.HOT) {
                flag = flag shl 1
                continue
            }
            val bitmapIndex = methodFlagBitmapIndex(flag, methodIndex, numMethodIds)
            if (this[bitmapIndex]) {
                result = result or flag
            }
            flag = flag shl 1
        }
        return result
    }

    companion object {
        internal const val size = 4
    }
}
