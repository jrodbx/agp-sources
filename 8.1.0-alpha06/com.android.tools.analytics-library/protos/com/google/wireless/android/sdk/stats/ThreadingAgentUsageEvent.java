// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * Protobuf type {@code android_studio.ThreadingAgentUsageEvent}
 */
public final class ThreadingAgentUsageEvent extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.ThreadingAgentUsageEvent)
    ThreadingAgentUsageEventOrBuilder {
private static final long serialVersionUID = 0L;
  // Use ThreadingAgentUsageEvent.newBuilder() to construct.
  private ThreadingAgentUsageEvent(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private ThreadingAgentUsageEvent() {
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new ThreadingAgentUsageEvent();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private ThreadingAgentUsageEvent(
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
            verifyUiThreadCount_ = input.readInt64();
            break;
          }
          case 16: {
            bitField0_ |= 0x00000002;
            verifyWorkerThreadCount_ = input.readInt64();
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
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_ThreadingAgentUsageEvent_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_ThreadingAgentUsageEvent_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent.class, com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent.Builder.class);
  }

  private int bitField0_;
  public static final int VERIFY_UI_THREAD_COUNT_FIELD_NUMBER = 1;
  private long verifyUiThreadCount_;
  /**
   * <pre>
   * The number of times the threading agent verified that a method was invoked
   * on a UI thread
   * </pre>
   *
   * <code>optional int64 verify_ui_thread_count = 1;</code>
   * @return Whether the verifyUiThreadCount field is set.
   */
  @java.lang.Override
  public boolean hasVerifyUiThreadCount() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <pre>
   * The number of times the threading agent verified that a method was invoked
   * on a UI thread
   * </pre>
   *
   * <code>optional int64 verify_ui_thread_count = 1;</code>
   * @return The verifyUiThreadCount.
   */
  @java.lang.Override
  public long getVerifyUiThreadCount() {
    return verifyUiThreadCount_;
  }

  public static final int VERIFY_WORKER_THREAD_COUNT_FIELD_NUMBER = 2;
  private long verifyWorkerThreadCount_;
  /**
   * <pre>
   * The number of times the threading agent verified that a method was invoked
   * on a worker thread
   * </pre>
   *
   * <code>optional int64 verify_worker_thread_count = 2;</code>
   * @return Whether the verifyWorkerThreadCount field is set.
   */
  @java.lang.Override
  public boolean hasVerifyWorkerThreadCount() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <pre>
   * The number of times the threading agent verified that a method was invoked
   * on a worker thread
   * </pre>
   *
   * <code>optional int64 verify_worker_thread_count = 2;</code>
   * @return The verifyWorkerThreadCount.
   */
  @java.lang.Override
  public long getVerifyWorkerThreadCount() {
    return verifyWorkerThreadCount_;
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
      output.writeInt64(1, verifyUiThreadCount_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeInt64(2, verifyWorkerThreadCount_);
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
        .computeInt64Size(1, verifyUiThreadCount_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(2, verifyWorkerThreadCount_);
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
    if (!(obj instanceof com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent other = (com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent) obj;

    if (hasVerifyUiThreadCount() != other.hasVerifyUiThreadCount()) return false;
    if (hasVerifyUiThreadCount()) {
      if (getVerifyUiThreadCount()
          != other.getVerifyUiThreadCount()) return false;
    }
    if (hasVerifyWorkerThreadCount() != other.hasVerifyWorkerThreadCount()) return false;
    if (hasVerifyWorkerThreadCount()) {
      if (getVerifyWorkerThreadCount()
          != other.getVerifyWorkerThreadCount()) return false;
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
    if (hasVerifyUiThreadCount()) {
      hash = (37 * hash) + VERIFY_UI_THREAD_COUNT_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getVerifyUiThreadCount());
    }
    if (hasVerifyWorkerThreadCount()) {
      hash = (37 * hash) + VERIFY_WORKER_THREAD_COUNT_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getVerifyWorkerThreadCount());
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent prototype) {
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
   * Protobuf type {@code android_studio.ThreadingAgentUsageEvent}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.ThreadingAgentUsageEvent)
      com.google.wireless.android.sdk.stats.ThreadingAgentUsageEventOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_ThreadingAgentUsageEvent_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_ThreadingAgentUsageEvent_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent.class, com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent.newBuilder()
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
      verifyUiThreadCount_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000001);
      verifyWorkerThreadCount_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000002);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_ThreadingAgentUsageEvent_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent build() {
      com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent buildPartial() {
      com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent result = new com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent(this);
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        result.verifyUiThreadCount_ = verifyUiThreadCount_;
        to_bitField0_ |= 0x00000001;
      }
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.verifyWorkerThreadCount_ = verifyWorkerThreadCount_;
        to_bitField0_ |= 0x00000002;
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
      if (other instanceof com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent) {
        return mergeFrom((com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent other) {
      if (other == com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent.getDefaultInstance()) return this;
      if (other.hasVerifyUiThreadCount()) {
        setVerifyUiThreadCount(other.getVerifyUiThreadCount());
      }
      if (other.hasVerifyWorkerThreadCount()) {
        setVerifyWorkerThreadCount(other.getVerifyWorkerThreadCount());
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
      com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private long verifyUiThreadCount_ ;
    /**
     * <pre>
     * The number of times the threading agent verified that a method was invoked
     * on a UI thread
     * </pre>
     *
     * <code>optional int64 verify_ui_thread_count = 1;</code>
     * @return Whether the verifyUiThreadCount field is set.
     */
    @java.lang.Override
    public boolean hasVerifyUiThreadCount() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <pre>
     * The number of times the threading agent verified that a method was invoked
     * on a UI thread
     * </pre>
     *
     * <code>optional int64 verify_ui_thread_count = 1;</code>
     * @return The verifyUiThreadCount.
     */
    @java.lang.Override
    public long getVerifyUiThreadCount() {
      return verifyUiThreadCount_;
    }
    /**
     * <pre>
     * The number of times the threading agent verified that a method was invoked
     * on a UI thread
     * </pre>
     *
     * <code>optional int64 verify_ui_thread_count = 1;</code>
     * @param value The verifyUiThreadCount to set.
     * @return This builder for chaining.
     */
    public Builder setVerifyUiThreadCount(long value) {
      bitField0_ |= 0x00000001;
      verifyUiThreadCount_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * The number of times the threading agent verified that a method was invoked
     * on a UI thread
     * </pre>
     *
     * <code>optional int64 verify_ui_thread_count = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearVerifyUiThreadCount() {
      bitField0_ = (bitField0_ & ~0x00000001);
      verifyUiThreadCount_ = 0L;
      onChanged();
      return this;
    }

    private long verifyWorkerThreadCount_ ;
    /**
     * <pre>
     * The number of times the threading agent verified that a method was invoked
     * on a worker thread
     * </pre>
     *
     * <code>optional int64 verify_worker_thread_count = 2;</code>
     * @return Whether the verifyWorkerThreadCount field is set.
     */
    @java.lang.Override
    public boolean hasVerifyWorkerThreadCount() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <pre>
     * The number of times the threading agent verified that a method was invoked
     * on a worker thread
     * </pre>
     *
     * <code>optional int64 verify_worker_thread_count = 2;</code>
     * @return The verifyWorkerThreadCount.
     */
    @java.lang.Override
    public long getVerifyWorkerThreadCount() {
      return verifyWorkerThreadCount_;
    }
    /**
     * <pre>
     * The number of times the threading agent verified that a method was invoked
     * on a worker thread
     * </pre>
     *
     * <code>optional int64 verify_worker_thread_count = 2;</code>
     * @param value The verifyWorkerThreadCount to set.
     * @return This builder for chaining.
     */
    public Builder setVerifyWorkerThreadCount(long value) {
      bitField0_ |= 0x00000002;
      verifyWorkerThreadCount_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * The number of times the threading agent verified that a method was invoked
     * on a worker thread
     * </pre>
     *
     * <code>optional int64 verify_worker_thread_count = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearVerifyWorkerThreadCount() {
      bitField0_ = (bitField0_ & ~0x00000002);
      verifyWorkerThreadCount_ = 0L;
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


    // @@protoc_insertion_point(builder_scope:android_studio.ThreadingAgentUsageEvent)
  }

  // @@protoc_insertion_point(class_scope:android_studio.ThreadingAgentUsageEvent)
  private static final com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent();
  }

  public static com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<ThreadingAgentUsageEvent>
      PARSER = new com.google.protobuf.AbstractParser<ThreadingAgentUsageEvent>() {
    @java.lang.Override
    public ThreadingAgentUsageEvent parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new ThreadingAgentUsageEvent(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<ThreadingAgentUsageEvent> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<ThreadingAgentUsageEvent> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

