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

import static com.android.tools.mlkit.DataInputOutputUtils.readFloatArray;
import static com.android.tools.mlkit.DataInputOutputUtils.writeFloatArray;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Strings;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Objects;
import org.tensorflow.lite.schema.Metadata;
import org.tensorflow.lite.schema.Model;
import org.tensorflow.lite.schema.QuantizationParameters;
import org.tensorflow.lite.schema.SubGraph;
import org.tensorflow.lite.schema.Tensor;
import org.tensorflow.lite.schema.TensorType;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;

/** Class to load metadata from TFLite FlatBuffer. */
// TODO(b/150458079): This file is forked from metadata library because right now we only have
// Android library(not java one).
// Remove this file once we have official library.
public class MetadataExtractor {
    /** Model that is loaded from TFLite FlatBuffer. */
    private final Model bufferModel;

    /**
     * Creates a {@link MetadataExtractor} with TFLite FlatBuffer {@code buffer}.
     *
     * @throws IllegalArgumentException if the model contains no or more than one subgraph.
     */
    public MetadataExtractor(ByteBuffer buffer) {
        this.bufferModel = Model.getRootAsModel(buffer);
        if (bufferModel.subgraphsLength() != 1) {
            throw new IllegalArgumentException("Only model with one subgraph is supported.");
        }
    }

    /** Gets the count of input tensors. */
    public int getInputTensorCount() {
        return bufferModel.subgraphs(0).inputsLength();
    }

    /** Gets the count of output tensors. */
    public int getOutputTensorCount() {
        return bufferModel.subgraphs(0).outputsLength();
    }

    /** Gets shape of the input tensor with {@code inputIndex}. */
    public int[] getInputTensorShape(int inputIndex) {
        Tensor tensor = getInputTensor(inputIndex);
        return getShape(tensor);
    }

    /** Gets {@link TensorType} of the input tensor with and {@code inputIndex}. */
    public byte getInputTensorType(int inputIndex) {
        Tensor tensor = getInputTensor(inputIndex);
        return tensor.type();
    }

    /** Gets shape of the output tensor with {@code outputIndex}. */
    public int[] getOutputTensorShape(int outputIndex) {
        Tensor tensor = getOutputTensor(outputIndex);
        return getShape(tensor);
    }

    /** Gets {@link TensorType} of the output tensor with {@code outputIndex}. */
    public byte getOutputTensorType(int outputIndex) {
        Tensor tensor = getOutputTensor(outputIndex);
        return tensor.type();
    }

    /**
     * Gets minimum parser version from model metadata. It means in order to support this model,
     * parser need to be no lower than the targeted version.
     */
    public String getMinParserVersion() {
        ModelMetadata modelMetadata = getModelMetaData();
        if (modelMetadata == null) {
            return "";
        } else {
            return Strings.nullToEmpty(modelMetadata.minParserVersion());
        }
    }

    /** Gets the input tensor with {@code inputIndex}. */
    public Tensor getInputTensor(int inputIndex) {
        return getTensor(inputIndex, true);
    }

    /** Gets the output tensor with {@code outputIndex}. */
    public Tensor getOutputTensor(int outputIndex) {
        return getTensor(outputIndex, false);
    }

    /**
     * Gets the input/output tensor with {@code tensorIndex}.
     *
     * @param isInput indicates the tensor is input or output.
     * @throws IllegalArgumentException if {@code tensorIndex} is out of bounds.
     */
    private Tensor getTensor(int tensorIndex, boolean isInput) {
        SubGraph subgraph = bufferModel.subgraphs(0);
        if (isInput) {
            return subgraph.tensors(subgraph.inputs(tensorIndex));
        } else {
            return subgraph.tensors(subgraph.outputs(tensorIndex));
        }
    }

    /** Gets the shape of a tensor. */
    private static int[] getShape(Tensor tensor) {
        int shapeDim = tensor.shapeLength();
        int[] tensorShape = new int[shapeDim];
        for (int i = 0; i < shapeDim; i++) {
            tensorShape[i] = tensor.shape(i);
        }
        return tensorShape;
    }

    @Nullable
    public ModelMetadata getModelMetaData() {
        int length = bufferModel.metadataLength();
        for (int i = 0; i < length; i++) {
            Metadata metadata = bufferModel.metadata(i);
            if ("TFLITE_METADATA".equals(metadata.name())) {
                long bufferIndex = metadata.buffer();
                return ModelMetadata.getRootAsModelMetadata(
                        bufferModel.buffers((int) bufferIndex).dataAsByteBuffer());
            }
        }
        return null;
    }

    public static QuantizationParams getQuantizationParams(Tensor tensor) {
        byte tensorType = tensor.type();
        float scale;
        long zeroPoint;
        // Gets the quantization parameters for integer tensors.
        if (tensorType == TensorType.INT32
                || tensorType == TensorType.INT64
                || tensorType == TensorType.UINT8) {
            QuantizationParameters quantization = tensor.quantization();
            // Some integer tensors may not have quantization parameters, meaning they don't need
            // quantization. Then both scale and zeroPoint are returned as 0. Reset scale to 1.0f to
            // bypass quantization.
            scale = quantization.scale(0) == 0.0f ? 1.0f : quantization.scale(0);
            zeroPoint = quantization.zeroPoint(0);
        } else {
            // Non-integer type tensors do not need quantization. Set zeroPoint to 0 and scale to
            // 1.0f to
            // bypass the quantization.
            scale = 1.0f;
            zeroPoint = 0;
        }

        return new QuantizationParams(scale, zeroPoint);
    }

    public static class QuantizationParams {
        /** The scale value used in dequantization. */
        private final float scale;
        /** The zero point value used in dequantization. */
        private final long zeroPoint;

        /**
         * Creates a {@link QuantizationParams} with {@code scale} and {@code zero_point}.
         *
         * @param scale The scale value used in dequantization.
         * @param zeroPoint The zero point value used in dequantization.
         */
        public QuantizationParams(float scale, long zeroPoint) {
            this.scale = scale;
            this.zeroPoint = zeroPoint;
        }

        public QuantizationParams(@NonNull DataInput in) throws IOException {
            this.scale = in.readFloat();
            this.zeroPoint = in.readLong();
        }

        public void save(@NonNull DataOutput out) throws IOException {
            out.writeFloat(scale);
            out.writeLong(zeroPoint);
        }

        /** Returns the scale value. */
        public float getScale() {
            return scale;
        }

        /** Returns the zero point value. */
        public long getZeroPoint() {
            return zeroPoint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QuantizationParams that = (QuantizationParams) o;
            return Float.compare(scale, that.scale) == 0 && zeroPoint == that.zeroPoint;
        }

        @Override
        public int hashCode() {
            return Objects.hash(scale, zeroPoint);
        }
    }

    public static class NormalizationParams {
        private final float[] mean;
        private final float[] std;
        private final float[] min;
        private final float[] max;

        public NormalizationParams(
                @NonNull FloatBuffer meanBuffer,
                @NonNull FloatBuffer stdBuffer,
                @NonNull FloatBuffer minBuffer,
                @NonNull FloatBuffer maxBuffer) {
            mean = new float[meanBuffer.limit()];
            meanBuffer.get(mean);
            std = new float[stdBuffer.limit()];
            stdBuffer.get(std);
            min = new float[minBuffer.limit()];
            minBuffer.get(min);
            max = new float[maxBuffer.limit()];
            maxBuffer.get(max);
        }

        public NormalizationParams(@NonNull DataInput in) throws IOException {
            mean = readFloatArray(in);
            std = readFloatArray(in);
            min = readFloatArray(in);
            max = readFloatArray(in);
        }

        public void save(@NonNull DataOutput out) throws IOException {
            writeFloatArray(out, mean);
            writeFloatArray(out, std);
            writeFloatArray(out, min);
            writeFloatArray(out, max);
        }

        public float[] getMean() {
            return mean;
        }

        public float[] getStd() {
            return std;
        }

        public float[] getMin() {
            return min;
        }

        public float[] getMax() {
            return max;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NormalizationParams that = (NormalizationParams) o;
            return Arrays.equals(mean, that.mean)
                    && Arrays.equals(std, that.std)
                    && Arrays.equals(min, that.min)
                    && Arrays.equals(max, that.max);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(mean);
            result = 31 * result + Arrays.hashCode(std);
            result = 31 * result + Arrays.hashCode(min);
            result = 31 * result + Arrays.hashCode(max);
            return result;
        }
    }

    /**
     * Compares two semantic version numbers.
     *
     * <p>Examples of comparing two versions: <br>
     * {@code 1.9} precedes {@code 1.14}; <br>
     * {@code 1.14} precedes {@code 1.14.1}; <br>
     * {@code 1.14} and {@code 1.14.0} are equal;
     *
     * @return the value {@code 0} if the two versions are equal; a value less than {@code 0} if
     *     {@code version1} precedes {@code version2}; a value greater than {@code 0} if {@code
     *     version2} precedes {@code version1}.
     */
    static int compareVersions(String version1, String version2) {
        String[] levels1 = version1.split("\\.", 0);
        String[] levels2 = version2.split("\\.", 0);

        int length = Math.max(levels1.length, levels2.length);
        for (int i = 0; i < length; i++) {
            Integer v1 = i < levels1.length ? Integer.parseInt(levels1[i]) : 0;
            Integer v2 = i < levels2.length ? Integer.parseInt(levels2[i]) : 0;
            int compare = v1.compareTo(v2);
            if (compare != 0) {
                return compare;
            }
        }

        return 0;
    }
}
