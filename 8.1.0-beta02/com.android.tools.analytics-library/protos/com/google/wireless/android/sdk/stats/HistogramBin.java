// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * <pre>
 * Represents a range of values and the number of samples that fell within that
 * range. The start and end attributes encode the range, and the samples
 * attribute holds the number of samples. For example, if start=10 end=20,
 * and samples=100, this means that 100 samples were recorded that fell within
 * the range of start &lt;= sample &lt; end.
 * </pre>
 *
 * Protobuf type {@code android_studio.HistogramBin}
 */
public final class HistogramBin extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.HistogramBin)
    HistogramBinOrBuilder {
private static final long serialVersionUID = 0L;
  // Use HistogramBin.newBuilder() to construct.
  private HistogramBin(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private HistogramBin() {
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new HistogramBin();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private HistogramBin(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new java.lang.NullPointerException();
    }
    int mutable_bitField0_ = 0;
    com.google.protobuf.UnknownFieldSet.Builder unknownFields =
        com.google.protobuf.UnknownFieldSet.newBuilder();
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          case 8: {
            bitField0_ |= 0x00000001;
            start_ = input.readInt64();
            break;
          }
          case 16: {
            bitField0_ |= 0x00000002;
            end_ = input.readInt64();
            break;
          }
          case 24: {
            bitField0_ |= 0x00000004;
            samples_ = input.readInt64();
            break;
          }
          case 32: {
            bitField0_ |= 0x00000008;
            totalSamples_ = input.readInt64();
            break;
          }
          default: {
            if (!parseUnknownField(
                input, unknownFields, extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e).setUnfinishedMessage(this);
    } finally {
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_HistogramBin_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_HistogramBin_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.HistogramBin.class, com.google.wireless.android.sdk.stats.HistogramBin.Builder.class);
  }

  private int bitField0_;
  public static final int START_FIELD_NUMBER = 1;
  private long start_;
  /**
   * <pre>
   * Start value for this bin, inclusive. All the samples in this bin are equal
   * or greater than this value.
   * </pre>
   *
   * <code>optional int64 start = 1;</code>
   * @return Whether the start field is set.
   */
  @java.lang.Override
  public boolean hasStart() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <pre>
   * Start value for this bin, inclusive. All the samples in this bin are equal
   * or greater than this value.
   * </pre>
   *
   * <code>optional int64 start = 1;</code>
   * @return The start.
   */
  @java.lang.Override
  public long getStart() {
    return start_;
  }

  public static final int END_FIELD_NUMBER = 2;
  private long end_;
  /**
   * <pre>
   * End value for this bin, exclusive. All the samples in this bin are strictly
   * less than this value.
   * </pre>
   *
   * <code>optional int64 end = 2;</code>
   * @return Whether the end field is set.
   */
  @java.lang.Override
  public boolean hasEnd() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <pre>
   * End value for this bin, exclusive. All the samples in this bin are strictly
   * less than this value.
   * </pre>
   *
   * <code>optional int64 end = 2;</code>
   * @return The end.
   */
  @java.lang.Override
  public long getEnd() {
    return end_;
  }

  public static final int SAMPLES_FIELD_NUMBER = 3;
  private long samples_;
  /**
   * <pre>
   * Number of samples that fell within this bin (between the start and end
   * values)
   * </pre>
   *
   * <code>optional int64 samples = 3;</code>
   * @return Whether the samples field is set.
   */
  @java.lang.Override
  public boolean hasSamples() {
    return ((bitField0_ & 0x00000004) != 0);
  }
  /**
   * <pre>
   * Number of samples that fell within this bin (between the start and end
   * values)
   * </pre>
   *
   * <code>optional int64 samples = 3;</code>
   * @return The samples.
   */
  @java.lang.Override
  public long getSamples() {
    return samples_;
  }

  public static final int TOTAL_SAMPLES_FIELD_NUMBER = 4;
  private long totalSamples_;
  /**
   * <pre>
   * Total number of samples that are greater than or equal to the bin's start.
   * This is equal to the number of samples in this bin plus the number of
   * samples in all bins greater than this one.
   * </pre>
   *
   * <code>optional int64 total_samples = 4;</code>
   * @return Whether the totalSamples field is set.
   */
  @java.lang.Override
  public boolean hasTotalSamples() {
    return ((bitField0_ & 0x00000008) != 0);
  }
  /**
   * <pre>
   * Total number of samples that are greater than or equal to the bin's start.
   * This is equal to the number of samples in this bin plus the number of
   * samples in all bins greater than this one.
   * </pre>
   *
   * <code>optional int64 total_samples = 4;</code>
   * @return The totalSamples.
   */
  @java.lang.Override
  public long getTotalSamples() {
    return totalSamples_;
  }

  private byte memoizedIsInitialized = -1;
  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (((bitField0_ & 0x00000001) != 0)) {
      output.writeInt64(1, start_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeInt64(2, end_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      output.writeInt64(3, samples_);
    }
    if (((bitField0_ & 0x00000008) != 0)) {
      output.writeInt64(4, totalSamples_);
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (((bitField0_ & 0x00000001) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(1, start_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(2, end_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(3, samples_);
    }
    if (((bitField0_ & 0x00000008) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(4, totalSamples_);
    }
    size += unknownFields.getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof com.google.wireless.android.sdk.stats.HistogramBin)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.HistogramBin other = (com.google.wireless.android.sdk.stats.HistogramBin) obj;

    if (hasStart() != other.hasStart()) return false;
    if (hasStart()) {
      if (getStart()
          != other.getStart()) return false;
    }
    if (hasEnd() != other.hasEnd()) return false;
    if (hasEnd()) {
      if (getEnd()
          != other.getEnd()) return false;
    }
    if (hasSamples() != other.hasSamples()) return false;
    if (hasSamples()) {
      if (getSamples()
          != other.getSamples()) return false;
    }
    if (hasTotalSamples() != other.hasTotalSamples()) return false;
    if (hasTotalSamples()) {
      if (getTotalSamples()
          != other.getTotalSamples()) return false;
    }
    if (!unknownFields.equals(other.unknownFields)) return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    if (hasStart()) {
      hash = (37 * hash) + START_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getStart());
    }
    if (hasEnd()) {
      hash = (37 * hash) + END_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getEnd());
    }
    if (hasSamples()) {
      hash = (37 * hash) + SAMPLES_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getSamples());
    }
    if (hasTotalSamples()) {
      hash = (37 * hash) + TOTAL_SAMPLES_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getTotalSamples());
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.HistogramBin parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.HistogramBin prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * <pre>
   * Represents a range of values and the number of samples that fell within that
   * range. The start and end attributes encode the range, and the samples
   * attribute holds the number of samples. For example, if start=10 end=20,
   * and samples=100, this means that 100 samples were recorded that fell within
   * the range of start &lt;= sample &lt; end.
   * </pre>
   *
   * Protobuf type {@code android_studio.HistogramBin}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.HistogramBin)
      com.google.wireless.android.sdk.stats.HistogramBinOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_HistogramBin_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_HistogramBin_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.HistogramBin.class, com.google.wireless.android.sdk.stats.HistogramBin.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.HistogramBin.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3
              .alwaysUseFieldBuilders) {
      }
    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      start_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000001);
      end_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000002);
      samples_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000004);
      totalSamples_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000008);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_HistogramBin_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.HistogramBin getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.HistogramBin.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.HistogramBin build() {
      com.google.wireless.android.sdk.stats.HistogramBin result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.HistogramBin buildPartial() {
      com.google.wireless.android.sdk.stats.HistogramBin result = new com.google.wireless.android.sdk.stats.HistogramBin(this);
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        result.start_ = start_;
        to_bitField0_ |= 0x00000001;
      }
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.end_ = end_;
        to_bitField0_ |= 0x00000002;
      }
      if (((from_bitField0_ & 0x00000004) != 0)) {
        result.samples_ = samples_;
        to_bitField0_ |= 0x00000004;
      }
      if (((from_bitField0_ & 0x00000008) != 0)) {
        result.totalSamples_ = totalSamples_;
        to_bitField0_ |= 0x00000008;
      }
      result.bitField0_ = to_bitField0_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder clone() {
      return super.clone();
    }
    @java.lang.Override
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return super.setField(field, value);
    }
    @java.lang.Override
    public Builder clearField(
        com.google.protobuf.Descriptors.FieldDescriptor field) {
      return super.clearField(field);
    }
    @java.lang.Override
    public Builder clearOneof(
        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return super.clearOneof(oneof);
    }
    @java.lang.Override
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        int index, java.lang.Object value) {
      return super.setRepeatedField(field, index, value);
    }
    @java.lang.Override
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return super.addRepeatedField(field, value);
    }
    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.google.wireless.android.sdk.stats.HistogramBin) {
        return mergeFrom((com.google.wireless.android.sdk.stats.HistogramBin)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.HistogramBin other) {
      if (other == com.google.wireless.android.sdk.stats.HistogramBin.getDefaultInstance()) return this;
      if (other.hasStart()) {
        setStart(other.getStart());
      }
      if (other.hasEnd()) {
        setEnd(other.getEnd());
      }
      if (other.hasSamples()) {
        setSamples(other.getSamples());
      }
      if (other.hasTotalSamples()) {
        setTotalSamples(other.getTotalSamples());
      }
      this.mergeUnknownFields(other.unknownFields);
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      com.google.wireless.android.sdk.stats.HistogramBin parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.google.wireless.android.sdk.stats.HistogramBin) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private long start_ ;
    /**
     * <pre>
     * Start value for this bin, inclusive. All the samples in this bin are equal
     * or greater than this value.
     * </pre>
     *
     * <code>optional int64 start = 1;</code>
     * @return Whether the start field is set.
     */
    @java.lang.Override
    public boolean hasStart() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <pre>
     * Start value for this bin, inclusive. All the samples in this bin are equal
     * or greater than this value.
     * </pre>
     *
     * <code>optional int64 start = 1;</code>
     * @return The start.
     */
    @java.lang.Override
    public long getStart() {
      return start_;
    }
    /**
     * <pre>
     * Start value for this bin, inclusive. All the samples in this bin are equal
     * or greater than this value.
     * </pre>
     *
     * <code>optional int64 start = 1;</code>
     * @param value The start to set.
     * @return This builder for chaining.
     */
    public Builder setStart(long value) {
      bitField0_ |= 0x00000001;
      start_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Start value for this bin, inclusive. All the samples in this bin are equal
     * or greater than this value.
     * </pre>
     *
     * <code>optional int64 start = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearStart() {
      bitField0_ = (bitField0_ & ~0x00000001);
      start_ = 0L;
      onChanged();
      return this;
    }

    private long end_ ;
    /**
     * <pre>
     * End value for this bin, exclusive. All the samples in this bin are strictly
     * less than this value.
     * </pre>
     *
     * <code>optional int64 end = 2;</code>
     * @return Whether the end field is set.
     */
    @java.lang.Override
    public boolean hasEnd() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <pre>
     * End value for this bin, exclusive. All the samples in this bin are strictly
     * less than this value.
     * </pre>
     *
     * <code>optional int64 end = 2;</code>
     * @return The end.
     */
    @java.lang.Override
    public long getEnd() {
      return end_;
    }
    /**
     * <pre>
     * End value for this bin, exclusive. All the samples in this bin are strictly
     * less than this value.
     * </pre>
     *
     * <code>optional int64 end = 2;</code>
     * @param value The end to set.
     * @return This builder for chaining.
     */
    public Builder setEnd(long value) {
      bitField0_ |= 0x00000002;
      end_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * End value for this bin, exclusive. All the samples in this bin are strictly
     * less than this value.
     * </pre>
     *
     * <code>optional int64 end = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearEnd() {
      bitField0_ = (bitField0_ & ~0x00000002);
      end_ = 0L;
      onChanged();
      return this;
    }

    private long samples_ ;
    /**
     * <pre>
     * Number of samples that fell within this bin (between the start and end
     * values)
     * </pre>
     *
     * <code>optional int64 samples = 3;</code>
     * @return Whether the samples field is set.
     */
    @java.lang.Override
    public boolean hasSamples() {
      return ((bitField0_ & 0x00000004) != 0);
    }
    /**
     * <pre>
     * Number of samples that fell within this bin (between the start and end
     * values)
     * </pre>
     *
     * <code>optional int64 samples = 3;</code>
     * @return The samples.
     */
    @java.lang.Override
    public long getSamples() {
      return samples_;
    }
    /**
     * <pre>
     * Number of samples that fell within this bin (between the start and end
     * values)
     * </pre>
     *
     * <code>optional int64 samples = 3;</code>
     * @param value The samples to set.
     * @return This builder for chaining.
     */
    public Builder setSamples(long value) {
      bitField0_ |= 0x00000004;
      samples_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Number of samples that fell within this bin (between the start and end
     * values)
     * </pre>
     *
     * <code>optional int64 samples = 3;</code>
     * @return This builder for chaining.
     */
    public Builder clearSamples() {
      bitField0_ = (bitField0_ & ~0x00000004);
      samples_ = 0L;
      onChanged();
      return this;
    }

    private long totalSamples_ ;
    /**
     * <pre>
     * Total number of samples that are greater than or equal to the bin's start.
     * This is equal to the number of samples in this bin plus the number of
     * samples in all bins greater than this one.
     * </pre>
     *
     * <code>optional int64 total_samples = 4;</code>
     * @return Whether the totalSamples field is set.
     */
    @java.lang.Override
    public boolean hasTotalSamples() {
      return ((bitField0_ & 0x00000008) != 0);
    }
    /**
     * <pre>
     * Total number of samples that are greater than or equal to the bin's start.
     * This is equal to the number of samples in this bin plus the number of
     * samples in all bins greater than this one.
     * </pre>
     *
     * <code>optional int64 total_samples = 4;</code>
     * @return The totalSamples.
     */
    @java.lang.Override
    public long getTotalSamples() {
      return totalSamples_;
    }
    /**
     * <pre>
     * Total number of samples that are greater than or equal to the bin's start.
     * This is equal to the number of samples in this bin plus the number of
     * samples in all bins greater than this one.
     * </pre>
     *
     * <code>optional int64 total_samples = 4;</code>
     * @param value The totalSamples to set.
     * @return This builder for chaining.
     */
    public Builder setTotalSamples(long value) {
      bitField0_ |= 0x00000008;
      totalSamples_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Total number of samples that are greater than or equal to the bin's start.
     * This is equal to the number of samples in this bin plus the number of
     * samples in all bins greater than this one.
     * </pre>
     *
     * <code>optional int64 total_samples = 4;</code>
     * @return This builder for chaining.
     */
    public Builder clearTotalSamples() {
      bitField0_ = (bitField0_ & ~0x00000008);
      totalSamples_ = 0L;
      onChanged();
      return this;
    }
    @java.lang.Override
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFields(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }


    // @@protoc_insertion_point(builder_scope:android_studio.HistogramBin)
  }

  // @@protoc_insertion_point(class_scope:android_studio.HistogramBin)
  private static final com.google.wireless.android.sdk.stats.HistogramBin DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.HistogramBin();
  }

  public static com.google.wireless.android.sdk.stats.HistogramBin getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<HistogramBin>
      PARSER = new com.google.protobuf.AbstractParser<HistogramBin>() {
    @java.lang.Override
    public HistogramBin parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new HistogramBin(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<HistogramBin> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<HistogramBin> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.HistogramBin getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

