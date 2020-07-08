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

import static com.android.tools.mlkit.DataInputOutputUtils.readTensorInfoList;
import static com.android.tools.mlkit.DataInputOutputUtils.writeTensorInfoList;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;

/** Stores necessary data for one model. */
public class ModelInfo {

    /**
     * Version of current parser. If model metadata's minParserVersion is higher than this, we will
     * use fallback APIs (use TensorBuffer for all APIs).
     */
    public static final String PARSER_VERSION = "1.0.0";

    private final long modelSize;
    private final String modelHash;

    private final boolean metadataExisted;
    private final String modelName;
    private final String modelDescription;
    private final String modelVersion;
    private final String modelAuthor;
    private final String modelLicense;
    private final String minParserVersion;

    private final List<TensorInfo> inputs;
    private final List<TensorInfo> outputs;

    public ModelInfo(
            long modelSize, @NonNull String modelHash, @Nullable ModelMetadata modelMetadata) {
        this.modelSize = modelSize;
        this.modelHash = modelHash;
        if (modelMetadata != null) {
            metadataExisted = true;
            modelName = Strings.nullToEmpty(modelMetadata.name());
            modelDescription = Strings.nullToEmpty(modelMetadata.description());
            modelVersion = Strings.nullToEmpty(modelMetadata.version());
            modelAuthor = Strings.nullToEmpty(modelMetadata.author());
            modelLicense = Strings.nullToEmpty(modelMetadata.license());
            minParserVersion = Strings.nullToEmpty(modelMetadata.minParserVersion());
        } else {
            metadataExisted = false;
            modelName = "";
            modelDescription = "";
            modelVersion = "";
            modelAuthor = "";
            modelLicense = "";
            minParserVersion = "";
        }
        inputs = new ArrayList<>();
        outputs = new ArrayList<>();
    }

    public ModelInfo(@NonNull DataInput in) throws IOException {
        modelSize = in.readLong();
        modelHash = in.readUTF();
        metadataExisted = in.readBoolean();
        modelName = in.readUTF();
        modelDescription = in.readUTF();
        modelVersion = in.readUTF();
        modelAuthor = in.readUTF();
        modelLicense = in.readUTF();
        minParserVersion = in.readUTF();
        inputs = readTensorInfoList(in);
        outputs = readTensorInfoList(in);
    }

    public void save(DataOutput out) throws IOException {
        out.writeLong(modelSize);
        out.writeUTF(modelHash);
        out.writeBoolean(metadataExisted);
        out.writeUTF(modelName);
        out.writeUTF(modelDescription);
        out.writeUTF(modelVersion);
        out.writeUTF(modelAuthor);
        out.writeUTF(modelLicense);
        out.writeUTF(minParserVersion);
        writeTensorInfoList(out, inputs);
        writeTensorInfoList(out, outputs);
    }

    public long getModelSize() {
        return modelSize;
    }

    @NonNull
    public String getModelHash() {
        return modelHash;
    }

    public boolean isMetadataExisted() {
        return metadataExisted;
    }

    @NonNull
    public String getModelName() {
        return modelName;
    }

    @NonNull
    public String getModelDescription() {
        return modelDescription;
    }

    @NonNull
    public String getModelVersion() {
        return modelVersion;
    }

    @NonNull
    public String getModelAuthor() {
        return modelAuthor;
    }

    @NonNull
    public String getModelLicense() {
        return modelLicense;
    }

    @NonNull
    public String getMinParserVersion() {
        return minParserVersion;
    }

    @NonNull
    public List<TensorInfo> getInputs() {
        return inputs;
    }

    @NonNull
    public List<TensorInfo> getOutputs() {
        return outputs;
    }

    public boolean isMetadataVersionTooHigh() {
        return isMetadataVersionTooHigh(minParserVersion);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelInfo that = (ModelInfo) o;
        return modelSize == that.modelSize
                && modelHash.equals(that.modelHash)
                && metadataExisted == that.metadataExisted
                && modelName.equals(that.modelName)
                && modelDescription.equals(that.modelDescription)
                && modelVersion.equals(that.modelVersion)
                && modelAuthor.equals(that.modelAuthor)
                && modelLicense.equals(that.modelLicense)
                && minParserVersion.equals(that.minParserVersion)
                && inputs.equals(that.inputs)
                && outputs.equals(that.outputs);
    }

    @Override
    public int hashCode() {
        return modelHash.hashCode();
    }

    @NonNull
    public static ModelInfo buildFrom(ByteBuffer byteBuffer) throws TfliteModelException {
        ModelVerifier.verifyModel(byteBuffer);
        String modelHash = Hashing.sha256().hashBytes(byteBuffer.array()).toString();
        MetadataExtractor extractor = new MetadataExtractor(byteBuffer);
        ModelMetadata modelMetadata = extractor.getModelMetaData();
        ModelInfo modelInfo = new ModelInfo(byteBuffer.remaining(), modelHash, modelMetadata);

        int inputLength = extractor.getInputTensorCount();
        for (int i = 0; i < inputLength; i++) {
            modelInfo.inputs.add(TensorInfo.parseFrom(extractor, TensorInfo.Source.INPUT, i));
        }
        int outputLength = extractor.getOutputTensorCount();
        for (int i = 0; i < outputLength; i++) {
            modelInfo.outputs.add(TensorInfo.parseFrom(extractor, TensorInfo.Source.OUTPUT, i));
        }

        return modelInfo;
    }

    static boolean isMetadataVersionTooHigh(@NonNull String minParserVersion) {
        if (Strings.isNullOrEmpty(minParserVersion)) {
            return false;
        }

        return MetadataExtractor.compareVersions(PARSER_VERSION, minParserVersion) < 0;
    }
}
