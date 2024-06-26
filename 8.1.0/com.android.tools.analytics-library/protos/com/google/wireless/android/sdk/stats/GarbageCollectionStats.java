// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * <pre>
 * Statistics about the garbage collector.
 * </pre>
 *
 * Protobuf type {@code android_studio.GarbageCollectionStats}
 */
public final class GarbageCollectionStats extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.GarbageCollectionStats)
    GarbageCollectionStatsOrBuilder {
private static final long serialVersionUID = 0L;
  // Use GarbageCollectionStats.newBuilder() to construct.
  private GarbageCollectionStats(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private GarbageCollectionStats() {
    name_ = "";
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new GarbageCollectionStats();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private GarbageCollectionStats(
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
          case 10: {
            com.google.protobuf.ByteString bs = input.readBytes();
            bitField0_ |= 0x00000001;
            name_ = bs;
            break;
          }
          case 16: {
            bitField0_ |= 0x00000002;
            gcCollections_ = input.readInt64();
            break;
          }
          case 24: {
            bitField0_ |= 0x00000004;
            gcTime_ = input.readInt64();
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
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GarbageCollectionStats_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GarbageCollectionStats_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.GarbageCollectionStats.class, com.google.wireless.android.sdk.stats.GarbageCollectionStats.Builder.class);
  }

  private int bitField0_;
  public static final int NAME_FIELD_NUMBER = 1;
  private volatile java.lang.Object name_;
  /**
   * <pre>
   * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
   * </pre>
   *
   * <code>optional string name = 1;</code>
   * @return Whether the name field is set.
   */
  @java.lang.Override
  public boolean hasName() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <pre>
   * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
   * </pre>
   *
   * <code>optional string name = 1;</code>
   * @return The name.
   */
  @java.lang.Override
  public java.lang.String getName() {
    java.lang.Object ref = name_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      if (bs.isValidUtf8()) {
        name_ = s;
      }
      return s;
    }
  }
  /**
   * <pre>
   * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
   * </pre>
   *
   * <code>optional string name = 1;</code>
   * @return The bytes for name.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString
      getNameBytes() {
    java.lang.Object ref = name_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      name_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int GC_COLLECTIONS_FIELD_NUMBER = 2;
  private long gcCollections_;
  /**
   * <pre>
   * Number of garbage collector invocations since last report.
   * </pre>
   *
   * <code>optional int64 gc_collections = 2;</code>
   * @return Whether the gcCollections field is set.
   */
  @java.lang.Override
  public boolean hasGcCollections() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <pre>
   * Number of garbage collector invocations since last report.
   * </pre>
   *
   * <code>optional int64 gc_collections = 2;</code>
   * @return The gcCollections.
   */
  @java.lang.Override
  public long getGcCollections() {
    return gcCollections_;
  }

  public static final int GC_TIME_FIELD_NUMBER = 3;
  private long gcTime_;
  /**
   * <pre>
   * Time spent garbage collecting since last report in milliseconds.
   * </pre>
   *
   * <code>optional int64 gc_time = 3;</code>
   * @return Whether the gcTime field is set.
   */
  @java.lang.Override
  public boolean hasGcTime() {
    return ((bitField0_ & 0x00000004) != 0);
  }
  /**
   * <pre>
   * Time spent garbage collecting since last report in milliseconds.
   * </pre>
   *
   * <code>optional int64 gc_time = 3;</code>
   * @return The gcTime.
   */
  @java.lang.Override
  public long getGcTime() {
    return gcTime_;
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
      com.google.protobuf.GeneratedMessageV3.writeString(output, 1, name_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeInt64(2, gcCollections_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      output.writeInt64(3, gcTime_);
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (((bitField0_ & 0x00000001) != 0)) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, name_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(2, gcCollections_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(3, gcTime_);
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
    if (!(obj instanceof com.google.wireless.android.sdk.stats.GarbageCollectionStats)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.GarbageCollectionStats other = (com.google.wireless.android.sdk.stats.GarbageCollectionStats) obj;

    if (hasName() != other.hasName()) return false;
    if (hasName()) {
      if (!getName()
          .equals(other.getName())) return false;
    }
    if (hasGcCollections() != other.hasGcCollections()) return false;
    if (hasGcCollections()) {
      if (getGcCollections()
          != other.getGcCollections()) return false;
    }
    if (hasGcTime() != other.hasGcTime()) return false;
    if (hasGcTime()) {
      if (getGcTime()
          != other.getGcTime()) return false;
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
    if (hasName()) {
      hash = (37 * hash) + NAME_FIELD_NUMBER;
      hash = (53 * hash) + getName().hashCode();
    }
    if (hasGcCollections()) {
      hash = (37 * hash) + GC_COLLECTIONS_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getGcCollections());
    }
    if (hasGcTime()) {
      hash = (37 * hash) + GC_TIME_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getGcTime());
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.GarbageCollectionStats prototype) {
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
   * Statistics about the garbage collector.
   * </pre>
   *
   * Protobuf type {@code android_studio.GarbageCollectionStats}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.GarbageCollectionStats)
      com.google.wireless.android.sdk.stats.GarbageCollectionStatsOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GarbageCollectionStats_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GarbageCollectionStats_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.GarbageCollectionStats.class, com.google.wireless.android.sdk.stats.GarbageCollectionStats.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.GarbageCollectionStats.newBuilder()
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
      name_ = "";
      bitField0_ = (bitField0_ & ~0x00000001);
      gcCollections_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000002);
      gcTime_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000004);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_GarbageCollectionStats_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.GarbageCollectionStats getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.GarbageCollectionStats.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.GarbageCollectionStats build() {
      com.google.wireless.android.sdk.stats.GarbageCollectionStats result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.GarbageCollectionStats buildPartial() {
      com.google.wireless.android.sdk.stats.GarbageCollectionStats result = new com.google.wireless.android.sdk.stats.GarbageCollectionStats(this);
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        to_bitField0_ |= 0x00000001;
      }
      result.name_ = name_;
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.gcCollections_ = gcCollections_;
        to_bitField0_ |= 0x00000002;
      }
      if (((from_bitField0_ & 0x00000004) != 0)) {
        result.gcTime_ = gcTime_;
        to_bitField0_ |= 0x00000004;
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
      if (other instanceof com.google.wireless.android.sdk.stats.GarbageCollectionStats) {
        return mergeFrom((com.google.wireless.android.sdk.stats.GarbageCollectionStats)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.GarbageCollectionStats other) {
      if (other == com.google.wireless.android.sdk.stats.GarbageCollectionStats.getDefaultInstance()) return this;
      if (other.hasName()) {
        bitField0_ |= 0x00000001;
        name_ = other.name_;
        onChanged();
      }
      if (other.hasGcCollections()) {
        setGcCollections(other.getGcCollections());
      }
      if (other.hasGcTime()) {
        setGcTime(other.getGcTime());
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
      com.google.wireless.android.sdk.stats.GarbageCollectionStats parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.google.wireless.android.sdk.stats.GarbageCollectionStats) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private java.lang.Object name_ = "";
    /**
     * <pre>
     * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
     * </pre>
     *
     * <code>optional string name = 1;</code>
     * @return Whether the name field is set.
     */
    public boolean hasName() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <pre>
     * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
     * </pre>
     *
     * <code>optional string name = 1;</code>
     * @return The name.
     */
    public java.lang.String getName() {
      java.lang.Object ref = name_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          name_ = s;
        }
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <pre>
     * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
     * </pre>
     *
     * <code>optional string name = 1;</code>
     * @return The bytes for name.
     */
    public com.google.protobuf.ByteString
        getNameBytes() {
      java.lang.Object ref = name_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        name_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <pre>
     * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
     * </pre>
     *
     * <code>optional string name = 1;</code>
     * @param value The name to set.
     * @return This builder for chaining.
     */
    public Builder setName(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000001;
      name_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
     * </pre>
     *
     * <code>optional string name = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearName() {
      bitField0_ = (bitField0_ & ~0x00000001);
      name_ = getDefaultInstance().getName();
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Name of the garbage collector (e.g. 'ConcurrentMarkSweep')
     * </pre>
     *
     * <code>optional string name = 1;</code>
     * @param value The bytes for name to set.
     * @return This builder for chaining.
     */
    public Builder setNameBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000001;
      name_ = value;
      onChanged();
      return this;
    }

    private long gcCollections_ ;
    /**
     * <pre>
     * Number of garbage collector invocations since last report.
     * </pre>
     *
     * <code>optional int64 gc_collections = 2;</code>
     * @return Whether the gcCollections field is set.
     */
    @java.lang.Override
    public boolean hasGcCollections() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <pre>
     * Number of garbage collector invocations since last report.
     * </pre>
     *
     * <code>optional int64 gc_collections = 2;</code>
     * @return The gcCollections.
     */
    @java.lang.Override
    public long getGcCollections() {
      return gcCollections_;
    }
    /**
     * <pre>
     * Number of garbage collector invocations since last report.
     * </pre>
     *
     * <code>optional int64 gc_collections = 2;</code>
     * @param value The gcCollections to set.
     * @return This builder for chaining.
     */
    public Builder setGcCollections(long value) {
      bitField0_ |= 0x00000002;
      gcCollections_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Number of garbage collector invocations since last report.
     * </pre>
     *
     * <code>optional int64 gc_collections = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearGcCollections() {
      bitField0_ = (bitField0_ & ~0x00000002);
      gcCollections_ = 0L;
      onChanged();
      return this;
    }

    private long gcTime_ ;
    /**
     * <pre>
     * Time spent garbage collecting since last report in milliseconds.
     * </pre>
     *
     * <code>optional int64 gc_time = 3;</code>
     * @return Whether the gcTime field is set.
     */
    @java.lang.Override
    public boolean hasGcTime() {
      return ((bitField0_ & 0x00000004) != 0);
    }
    /**
     * <pre>
     * Time spent garbage collecting since last report in milliseconds.
     * </pre>
     *
     * <code>optional int64 gc_time = 3;</code>
     * @return The gcTime.
     */
    @java.lang.Override
    public long getGcTime() {
      return gcTime_;
    }
    /**
     * <pre>
     * Time spent garbage collecting since last report in milliseconds.
     * </pre>
     *
     * <code>optional int64 gc_time = 3;</code>
     * @param value The gcTime to set.
     * @return This builder for chaining.
     */
    public Builder setGcTime(long value) {
      bitField0_ |= 0x00000004;
      gcTime_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Time spent garbage collecting since last report in milliseconds.
     * </pre>
     *
     * <code>optional int64 gc_time = 3;</code>
     * @return This builder for chaining.
     */
    public Builder clearGcTime() {
      bitField0_ = (bitField0_ & ~0x00000004);
      gcTime_ = 0L;
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


    // @@protoc_insertion_point(builder_scope:android_studio.GarbageCollectionStats)
  }

  // @@protoc_insertion_point(class_scope:android_studio.GarbageCollectionStats)
  private static final com.google.wireless.android.sdk.stats.GarbageCollectionStats DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.GarbageCollectionStats();
  }

  public static com.google.wireless.android.sdk.stats.GarbageCollectionStats getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<GarbageCollectionStats>
      PARSER = new com.google.protobuf.AbstractParser<GarbageCollectionStats>() {
    @java.lang.Override
    public GarbageCollectionStats parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new GarbageCollectionStats(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<GarbageCollectionStats> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<GarbageCollectionStats> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.GarbageCollectionStats getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

