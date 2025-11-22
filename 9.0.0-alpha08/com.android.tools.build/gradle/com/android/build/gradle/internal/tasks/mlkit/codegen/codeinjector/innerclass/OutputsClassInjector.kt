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
package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassMetadata
import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.CodeInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getGetterMethodInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.DefaultGetMethodInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.GroupGetMethodInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods.NoMetadataGetMethodInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.getDataType
import com.android.build.gradle.internal.tasks.mlkit.codegen.getParameterType
import com.android.tools.mlkit.MlNames
import com.android.tools.mlkit.ModelInfo
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

/** Injector to inject output class. */
class OutputsClassInjector(private val metadata: ClassMetadata) : CodeInjector<TypeSpec.Builder, ModelInfo> {
    override fun inject(classBuilder: TypeSpec.Builder, modelInfo: ModelInfo) {
        val tensorInfos = modelInfo.outputs
        val builder = TypeSpec.classBuilder(MlNames.OUTPUTS)
        builder.addModifiers(Modifier.PUBLIC)

        // Add necessary fields.
        for (tensorInfo in tensorInfos) {
            val fieldSpec =
                FieldSpec.builder(getParameterType(tensorInfo), tensorInfo.identifierName)
                    .addModifiers(Modifier.PRIVATE)
                    .build()
            builder.addField(fieldSpec)
        }

        // Add constructor.
        val constructorBuilder = MethodSpec.constructorBuilder()
            .addParameter(ClassNames.MODEL, "model")
            .addModifiers(Modifier.PRIVATE)
        for ((index, tensorInfo) in tensorInfos.withIndex()) {
            if (tensorInfo.isRGBImage) {
                constructorBuilder.addStatement(
                    "this.\$L = new \$T(\$T.\$L)",
                    tensorInfo.identifierName,
                    ClassNames.TENSOR_IMAGE,
                    ClassNames.DATA_TYPE,
                    getDataType(tensorInfo.dataType))
                constructorBuilder.addStatement(
                    "\$L.load(TensorBuffer.createFixedSize(model.getOutputTensorShape(\$L), \$T.\$L))",
                    tensorInfo.identifierName,
                    index,
                    ClassNames.DATA_TYPE,
                    getDataType(tensorInfo.dataType))

            } else {
                constructorBuilder.addStatement(
                    "this.\$L = TensorBuffer.createFixedSize(model.getOutputTensorShape(\$L), \$T.\$L)",
                    tensorInfo.identifierName,
                    index,
                    ClassNames.DATA_TYPE,
                    getDataType(tensorInfo.dataType))
            }
        }
        builder.addMethod(constructorBuilder.build())

        // Add getter methods for each param.
        for (tensorInfo in tensorInfos) {
            val methodInjector = getGetterMethodInjector(tensorInfo, modelInfo)
            methodInjector.inject(builder, tensorInfo)
            // Add getter method to return generic TensorBuffer type if missing.
            if (methodInjector !is DefaultGetMethodInjector && methodInjector !is NoMetadataGetMethodInjector) {
                if (tensorInfo.isMetadataExisted) {
                    DefaultGetMethodInjector().inject(builder, tensorInfo)
                } else {
                    NoMetadataGetMethodInjector().inject(builder, tensorInfo)
                }
            }
        }

        // Add group getter methods if necessary.
        if (modelInfo.outputTensorGroups.isNotEmpty()) {
            GroupGetMethodInjector(metadata).inject(builder, modelInfo)
        }

        // Add getBuffer method for inner usage.
        buildGetBufferMethod(builder, tensorInfos)
        classBuilder.addType(builder.build())
    }

    companion object {
        private fun buildGetBufferMethod(
            classBuilder: TypeSpec.Builder, tensorInfos: List<TensorInfo>
        ) {
            val mapType: TypeName = ParameterizedTypeName.get(
                ClassNames.MAP,
                ClassNames.INTEGER,
                ClassNames.OBJECT
            )
            val getterBuilder = MethodSpec.methodBuilder("getBuffer")
                .addModifiers(Modifier.PRIVATE)
                .returns(mapType)
                .addAnnotation(ClassNames.NON_NULL)
                .addStatement(
                    "\$T outputs = new \$T<>()",
                    mapType,
                    ClassNames.HASH_MAP
                )
            for ((index, tensorInfo) in tensorInfos.withIndex()) {
                getterBuilder.addStatement(
                    "outputs.put(\$L, \$L.getBuffer())", index, tensorInfo.identifierName
                )
            }
            getterBuilder.addStatement("return outputs")
            classBuilder.addMethod(getterBuilder.build())
        }
    }
}