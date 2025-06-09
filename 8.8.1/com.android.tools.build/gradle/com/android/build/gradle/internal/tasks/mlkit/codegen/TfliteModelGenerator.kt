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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.CodeBlockInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor.DefaultProcessInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getAssociatedFileInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getFieldInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getInputProcessorInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getOutputProcessorInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.getProcessInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass.GroupClassInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass.OutputsClassInjector
import com.android.tools.mlkit.MlNames
import com.android.tools.mlkit.ModelInfo
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.ArrayList
import javax.lang.model.element.Modifier

/** Generator to generate code for tflite model. */
class TfliteModelGenerator(
    private val modelFile: File,
    private val packageName: String,
    private val localModelPath: String
) : ModelGenerator {
    private val logger: Logger = Logging.getLogger(this.javaClass)
    private val modelInfo: ModelInfo = ModelInfo.buildFrom(ByteBuffer.wrap(modelFile.readBytes()))
    private val className: String = MlNames.computeModelClassName(localModelPath)
    private val androidLogger: LoggerWrapper = LoggerWrapper(logger)

    override fun generateBuildClass(outputDirProperty: DirectoryProperty) {
        val classBuilder = TypeSpec.classBuilder(className).addModifiers(
            Modifier.PUBLIC,
            Modifier.FINAL
        )

        if (!modelInfo.isMinParserVersionSatisfied) {
            androidLogger.warning(
                "Model is not fully supported in current Android Gradle Plugin" +
                        " and will use fallback APIs, so please update to the latest version: ${modelFile.absolutePath}"
            )
        }

        if (modelInfo.isMetadataExisted) {
            classBuilder.addJavadoc(modelInfo.modelDescription)
        } else {
            classBuilder.addJavadoc(
                "This model doesn't have metadata, so no javadoc can be generated."
            )
        }
        buildFields(classBuilder)
        buildConstructor(classBuilder)
        buildStaticNewInstanceMethods(classBuilder)
        buildProcessMethod(classBuilder)
        buildCloseMethod(classBuilder)
        // RGB image is the only advanced input, so we can check it like this.
        if( modelInfo.inputs.any { it.isRGBImage }) {
            buildProcessMethod(classBuilder, isGeneric = true)
        }
        buildInnerClass(classBuilder)

        // Final steps.
        try {
            JavaFile.builder(packageName, classBuilder.build()).build()
                .writeTo(outputDirProperty.asFile.get())
        } catch (e: IOException) {
            logger.debug("Failed to write mlkit generated java file")
        }
    }

    private fun buildFields(classBuilder: TypeSpec.Builder) {
        for (tensorInfo in modelInfo.inputs) {
            getFieldInjector().inject(classBuilder, tensorInfo)
        }
        for (tensorInfo in modelInfo.outputs) {
            getFieldInjector().inject(classBuilder, tensorInfo)
        }
        val model = FieldSpec.builder(ClassNames.MODEL, FIELD_MODEL)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .addAnnotation(ClassNames.NON_NULL)
            .build()
        classBuilder.addField(model)
    }

    private fun buildInnerClass(classBuilder: TypeSpec.Builder) {
        OutputsClassInjector(ClassMetadata(packageName, className)).inject(classBuilder, modelInfo)
        if (modelInfo.outputTensorGroups.isNotEmpty()) {
            GroupClassInjector().inject(classBuilder, modelInfo)
        }
    }

    private fun buildConstructor(classBuilder: TypeSpec.Builder) {
        val context = ParameterSpec.builder(ClassNames.CONTEXT, "context")
            .addAnnotation(ClassNames.NON_NULL)
            .build()
        val options = ParameterSpec.builder(ClassNames.MODEL_OPTIONS, "options")
            .addAnnotation(ClassNames.NON_NULL)
            .build()
        val constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(context)
            .addParameter(options)
            .addException(ClassNames.IO_EXCEPTION)
            .addStatement(
                "\$L = \$T.createModel(context, \$S, options)",
                FIELD_MODEL,
                ClassNames.MODEL,
                localModelPath
            )
            .addStatement(
                "\$T extractor = new \$T(model.getData())",
                ClassNames.METADATA_EXTRACTOR,
                ClassNames.METADATA_EXTRACTOR
            )

        // Init preprocessor
        for (tensorInfo in modelInfo.inputs) {
            if (tensorInfo.isMetadataExisted) {
                val preprocessorInjector = getInputProcessorInjector(tensorInfo)
                preprocessorInjector.inject(constructorBuilder, tensorInfo)
            }
        }

        // Init associated file and postprocessor
        for (tensorInfo in modelInfo.outputs) {
            if (tensorInfo.isMetadataExisted) {
                val postprocessorInjector = getOutputProcessorInjector(tensorInfo)
                postprocessorInjector.inject(constructorBuilder, tensorInfo)

                val codeBlockInjector: CodeBlockInjector = getAssociatedFileInjector()
                codeBlockInjector.inject(constructorBuilder, tensorInfo)
            }
        }
        classBuilder.addMethod(constructorBuilder.build())
    }

    private fun buildProcessMethod(classBuilder: TypeSpec.Builder, isGeneric : Boolean = false) {
        val outputType: TypeName = ClassName.get(packageName, className)
            .nestedClass(MlNames.OUTPUTS)
        val localOutputs = "outputs"
        val methodBuilder = MethodSpec.methodBuilder("process")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassNames.NON_NULL)
            .returns(outputType)
        val byteBufferList: MutableList<String> = ArrayList()
        for (tensorInfo in modelInfo.inputs) {
            val processedTypeName = getProcessedTypeName(tensorInfo)
            val parameterType = if(isGeneric) ClassNames.TENSOR_BUFFER else getParameterType(tensorInfo)
            val parameterSpec = ParameterSpec.builder(parameterType, tensorInfo.identifierName)
                .addAnnotation(ClassNames.NON_NULL)
                .build()
            methodBuilder.addParameter(parameterSpec)
            byteBufferList.add("$processedTypeName.getBuffer()")
        }
        for (tensorInfo in modelInfo.inputs) {
            if (isGeneric) {
                DefaultProcessInjector().inject(methodBuilder, tensorInfo)
            } else {
                getProcessInjector(tensorInfo).inject(methodBuilder, tensorInfo)
            }
        }
        methodBuilder.addStatement("\$T \$L = new \$T(model)", outputType, localOutputs, outputType)
        methodBuilder.addStatement(
            "\$L.run(\$L, \$L.getBuffer())",
            FIELD_MODEL,
            getObjectArrayString(byteBufferList.toTypedArray()),
            localOutputs
        )
        methodBuilder.addStatement("return \$L", localOutputs)
        classBuilder.addMethod(methodBuilder.build())
    }

    private fun buildCloseMethod(classBuilder: TypeSpec.Builder) {
        val methodBuilder = MethodSpec.methodBuilder("close")
            .addModifiers(Modifier.PUBLIC)
            .addStatement("\$L.close()", FIELD_MODEL)
        classBuilder.addMethod(methodBuilder.build())
    }

    private fun buildStaticNewInstanceMethods(classBuilder: TypeSpec.Builder) {
        val context = ParameterSpec.builder(ClassNames.CONTEXT, "context")
            .addAnnotation(ClassNames.NON_NULL)
            .build()
        val options = ParameterSpec.builder(ClassNames.MODEL_OPTIONS, "options")
            .addAnnotation(ClassNames.NON_NULL)
            .build()
        val returnType: TypeName = ClassName.get(packageName, className)

        val methodBuilder = MethodSpec.methodBuilder("newInstance")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(context)
            .addException(ClassNames.IO_EXCEPTION)
            .addAnnotation(ClassNames.NON_NULL)
            .returns(returnType)
            .addStatement("return new \$T(context, (new \$T.Builder()).build())", returnType, ClassNames.MODEL_OPTIONS)
        classBuilder.addMethod(methodBuilder.build())

        val methodWithOptionsBuilder = MethodSpec.methodBuilder("newInstance")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(context)
            .addParameter(options)
            .addException(ClassNames.IO_EXCEPTION)
            .addAnnotation(ClassNames.NON_NULL)
            .returns(returnType)
            .addStatement("return new \$T(context, options)", returnType)
        classBuilder.addMethod(methodWithOptionsBuilder.build())
    }

    companion object {
        private const val FIELD_MODEL = "model"
    }
}