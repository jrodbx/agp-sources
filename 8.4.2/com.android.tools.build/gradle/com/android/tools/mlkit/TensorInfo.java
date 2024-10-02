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
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import org.tensorflow.lite.support.metadata.schema.AssociatedFile;
import org.tensorflow.lite.support.metadata.schema.Content;
import org.tensorflow.lite.support.metadata.schema.ContentProperties;
import org.tensorflow.lite.support.metadata.schema.ModelMetadata;
import org.tensorflow.lite.support.metadata.schema.NormalizationOptions;
import org.tensorflow.lite.support.metadata.schema.ProcessUnit;
import org.tensorflow.lite.support.metadata.schema.ProcessUnitOptions;
import org.tensorflow.lite.support.metadata.schema.Stats;
import org.tensorflow.lite.support.metadata.schema.TensorMetadata;

/**
 * Stores necessary data for each single input or output. For tflite model, this class stores
 * necessary data for input or output tensor.
 */
public class TensorInfo {
    private static final String DEFAULT_INPUT_NAME = "inputFeature";
    private static final String DEFAULT_OUTPUT_NAME = "outputFeature";

    public enum DataType {
        UNKNOWN((byte) -1),
        FLOAT32((byte) 0),
        INT32((byte) 2),
        UINT8((byte) 3),
        INT64((byte) 4);

        private final byte id;

        DataType(byte id) {
            this.id = id;
        }

        public static DataType fromByte(byte id) {
            for (DataType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    public enum Source {
        UNKNOWN((byte) 0),
        INPUT((byte) 1),
        OUTPUT((byte) 2);

        private final byte id;

        Source(byte id) {
            this.id = id;
        }

        public static Source fromByte(byte id) {
            for (Source source : values()) {
                if (source.id == id) {
                    return source;
                }
            }
            return UNKNOWN;
        }
    }

    public enum FileType {
        UNKNOWN((byte) 0),
        DESCRIPTIONS((byte) 1),
        TENSOR_AXIS_LABELS((byte) 2),
        TENSOR_VALUE_LABELS((byte) 3),
        TENSOR_AXIS_SCORE_CALIBRATION((byte) 4);

        private final byte id;

        FileType(byte id) {
            this.id = id;
        }

        public static FileType fromByte(byte id) {
            for (FileType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    public enum ContentType {
        UNKNOWN((byte) 0),
        FEATURE((byte) 1),
        IMAGE((byte) 2),
        BOUNDING_BOX((byte) 3);

        private final byte id;

        ContentType(byte id) {
            this.id = id;
        }

        public static ContentType fromByte(byte id) {
            for (ContentType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    // Infos from model itself.
    @NonNull private final Source source;
    private final int index;
    @NonNull private final DataType dataType;
    private final int[] shape;
    @NonNull private final QuantizationParams quantizationParams;

    // Infos from model metadata.
    private final boolean metadataExisted;
    @NonNull private final String name;
    @NonNull private final String identifierName;
    @NonNull private final String description;
    @NonNull private final ContentType contentType;
    @NonNull private final ContentRange contentRange;
    @NonNull private final String fileName;
    @NonNull private final FileType fileType;
    @NonNull private final NormalizationParams normalizationParams;
    @Nullable
    private final ImageProperties imageProperties;
    @Nullable
    private final BoundingBoxProperties boundingBoxProperties;

    public TensorInfo(
            Source source,
            int index,
            DataType dataType,
            int[] shape,
            QuantizationParams quantizationParams,
            boolean metadataExisted,
            String name,
            String description,
            ContentType contentType,
            ContentRange contentRange,
            String fileName,
            FileType fileType,
            NormalizationParams normalizationParams,
            @Nullable ImageProperties imageProperties,
            @Nullable BoundingBoxProperties boundingBoxProperties) {
        this.source = source;
        this.index = index;
        this.dataType = dataType;
        this.shape = shape;
        this.quantizationParams = quantizationParams;
        this.metadataExisted = metadataExisted;
        this.name = name;
        this.description = description;
        this.contentType = contentType;
        this.contentRange = contentRange;
        this.fileName = fileName;
        this.fileType = fileType;
        this.normalizationParams = normalizationParams;
        this.imageProperties = imageProperties;
        this.boundingBoxProperties = boundingBoxProperties;

        this.identifierName = MlNames.computeIdentifierName(name, getDefaultName(source, index));
    }

    public TensorInfo(@NonNull DataInput in) throws IOException {
        // Read infos from model itself.
        source = Source.fromByte(in.readByte());
        index = in.readInt();
        dataType = DataType.fromByte(in.readByte());
        shape = DataInputOutputUtils.readIntArray(in);
        quantizationParams = new QuantizationParams(in);

        // Read infos from model metadata.
        metadataExisted = in.readBoolean();
        name = in.readUTF();
        description = in.readUTF();
        contentType = ContentType.fromByte(in.readByte());
        contentRange = new ContentRange(in);
        fileName = in.readUTF();
        fileType = FileType.fromByte(in.readByte());
        normalizationParams = new NormalizationParams(in);
        imageProperties = in.readBoolean() ? new ImageProperties(in) : null;
        boundingBoxProperties = in.readBoolean() ? new BoundingBoxProperties(in) : null;

        identifierName = MlNames.computeIdentifierName(name, getDefaultName(source, index));
    }

    public void save(@NonNull DataOutput out) throws IOException {
        // Save infos from model itself.
        out.write(source.id);
        out.writeInt(index);
        out.write(dataType.id);
        DataInputOutputUtils.writeIntArray(out, shape);
        quantizationParams.save(out);

        // Save infos from model metadata.
        out.writeBoolean(metadataExisted);
        out.writeUTF(name);
        out.writeUTF(description);
        out.write(contentType.id);
        contentRange.save(out);
        out.writeUTF(fileName);
        out.write(fileType.id);
        normalizationParams.save(out);
        out.writeBoolean(imageProperties != null);
        if (imageProperties != null) {
            imageProperties.save(out);
        }
        out.writeBoolean(boundingBoxProperties != null);
        if (boundingBoxProperties != null) {
            boundingBoxProperties.save(out);
        }
    }

    @NonNull
    public Source getSource() {
        return source;
    }

    @NonNull
    public DataType getDataType() {
        return dataType;
    }

    @NonNull
    public int[] getShape() {
        return shape;
    }

    @NonNull
    public QuantizationParams getQuantizationParams() {
        return quantizationParams;
    }

    public boolean isMetadataExisted() {
        return metadataExisted;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getIdentifierName() {
        return identifierName;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    @NonNull
    public ContentType getContentType() {
        return contentType;
    }

    @NonNull
    public ContentRange getContentRange() {
        return contentRange;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public FileType getFileType() {
        return fileType;
    }

    @NonNull
    public NormalizationParams getNormalizationParams() {
        return normalizationParams;
    }

    @Nullable
    public ImageProperties getImageProperties() {
        return imageProperties;
    }

    @Nullable
    public BoundingBoxProperties getBoundingBoxProperties() {
        return boundingBoxProperties;
    }

    public boolean isRGBImage() {
        return imageProperties != null
                && imageProperties.colorSpaceType == ImageProperties.ColorSpaceType.RGB;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TensorInfo that = (TensorInfo) o;
        return index == that.index
                && source == that.source
                && dataType == that.dataType
                && Arrays.equals(shape, that.shape)
                && Objects.equals(quantizationParams, that.quantizationParams)
                && metadataExisted == that.metadataExisted
                && name.equals(that.name)
                && description.equals(that.description)
                && contentType == that.contentType
                && Objects.equals(contentRange, that.contentRange)
                && fileName.equals(that.fileName)
                && fileType == that.fileType
                && Objects.equals(normalizationParams, that.normalizationParams)
                && Objects.equals(imageProperties, that.imageProperties)
                && Objects.equals(boundingBoxProperties, that.boundingBoxProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, index, dataType, metadataExisted, name, description);
    }

    private static class Builder {
        private Source source = Source.UNKNOWN;
        private int index;
        private DataType dataType = DataType.UNKNOWN;
        private int[] shape;
        private QuantizationParams quantizationParams;
        private boolean metadataExisted;
        private String name = "";
        private String description = "";
        private ContentType contentType = ContentType.UNKNOWN;
        private ContentRange contentRange = new ContentRange(-1, -1);
        private String fileName = "";
        private FileType fileType = FileType.UNKNOWN;
        @Nullable private NormalizationParams normalizationParams;
        @Nullable
        private ImageProperties imageProperties;
        @Nullable
        private BoundingBoxProperties boundingBoxProperties;

        private Builder setSource(Source source) {
            this.source = source;
            return this;
        }

        private Builder setIndex(int index) {
            this.index = index;
            return this;
        }

        private Builder setDataType(DataType dataType) {
            this.dataType = dataType;
            return this;
        }

        private Builder setShape(int[] shape) {
            this.shape = shape;
            return this;
        }

        private Builder setQuantizationParams(QuantizationParams quantizationParams) {
            this.quantizationParams = quantizationParams;
            return this;
        }

        private Builder setMetadataExisted(boolean metadataExisted) {
            this.metadataExisted = metadataExisted;
            return this;
        }

        private Builder setName(String name) {
            this.name = name;
            return this;
        }

        private Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        private Builder setContentType(ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        private Builder setContentRange(ContentRange contentRange) {
            this.contentRange = contentRange;
            return this;
        }

        private Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        private Builder setFileType(FileType fileType) {
            this.fileType = fileType;
            return this;
        }

        private Builder setNormalizationParams(NormalizationParams normalizationParams) {
            this.normalizationParams = normalizationParams;
            return this;
        }

        public Builder setImageProperties(ImageProperties imageProperties) {
            this.imageProperties = imageProperties;
            return this;
        }

        public Builder setBoundingBoxProperties(BoundingBoxProperties boundingBoxProperties) {
            this.boundingBoxProperties = boundingBoxProperties;
            return this;
        }

        private TensorInfo build() {
            return new TensorInfo(
                    source,
                    index,
                    dataType,
                    shape,
                    quantizationParams,
                    metadataExisted,
                    name,
                    description,
                    contentType,
                    contentRange,
                    fileName,
                    fileType,
                    normalizationParams != null
                            ? normalizationParams
                            : new NormalizationParams(
                                    toFloatBuffer(0),
                                    toFloatBuffer(1),
                                    toFloatBuffer(Float.MIN_VALUE),
                                    toFloatBuffer(Float.MAX_VALUE)),
                    imageProperties,
                    boundingBoxProperties);
        }
    }

    public static TensorInfo parseFrom(MetadataExtractor extractor, Source source, int index) {
        TensorInfo.Builder builder = new TensorInfo.Builder();
        builder.setSource(source).setIndex(index);

        // Deal with data from original model
        if (source == Source.INPUT) {
            MetadataExtractor.QuantizationParams params =
                    extractor.getInputTensorQuantizationParams(index);
            builder.setDataType(DataType.fromByte(extractor.getInputTensorType(index)))
                    .setShape(extractor.getInputTensorShape(index))
                    .setQuantizationParams(
                            new QuantizationParams(params.getScale(), params.getZeroPoint()));
        } else {
            MetadataExtractor.QuantizationParams params =
                    extractor.getOutputTensorQuantizationParams(index);
            builder.setDataType(DataType.fromByte(extractor.getOutputTensorType(index)))
                    .setShape(extractor.getOutputTensorShape(index))
                    .setQuantizationParams(
                            new QuantizationParams(params.getScale(), params.getZeroPoint()));
        }

        // Deal with data from extra metadata
        ModelMetadata metadata = extractor.hasMetadata() ? extractor.getModelMetadata() : null;
        if (metadata == null) {
            builder.setMetadataExisted(false);
        } else if (!extractor.isMinimumParserVersionSatisfied()) {
            builder.setMetadataExisted(true);

            // Get name and normalization data to generate compatible APIs
            TensorMetadata tensorMetadata =
                    source == Source.INPUT
                            ? metadata.subgraphMetadata(0).inputTensorMetadata(index)
                            : metadata.subgraphMetadata(0).outputTensorMetadata(index);
            builder.setName(Strings.nullToEmpty(tensorMetadata.name()));
            builder.setNormalizationParams(extractNormalizationParams(tensorMetadata));
        } else {
            builder.setMetadataExisted(true);

            TensorMetadata tensorMetadata =
                    source == Source.INPUT
                            ? metadata.subgraphMetadata(0).inputTensorMetadata(index)
                            : metadata.subgraphMetadata(0).outputTensorMetadata(index);

            builder.setName(Strings.nullToEmpty(tensorMetadata.name()))
                    .setDescription(Strings.nullToEmpty(tensorMetadata.description()))
                    .setContentType(extractContentType(tensorMetadata));

            Content content = tensorMetadata.content();
            if (content != null && content.range() != null) {
                builder.setContentRange(new ContentRange(content.range().min(), content.range().max()));
            }

            AssociatedFile file = getPreferredAssociatedFile(tensorMetadata);
            if (file != null) {
                builder.setFileName(Strings.nullToEmpty(file.name()))
                        .setFileType(FileType.fromByte(file.type()));
            }

            if (builder.contentType == ContentType.IMAGE) {
                org.tensorflow.lite.support.metadata.schema.ImageProperties properties =
                        (org.tensorflow.lite.support.metadata.schema.ImageProperties)
                                tensorMetadata
                                        .content()
                                        .contentProperties(
                                                new org.tensorflow.lite.support.metadata.schema
                                                        .ImageProperties());
                builder.setImageProperties(
                        new ImageProperties(
                                ImageProperties.ColorSpaceType.fromByte(properties.colorSpace())));
            } else if (builder.contentType == ContentType.BOUNDING_BOX) {
                org.tensorflow.lite.support.metadata.schema.BoundingBoxProperties properties =
                        (org.tensorflow.lite.support.metadata.schema.BoundingBoxProperties)
                                tensorMetadata
                                        .content()
                                        .contentProperties(
                                                new org.tensorflow.lite.support.metadata.schema
                                                        .BoundingBoxProperties());
                builder.setBoundingBoxProperties(new BoundingBoxProperties(
                        BoundingBoxProperties.Type.fromByte(properties.type()),
                        BoundingBoxProperties.CoordinateType.fromByte(properties.coordinateType()),
                        properties.indexAsByteBuffer().asIntBuffer()));
            }

            builder.setNormalizationParams(extractNormalizationParams(tensorMetadata));
        }

        return builder.build();
    }

    private static FloatBuffer toFloatBuffer(float value) {
        return FloatBuffer.wrap(new float[] {value});
    }

    private static String getDefaultName(Source source, int index) {
        return (source == Source.INPUT ? DEFAULT_INPUT_NAME : DEFAULT_OUTPUT_NAME) + index;
    }

    /**
     * Gets preferred associated file among multiple files with following priority: Locale(English)
     * > Locale(Any) > No Locale. Otherwise select the first file.
     */
    @Nullable
    private static AssociatedFile getPreferredAssociatedFile(
            @NonNull TensorMetadata tensorMetadata) {
        AssociatedFile defaultFile = tensorMetadata.associatedFiles(0);
        int length = tensorMetadata.associatedFilesLength();
        for (int i = 0; i < length; i++) {
            AssociatedFile associatedFile = tensorMetadata.associatedFiles(i);
            String localeTag = associatedFile.locale();
            if (localeTag != null) {
                if (Locale.ENGLISH.equals(Locale.forLanguageTag(localeTag))) {
                    return associatedFile;
                } else {
                    defaultFile = associatedFile;
                }
            }
        }

        return defaultFile;
    }

    public static TensorInfo.ContentType extractContentType(TensorMetadata tensorMetadata) {
        Content content = tensorMetadata.content();
        if (content == null) {
            return ContentType.UNKNOWN;
        }
        byte type = content.contentPropertiesType();
        if (type == ContentProperties.ImageProperties) {
            return ContentType.IMAGE;
        } else if (type == ContentProperties.FeatureProperties) {
            return ContentType.FEATURE;
        } else if (type == ContentProperties.BoundingBoxProperties) {
            return ContentType.BOUNDING_BOX;
        }
        return ContentType.UNKNOWN;
    }

    private static NormalizationOptions extractNormalizationOptions(TensorMetadata tensorMetadata) {
        for (int i = 0; i < tensorMetadata.processUnitsLength(); i++) {
            ProcessUnit unit = tensorMetadata.processUnits(i);
            if (unit.optionsType() == ProcessUnitOptions.NormalizationOptions) {
                return (NormalizationOptions) unit.options(new NormalizationOptions());
            }
        }

        return null;
    }

    private static NormalizationParams extractNormalizationParams(TensorMetadata tensorMetadata) {
        NormalizationOptions normalizationOptions = extractNormalizationOptions(tensorMetadata);
        FloatBuffer mean =
                normalizationOptions != null && normalizationOptions.meanAsByteBuffer() != null
                        ? normalizationOptions.meanAsByteBuffer().asFloatBuffer()
                        : toFloatBuffer(0);
        FloatBuffer std =
                normalizationOptions != null && normalizationOptions.stdAsByteBuffer() != null
                        ? normalizationOptions.stdAsByteBuffer().asFloatBuffer()
                        : toFloatBuffer(1);

        Stats stats = tensorMetadata.stats();
        FloatBuffer min =
                stats != null && stats.minAsByteBuffer() != null
                        ? tensorMetadata.stats().minAsByteBuffer().asFloatBuffer()
                        : toFloatBuffer(Float.MIN_VALUE);
        FloatBuffer max =
                stats != null && stats.maxAsByteBuffer() != null
                        ? tensorMetadata.stats().maxAsByteBuffer().asFloatBuffer()
                        : toFloatBuffer(Float.MAX_VALUE);

        return new NormalizationParams(mean, std, min, max);
    }

    public static class ImageProperties {
        public enum ColorSpaceType {
            UNKNOWN(0),
            RGB(1),
            GRAYSCALE(2);

            private final int id;

            ColorSpaceType(int id) {
                this.id = id;
            }

            public static ColorSpaceType fromByte(byte id) {
                for (ColorSpaceType type : values()) {
                    if (type.id == id) {
                        return type;
                    }
                }
                return UNKNOWN;
            }
        }

        public final ColorSpaceType colorSpaceType;

        public ImageProperties(@NonNull ColorSpaceType colorSpaceType) {
            this.colorSpaceType = colorSpaceType;
        }

        public ImageProperties(@NonNull DataInput in) throws IOException {
            colorSpaceType = ColorSpaceType.fromByte(in.readByte());
        }

        public void save(@NonNull DataOutput out) throws IOException {
            out.write(colorSpaceType.id);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ImageProperties that = (ImageProperties) o;
            return colorSpaceType == that.colorSpaceType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(colorSpaceType);
        }
    }

    public static class BoundingBoxProperties {

        /** Denotes how a bounding box is represented. */
        public enum Type {
            UNKNOWN((byte)0),
            /**
             * Represents the bounding box by using the combination of boundaries, {left, top, right,
             * bottom}. The default order is {left, top, right, bottom}. Other orders can be indicated by an
             * index array.
             */
            BOUNDARIES((byte)1),
            /**
             * Represents the bounding box by using the upper_left corner, width and height. The default
             * order is {upper_left_x, upper_left_y, width, height}. Other orders can be indicated by an
             * index array.
             */
            UPPER_LEFT((byte)2),
            /**
             * Represents the bounding box by using the center of the box, width and height. The default
             * order is {center_x, center_y, width, height}. Other orders can be indicated by an index
             * array.
             */
            CENTER((byte)3);

            private final int id;

            Type(int id) {
                this.id = id;
            }

            public static Type fromByte(byte id) {
                for (Type type : values()) {
                    if (type.id == id) {
                        return type;
                    }
                }
                return UNKNOWN;
            }
        }

        /** Denotes if the coordinates are actual pixels or relative ratios. */
        public enum CoordinateType {
            UNKNOWN((byte)-1),
            /** The coordinates are relative ratios in range [0, 1]. */
            RATIO((byte)0),
            /** The coordinates are actual pixel values. */
            PIXEL((byte)1);

            private final int id;

            CoordinateType(int id) {
                this.id = id;
            }

            public static CoordinateType fromByte(byte id) {
                for (CoordinateType type : values()) {
                    if (type.id == id) {
                        return type;
                    }
                }
                return UNKNOWN;
            }
        }

        public final Type type;
        public final CoordinateType coordinateType;
        /**
         * Denotes the order of the elements defined in each bounding box type. An
         * empty index array represent the default order of each bounding box type.
         * For example, to denote the default order of BOUNDARIES, {left, top, right,
         * bottom}, the index should be {0, 1, 2, 3}. To denote the order {left,
         * right, top, bottom}, the order should be {0, 2, 1, 3}.
         * <p>
         * The index array can be applied to all bounding box types to adjust the
         * order of their corresponding underlying elements.
         */
        public final int[] index;

        public BoundingBoxProperties(@NonNull Type type, @NonNull CoordinateType coordinateType, @NonNull IntBuffer indexBuffer) {
            this.type = type;
            this.coordinateType = coordinateType;
            this.index = new int[indexBuffer.remaining()];
            indexBuffer.get(index);
        }

        public BoundingBoxProperties(@NonNull DataInput in) throws IOException {
            this.type = Type.fromByte(in.readByte());
            this.coordinateType = CoordinateType.fromByte(in.readByte());
            this.index = DataInputOutputUtils.readIntArray(in);
        }

        public void save(@NonNull DataOutput out) throws IOException {
            out.write(type.id);
            out.write(coordinateType.id);
            DataInputOutputUtils.writeIntArray(out, index);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoundingBoxProperties that = (BoundingBoxProperties) o;
            return type == that.type && coordinateType == that.coordinateType && Arrays.equals(index, that.index);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(type, coordinateType);
            result = 31 * result + Arrays.hashCode(index);
            return result;
        }
    }

    public static class ContentRange {
        public final int min;
        public final int max;

        public ContentRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public ContentRange(@NonNull DataInput in) throws IOException {
            this.min = in.readInt();
            this.max = in.readInt();
        }

        public void save(@NonNull DataOutput out) throws IOException {
            out.writeInt(min);
            out.writeInt(max);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContentRange that = (ContentRange) o;
            return min == that.min && max == that.max;
        }

        @Override
        public int hashCode() {
            return Objects.hash(min, max);
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
}
