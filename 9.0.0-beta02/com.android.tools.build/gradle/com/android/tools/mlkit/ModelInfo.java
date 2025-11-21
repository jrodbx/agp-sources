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

import static com.android.tools.mlkit.DataInputOutputUtils.readTensorGroupInfoList;
import static com.android.tools.mlkit.DataInputOutputUtils.readTensorInfoList;
import static com.android.tools.mlkit.DataInputOutputUtils.writeTensorGroupInfoList;
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
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import org.tensorflow.lite.support.metadata.schema.SubGraphMetadata;

/** Stores necessary data for one model. */
public class ModelInfo {
    private final long modelSize;
    @NonNull private final String modelHash;

    private final boolean metadataExisted;
    private final boolean minParserVersionSatisfied;
    @NonNull private final String modelName;
    @NonNull private final String modelDescription;
    @NonNull private final String modelVersion;
    @NonNull private final String modelAuthor;
    @NonNull private final String modelLicense;
    @NonNull private final String minParserVersion;

    @NonNull private final List<TensorInfo> inputs;
    @NonNull private final List<TensorInfo> outputs;
    @NonNull private final List<TensorGroupInfo> inputTensorGroupInfos;
    @NonNull private final List<TensorGroupInfo> outputTensorGroupInfos;

    public ModelInfo(
            long modelSize, @NonNull String modelHash, @NonNull MetadataExtractor extractor) {
        this.modelSize = modelSize;
        this.modelHash = modelHash;

        ModelMetadata modelMetadata = extractor.hasMetadata() ? extractor.getModelMetadata() : null;
        if (modelMetadata != null) {
            metadataExisted = true;
            minParserVersionSatisfied = extractor.isMinimumParserVersionSatisfied();
            modelName = Strings.nullToEmpty(modelMetadata.name());
            modelDescription = Strings.nullToEmpty(modelMetadata.description());
            modelVersion = Strings.nullToEmpty(modelMetadata.version());
            modelAuthor = Strings.nullToEmpty(modelMetadata.author());
            modelLicense = Strings.nullToEmpty(modelMetadata.license());
            minParserVersion =
                    Strings.isNullOrEmpty(modelMetadata.minParserVersion())
                            ? "1.0.0"
                            : modelMetadata.minParserVersion();
        } else {
            metadataExisted = false;
            minParserVersionSatisfied = true;
            modelName = "";
            modelDescription = "";
            modelVersion = "";
            modelAuthor = "";
            modelLicense = "";
            minParserVersion = "1.0.0";
        }
        inputs = new ArrayList<>();
        outputs = new ArrayList<>();
        inputTensorGroupInfos = new ArrayList<>();
        outputTensorGroupInfos = new ArrayList<>();
    }

    public ModelInfo(@NonNull DataInput in) throws IOException {
        modelSize = in.readLong();
        modelHash = in.readUTF();
        metadataExisted = in.readBoolean();
        minParserVersionSatisfied = in.readBoolean();
        modelName = in.readUTF();
        modelDescription = in.readUTF();
        modelVersion = in.readUTF();
        modelAuthor = in.readUTF();
        modelLicense = in.readUTF();
        minParserVersion = in.readUTF();
        inputs = readTensorInfoList(in);
        outputs = readTensorInfoList(in);
        inputTensorGroupInfos = readTensorGroupInfoList(in);
        outputTensorGroupInfos = readTensorGroupInfoList(in);
    }

    public void save(DataOutput out) throws IOException {
        out.writeLong(modelSize);
        out.writeUTF(modelHash);
        out.writeBoolean(metadataExisted);
        out.writeBoolean(minParserVersionSatisfied);
        out.writeUTF(modelName);
        out.writeUTF(modelDescription);
        out.writeUTF(modelVersion);
        out.writeUTF(modelAuthor);
        out.writeUTF(modelLicense);
        out.writeUTF(minParserVersion);
        writeTensorInfoList(out, inputs);
        writeTensorInfoList(out, outputs);
        writeTensorGroupInfoList(out, inputTensorGroupInfos);
        writeTensorGroupInfoList(out, outputTensorGroupInfos);
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
    public boolean isMinParserVersionSatisfied() {
        return minParserVersionSatisfied;
    }

    @NonNull
    public List<TensorInfo> getInputs() {
        return inputs;
    }

    @NonNull
    public List<TensorInfo> getOutputs() {
        return outputs;
    }

    @NonNull
    public List<TensorGroupInfo> getInputTensorGroups() {
        return inputTensorGroupInfos;
    }

    @NonNull
    public List<TensorGroupInfo> getOutputTensorGroups() {
        return outputTensorGroupInfos;
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
                && outputs.equals(that.outputs)
                && inputTensorGroupInfos.equals(that.inputTensorGroupInfos)
                && outputTensorGroupInfos.equals(that.outputTensorGroupInfos);
    }

    @Override
    public int hashCode() {
        return modelHash.hashCode();
    }

    @NonNull
    public static ModelInfo buildFrom(@NonNull ByteBuffer byteBuffer) throws TfliteModelException {
        MetadataExtractor extractor = ModelVerifier.getExtractorWithVerification(byteBuffer);
        String modelHash = Hashing.sha256().hashBytes(byteBuffer.array()).toString();
        ModelInfo modelInfo = new ModelInfo(byteBuffer.remaining(), modelHash, extractor);

        int inputLength = extractor.getInputTensorCount();
        for (int i = 0; i < inputLength; i++) {
            modelInfo.inputs.add(TensorInfo.parseFrom(extractor, TensorInfo.Source.INPUT, i));
        }
        int outputLength = extractor.getOutputTensorCount();
        for (int i = 0; i < outputLength; i++) {
            modelInfo.outputs.add(TensorInfo.parseFrom(extractor, TensorInfo.Source.OUTPUT, i));
        }

        if (extractor.hasMetadata()) {
            ModelMetadata modelMetadata = extractor.getModelMetadata();
            SubGraphMetadata subGraphMetadata = modelMetadata.subgraphMetadata(0);
            int inputGroupLen = subGraphMetadata.inputTensorGroupsLength();
            for (int i = 0; i < inputGroupLen; i++) {
                modelInfo.inputTensorGroupInfos.add(
                        new TensorGroupInfo(subGraphMetadata.inputTensorGroups(i)));
            }
            int outputGroupLen = subGraphMetadata.outputTensorGroupsLength();
            for (int i = 0; i < outputGroupLen; i++) {
                modelInfo.outputTensorGroupInfos.add(
                        new TensorGroupInfo(subGraphMetadata.outputTensorGroups(i)));
            }
        }

        return modelInfo;
    }
}
