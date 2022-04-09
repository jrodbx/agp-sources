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
package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.fields

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.CodeInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.getIdentifierFromFileName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getImageHeightFieldName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getImageWidthFieldName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getProcessorName
import com.android.tools.mlkit.TensorInfo
import com.google.common.base.Strings
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

/** Inject fields based on [TensorInfo]. */
class FieldInjector : CodeInjector<TypeSpec.Builder, TensorInfo> {
    override fun inject(classBuilder: TypeSpec.Builder, tensorInfo: TensorInfo) {
        if (!tensorInfo.isMetadataExisted) {
            return
        }

        if (!Strings.isNullOrEmpty(tensorInfo.fileName)) {
            val fieldName = FieldSpec.builder(
                ClassNames.LIST_OF_STRING,
                getIdentifierFromFileName(tensorInfo.fileName)
            )
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .addAnnotation(ClassNames.NON_NULL)
                .build()
            classBuilder.addField(fieldName)
        }

        // Add preprocessor and postprocessor fields.
        if (tensorInfo.isRGBImage) {
            // Add processor and image fields.
            val processorField = FieldSpec.builder(
                ClassNames.IMAGE_PROCESSOR,
                getProcessorName(tensorInfo)
            )
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .addAnnotation(ClassNames.NON_NULL)
                .build()
            classBuilder.addField(processorField)

            val imageHeightField = FieldSpec.builder(
                TypeName.INT,
                getImageHeightFieldName(tensorInfo)
            )
                .addModifiers(Modifier.PRIVATE)
                .build()
            classBuilder.addField(imageHeightField)

            val imageWidthField = FieldSpec.builder(
                TypeName.INT,
                getImageWidthFieldName(tensorInfo)
            )
                .addModifiers(Modifier.PRIVATE)
                .build()
            classBuilder.addField(imageWidthField)
        } else {
            val fieldName = FieldSpec.builder(
                ClassNames.TENSOR_PROCESSOR,
                getProcessorName(tensorInfo)
            )
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .addAnnotation(ClassNames.NON_NULL)
                .build()
            classBuilder.addField(fieldName)
        }
    }
}