/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks.mlkit.codegen

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName

/** Stores class names used by code generator. */
object ClassNames {
    // Basic types
    @JvmField
    val STRING: ClassName =
        ClassName.get("java.lang", "String")

    @JvmField
    val LIST: ClassName =
        ClassName.get("java.util", "List")

    @JvmField
    val CONTEXT: ClassName =
        ClassName.get("android.content", "Context")

    @JvmField
    val IO_EXCEPTION: ClassName =
        ClassName.get("java.io", "IOException")

    @JvmField
    val MAP: ClassName =
        ClassName.get("java.util", "Map")

    @JvmField
    val INTEGER: ClassName =
        ClassName.get("java.lang", "Integer")

    @JvmField
    val OBJECT: ClassName =
        ClassName.get("java.lang", "Object")

    @JvmField
    val HASH_MAP: ClassName =
        ClassName.get("java.util", "HashMap")

    @JvmField
    val ZIP_FILE: ClassName =
        ClassName.get("org.apache.commons.compress.archivers.zip", "ZipFile")

    @JvmField
    val IO_UTILS: ClassName =
        ClassName.get("org.apache.commons.compress.utils", "IOUtils")

    @JvmField
    val SEEKABLE_IN_MEMORY_BYTE_CHANNEL: ClassName =
        ClassName.get(
            "org.apache.commons.compress.utils",
            "SeekableInMemoryByteChannel"
        )

    @JvmField
    val LIST_OF_STRING: ParameterizedTypeName = ParameterizedTypeName.get(
        LIST,
        STRING
    )

    @JvmField
    val NON_NULL: ClassName =
        ClassName.get("androidx.annotation", "NonNull")

    // ML model related types
    @JvmField
    val DATA_TYPE: ClassName =
        ClassName.get("org.tensorflow.lite", "DataType")

    @JvmField
    val FILE_UTIL: ClassName =
        ClassName.get("org.tensorflow.lite.support.common", "FileUtil")

    @JvmField
    val TENSOR_PROCESSOR: ClassName =
        ClassName.get("org.tensorflow.lite.support.common", "TensorProcessor")

    @JvmField
    val CAST_OP: ClassName =
        ClassName.get("org.tensorflow.lite.support.common.ops", "CastOp")

    @JvmField
    val DEQUANTIZE_OP: ClassName = ClassName.get(
        "org.tensorflow.lite.support.common.ops",
        "DequantizeOp"
    )

    @JvmField
    val NORMALIZE_OP: ClassName =
        ClassName.get("org.tensorflow.lite.support.common.ops", "NormalizeOp")

    @JvmField
    val QUANTIZE_OP: ClassName =
        ClassName.get("org.tensorflow.lite.support.common.ops", "QuantizeOp")

    @JvmField
    val IMAGE_PROCESSOR: ClassName =
        ClassName.get("org.tensorflow.lite.support.image", "ImageProcessor")

    @JvmField
    val TENSOR_IMAGE: ClassName =
        ClassName.get("org.tensorflow.lite.support.image", "TensorImage")

    @JvmField
    val RESIZE_OP: ClassName =
        ClassName.get("org.tensorflow.lite.support.image.ops", "ResizeOp")

    @JvmField
    val RESIZE_METHOD: ClassName = ClassName.get(
        "org.tensorflow.lite.support.image.ops.ResizeOp",
        "ResizeMethod"
    )

    @JvmField
    val TENSOR_LABEL: ClassName =
        ClassName.get("org.tensorflow.lite.support.label", "TensorLabel")

    @JvmField
    val MODEL: ClassName =
        ClassName.get("org.tensorflow.lite.support.model", "Model")

    @JvmField
    val METADATA_EXTRACTOR: ClassName =
        ClassName.get("org.tensorflow.lite.support.metadata", "MetadataExtractor")

    @JvmField
    val TENSOR_BUFFER: ClassName = ClassName.get(
        "org.tensorflow.lite.support.tensorbuffer",
        "TensorBuffer"
    )

    @JvmField
    val MODEL_OPTIONS: ClassName = MODEL.nestedClass("Options")

    @JvmField
    val CATEGORY: ClassName = ClassName.get("org.tensorflow.lite.support.label", "Category")

    @JvmField
    val CATEGORY_LIST: TypeName = ParameterizedTypeName.get(LIST, CATEGORY)

    @JvmField
    val ARRAY_LIST: ClassName = ClassName.get("java.util", "ArrayList")

    @JvmField
    val LABEL_UTIL: ClassName = ClassName.get(
        "org.tensorflow.lite.support.label",
        "LabelUtil"
    )

    @JvmField
    val BOUNDING_BOX_UTIL: ClassName = ClassName.get(
        "org.tensorflow.lite.support.image",
        "BoundingBoxUtil"
    )

    @JvmField
    val RECT_F: ClassName = ClassName.get(
        "android.graphics",
        "RectF"
    )

    @JvmField
    val STRING_LIST: TypeName = ParameterizedTypeName.get(LIST, STRING)

    @JvmField
    val RECTF_LIST: TypeName = ParameterizedTypeName.get(LIST, RECT_F)
}