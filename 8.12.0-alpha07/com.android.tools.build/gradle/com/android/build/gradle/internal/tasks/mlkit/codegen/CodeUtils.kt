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

@file:JvmName("CodeUtils")

package com.android.build.gradle.internal.tasks.mlkit.codegen

import com.android.tools.mlkit.MlNames
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.TypeName

fun getParameterType(tensorInfo: TensorInfo): TypeName {
    return if (tensorInfo.isRGBImage) {
        ClassNames.TENSOR_IMAGE
    } else {
        ClassNames.TENSOR_BUFFER
    }
}

fun getGroupClassParameterType(tensorInfo: TensorInfo): TypeName {
    return if (tensorInfo.fileType == TensorInfo.FileType.TENSOR_VALUE_LABELS) {
        ClassNames.STRING
    } else if (tensorInfo.contentType == TensorInfo.ContentType.BOUNDING_BOX) {
        ClassNames.RECT_F
    } else if (tensorInfo.dataType == TensorInfo.DataType.FLOAT32) {
        TypeName.FLOAT
    } else if (tensorInfo.dataType == TensorInfo.DataType.UINT8) {
        TypeName.INT
    } else {
        ClassNames.TENSOR_BUFFER
    }
}

fun getIdentifierFromFileName(name: String): String {
    return MlNames.computeIdentifierName(name.replace("\\..*".toRegex(), ""))
}

fun getProcessorName(tensorInfo: TensorInfo): String {
    return if (tensorInfo.source == TensorInfo.Source.INPUT) {
        tensorInfo.identifierName + "Processor"
    } else {
        tensorInfo.identifierName + "PostProcessor"
    }
}

fun getProcessedTypeName(tensorInfo: TensorInfo): String {
    return "processed" + tensorInfo.identifierName
}

fun getProcessorBuilderName(tensorInfo: TensorInfo): String {
    return getProcessorName(tensorInfo) + "Builder"
}

fun getFloatArrayString(array: FloatArray): String {
    return getArrayString("float", array.map { it.toString() + "f" }.toTypedArray())
}

fun getIntArrayString(array: IntArray): String {
    return getArrayString("int", array.map { it.toString() }.toTypedArray())
}

fun getObjectArrayString(array: Array<String>): String {
    return getArrayString("Object", array)
}

private fun getArrayString(
    type: String,
    array: Array<String>
): String {
    val builder = StringBuilder()
    builder.append(String.format("new %s[] {", type))
    for (dim in array) {
        builder.append(dim).append(",")
    }
    builder.deleteCharAt(builder.length - 1)
    builder.append("}")
    return builder.toString()
}

fun getDataType(type: TensorInfo.DataType): String {
    return type.toString()
}

fun getOutputParameterType(tensorInfo: TensorInfo): TypeName {
    return when {
        tensorInfo.isRGBImage -> ClassNames.TENSOR_IMAGE
        tensorInfo.fileType == TensorInfo.FileType.TENSOR_AXIS_LABELS -> ClassNames.CATEGORY_LIST
        tensorInfo.fileType == TensorInfo.FileType.TENSOR_VALUE_LABELS -> ClassNames.STRING_LIST
        tensorInfo.contentType == TensorInfo.ContentType.BOUNDING_BOX -> ClassNames.RECTF_LIST
        else -> ClassNames.TENSOR_BUFFER
    }
}

fun getOutputParameterTypeName(tensorInfo: TensorInfo): String {
    return when {
        tensorInfo.isRGBImage -> ClassNames.TENSOR_IMAGE.simpleName()
        tensorInfo.fileType == TensorInfo.FileType.TENSOR_AXIS_LABELS -> "CategoryList"
        tensorInfo.fileType == TensorInfo.FileType.TENSOR_VALUE_LABELS -> "StringList"
        tensorInfo.contentType == TensorInfo.ContentType.BOUNDING_BOX -> "RectFList"
        else -> ClassNames.TENSOR_BUFFER.simpleName()
    }
}

fun getImageHeightFieldName(tensorInfo: TensorInfo): String {
    return tensorInfo.identifierName + "Height"
}

fun getImageWidthFieldName(tensorInfo: TensorInfo): String {
    return tensorInfo.identifierName + "Width"
}
