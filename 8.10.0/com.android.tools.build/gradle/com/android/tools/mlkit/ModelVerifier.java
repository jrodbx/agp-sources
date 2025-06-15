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

package com.android.tools.mlkit;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import org.tensorflow.lite.support.metadata.schema.TensorMetadata;

/** Verify whether model is valid to generate code. */
class ModelVerifier {
    private static final String MODEL_UNSUPPORTED_PREFIX = "This model is not supported: ";

    @NonNull
    static MetadataExtractor getExtractorWithVerification(@NonNull ByteBuffer byteBuffer)
            throws TfliteModelException {
        MetadataExtractor extractor;
        try {
            extractor = new MetadataExtractor(byteBuffer);
        } catch (Exception e) {
            throw new TfliteModelException("Not a valid TensorFlow Lite model");
        }

        verifyModel(extractor);
        return extractor;
    }

    @VisibleForTesting
    static void verifyModel(@NonNull MetadataExtractor extractor) throws TfliteModelException {
        ModelMetadata metadata = extractor.hasMetadata() ? extractor.getModelMetadata() : null;

        Set<String> inputNameSet = new HashSet<>();
        for (int i = 0; i < extractor.getInputTensorCount(); i++) {
            verifyDataType(extractor.getInputTensorType(i), i, TensorInfo.Source.INPUT);

            if (metadata != null) {
                TensorMetadata tensorMetadata = metadata.subgraphMetadata(0).inputTensorMetadata(i);
                verifyTensorMetadata(tensorMetadata, i, TensorInfo.Source.INPUT);

                String formattedName = MlNames.computeIdentifierName(tensorMetadata.name());
                if (inputNameSet.contains(formattedName)) {
                    throw new TfliteModelException(
                            String.format(
                                    "%sTensors cannot have the same name (%s).",
                                    MODEL_UNSUPPORTED_PREFIX, formattedName));
                }
                inputNameSet.add(formattedName);

                if (TensorInfo.extractContentType(tensorMetadata) == TensorInfo.ContentType.IMAGE
                        && extractor.getInputTensorShape(i).length != 4) {
                    throw new TfliteModelException(
                            MODEL_UNSUPPORTED_PREFIX + "Image tensor shape must have length 4.");
                }
            }
        }

        Set<String> outputNameSet = new HashSet<>();
        for (int i = 0; i < extractor.getOutputTensorCount(); i++) {
            verifyDataType(extractor.getOutputTensorType(i), i, TensorInfo.Source.OUTPUT);

            if (metadata != null) {
                TensorMetadata tensorMetadata =
                        metadata.subgraphMetadata(0).outputTensorMetadata(i);
                verifyTensorMetadata(tensorMetadata, i, TensorInfo.Source.OUTPUT);

                String formattedName = MlNames.computeIdentifierName(tensorMetadata.name());
                if (outputNameSet.contains(formattedName)) {
                    throw new TfliteModelException(
                            String.format(
                                    "%sTensors cannot have the same name (%s).",
                                    MODEL_UNSUPPORTED_PREFIX, formattedName));
                }
                outputNameSet.add(formattedName);
            }
        }
    }

    private static void verifyTensorMetadata(
            @Nullable TensorMetadata tensorMetadata, int index, @NonNull TensorInfo.Source source)
            throws TfliteModelException {
        if (tensorMetadata == null) {
            throw new TfliteModelException(
                    MODEL_UNSUPPORTED_PREFIX
                            + String.format(
                                    "%s tensor %d does not have metadata.",
                                    source == TensorInfo.Source.INPUT ? "Input" : "Output", index));
        }
        if (tensorMetadata.name() == null) {
            throw new TfliteModelException(
                    String.format(
                            MODEL_UNSUPPORTED_PREFIX + "%s tensor %d does not have a name.",
                            source == TensorInfo.Source.INPUT ? "Input" : "Output",
                            index));
        }
    }

    @VisibleForTesting
    static void verifyDataType(byte dataType, int index, @NonNull TensorInfo.Source source)
            throws TfliteModelException {
        if (TensorInfo.DataType.fromByte(dataType) == TensorInfo.DataType.UNKNOWN) {
            throw new TfliteModelException(
                    MODEL_UNSUPPORTED_PREFIX
                            + String.format(
                                    "Data type of %s tensor %d is not supported.",
                                    source == TensorInfo.Source.INPUT ? "input" : "output", index));
        }
    }
}
