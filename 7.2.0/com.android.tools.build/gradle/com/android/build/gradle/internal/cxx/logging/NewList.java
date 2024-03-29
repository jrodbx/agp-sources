// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: cxx_logging.proto

package com.android.build.gradle.internal.cxx.logging;

/**
 * <pre>
 **
 *Message that indicates a new string list is seen in the stream.
 * </pre>
 *
 * Protobuf type {@code NewList}
 */
public  final class NewList extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:NewList)
    NewListOrBuilder {
private static final long serialVersionUID = 0L;
  // Use NewList.newBuilder() to construct.
  private NewList(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private NewList() {
    data_ = emptyIntList();
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new NewList();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private NewList(
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
            if (!((mutable_bitField0_ & 0x00000001) != 0)) {
              data_ = newIntList();
              mutable_bitField0_ |= 0x00000001;
            }
            data_.addInt(input.readInt32());
            break;
          }
          case 10: {
            int length = input.readRawVarint32();
            int limit = input.pushLimit(length);
            if (!((mutable_bitField0_ & 0x00000001) != 0) && input.getBytesUntilLimit() > 0) {
              data_ = newIntList();
              mutable_bitField0_ |= 0x00000001;
            }
            while (input.getBytesUntilLimit() > 0) {
              data_.addInt(input.readInt32());
            }
            input.popLimit(limit);
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
      if (((mutable_bitField0_ & 0x00000001) != 0)) {
        data_.makeImmutable(); // C
      }
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.android.build.gradle.internal.cxx.logging.CxxLogging.internal_static_NewList_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.android.build.gradle.internal.cxx.logging.CxxLogging.internal_static_NewList_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.android.build.gradle.internal.cxx.logging.NewList.class, com.android.build.gradle.internal.cxx.logging.NewList.Builder.class);
  }

  public static final int DATA_FIELD_NUMBER = 1;
  private com.google.protobuf.Internal.IntList data_;
  /**
   * <code>repeated int32 data = 1;</code>
   * @return A list containing the data.
   */
  public java.util.List<java.lang.Integer>
      getDataList() {
    return data_;
  }
  /**
   * <code>repeated int32 data = 1;</code>
   * @return The count of data.
   */
  public int getDataCount() {
    return data_.size();
  }
  /**
   * <code>repeated int32 data = 1;</code>
   * @param index The index of the element to return.
   * @return The data at the given index.
   */
  public int getData(int index) {
    return data_.getInt(index);
  }
  private int dataMemoizedSerializedSize = -1;

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
    getSerializedSize();
    if (getDataList().size() > 0) {
      output.writeUInt32NoTag(10);
      output.writeUInt32NoTag(dataMemoizedSerializedSize);
    }
    for (int i = 0; i < data_.size(); i++) {
      output.writeInt32NoTag(data_.getInt(i));
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    {
      int dataSize = 0;
      for (int i = 0; i < data_.size(); i++) {
        dataSize += com.google.protobuf.CodedOutputStream
          .computeInt32SizeNoTag(data_.getInt(i));
      }
      size += dataSize;
      if (!getDataList().isEmpty()) {
        size += 1;
        size += com.google.protobuf.CodedOutputStream
            .computeInt32SizeNoTag(dataSize);
      }
      dataMemoizedSerializedSize = dataSize;
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
    if (!(obj instanceof com.android.build.gradle.internal.cxx.logging.NewList)) {
      return super.equals(obj);
    }
    com.android.build.gradle.internal.cxx.logging.NewList other = (com.android.build.gradle.internal.cxx.logging.NewList) obj;

    if (!getDataList()
        .equals(other.getDataList())) return false;
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
    if (getDataCount() > 0) {
      hash = (37 * hash) + DATA_FIELD_NUMBER;
      hash = (53 * hash) + getDataList().hashCode();
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.android.build.gradle.internal.cxx.logging.NewList parseFrom(
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
  public static Builder newBuilder(com.android.build.gradle.internal.cxx.logging.NewList prototype) {
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
   **
   *Message that indicates a new string list is seen in the stream.
   * </pre>
   *
   * Protobuf type {@code NewList}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:NewList)
      com.android.build.gradle.internal.cxx.logging.NewListOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.android.build.gradle.internal.cxx.logging.CxxLogging.internal_static_NewList_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.android.build.gradle.internal.cxx.logging.CxxLogging.internal_static_NewList_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.android.build.gradle.internal.cxx.logging.NewList.class, com.android.build.gradle.internal.cxx.logging.NewList.Builder.class);
    }

    // Construct using com.android.build.gradle.internal.cxx.logging.NewList.newBuilder()
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
      data_ = emptyIntList();
      bitField0_ = (bitField0_ & ~0x00000001);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.android.build.gradle.internal.cxx.logging.CxxLogging.internal_static_NewList_descriptor;
    }

    @java.lang.Override
    public com.android.build.gradle.internal.cxx.logging.NewList getDefaultInstanceForType() {
      return com.android.build.gradle.internal.cxx.logging.NewList.getDefaultInstance();
    }

    @java.lang.Override
    public com.android.build.gradle.internal.cxx.logging.NewList build() {
      com.android.build.gradle.internal.cxx.logging.NewList result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.android.build.gradle.internal.cxx.logging.NewList buildPartial() {
      com.android.build.gradle.internal.cxx.logging.NewList result = new com.android.build.gradle.internal.cxx.logging.NewList(this);
      int from_bitField0_ = bitField0_;
      if (((bitField0_ & 0x00000001) != 0)) {
        data_.makeImmutable();
        bitField0_ = (bitField0_ & ~0x00000001);
      }
      result.data_ = data_;
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
      if (other instanceof com.android.build.gradle.internal.cxx.logging.NewList) {
        return mergeFrom((com.android.build.gradle.internal.cxx.logging.NewList)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.android.build.gradle.internal.cxx.logging.NewList other) {
      if (other == com.android.build.gradle.internal.cxx.logging.NewList.getDefaultInstance()) return this;
      if (!other.data_.isEmpty()) {
        if (data_.isEmpty()) {
          data_ = other.data_;
          bitField0_ = (bitField0_ & ~0x00000001);
        } else {
          ensureDataIsMutable();
          data_.addAll(other.data_);
        }
        onChanged();
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
      com.android.build.gradle.internal.cxx.logging.NewList parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.android.build.gradle.internal.cxx.logging.NewList) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private com.google.protobuf.Internal.IntList data_ = emptyIntList();
    private void ensureDataIsMutable() {
      if (!((bitField0_ & 0x00000001) != 0)) {
        data_ = mutableCopy(data_);
        bitField0_ |= 0x00000001;
       }
    }
    /**
     * <code>repeated int32 data = 1;</code>
     * @return A list containing the data.
     */
    public java.util.List<java.lang.Integer>
        getDataList() {
      return ((bitField0_ & 0x00000001) != 0) ?
               java.util.Collections.unmodifiableList(data_) : data_;
    }
    /**
     * <code>repeated int32 data = 1;</code>
     * @return The count of data.
     */
    public int getDataCount() {
      return data_.size();
    }
    /**
     * <code>repeated int32 data = 1;</code>
     * @param index The index of the element to return.
     * @return The data at the given index.
     */
    public int getData(int index) {
      return data_.getInt(index);
    }
    /**
     * <code>repeated int32 data = 1;</code>
     * @param index The index to set the value at.
     * @param value The data to set.
     * @return This builder for chaining.
     */
    public Builder setData(
        int index, int value) {
      ensureDataIsMutable();
      data_.setInt(index, value);
      onChanged();
      return this;
    }
    /**
     * <code>repeated int32 data = 1;</code>
     * @param value The data to add.
     * @return This builder for chaining.
     */
    public Builder addData(int value) {
      ensureDataIsMutable();
      data_.addInt(value);
      onChanged();
      return this;
    }
    /**
     * <code>repeated int32 data = 1;</code>
     * @param values The data to add.
     * @return This builder for chaining.
     */
    public Builder addAllData(
        java.lang.Iterable<? extends java.lang.Integer> values) {
      ensureDataIsMutable();
      com.google.protobuf.AbstractMessageLite.Builder.addAll(
          values, data_);
      onChanged();
      return this;
    }
    /**
     * <code>repeated int32 data = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearData() {
      data_ = emptyIntList();
      bitField0_ = (bitField0_ & ~0x00000001);
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


    // @@protoc_insertion_point(builder_scope:NewList)
  }

  // @@protoc_insertion_point(class_scope:NewList)
  private static final com.android.build.gradle.internal.cxx.logging.NewList DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.android.build.gradle.internal.cxx.logging.NewList();
  }

  public static com.android.build.gradle.internal.cxx.logging.NewList getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<NewList>
      PARSER = new com.google.protobuf.AbstractParser<NewList>() {
    @java.lang.Override
    public NewList parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new NewList(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<NewList> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<NewList> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.android.build.gradle.internal.cxx.logging.NewList getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

