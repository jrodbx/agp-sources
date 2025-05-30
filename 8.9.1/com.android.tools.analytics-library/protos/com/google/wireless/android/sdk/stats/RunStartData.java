// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * <pre>
 * Data collected by IntelliJ when a run is started
 * </pre>
 *
 * Protobuf type {@code android_studio.RunStartData}
 */
public final class RunStartData extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.RunStartData)
    RunStartDataOrBuilder {
private static final long serialVersionUID = 0L;
  // Use RunStartData.newBuilder() to construct.
  private RunStartData(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private RunStartData() {
    executor_ = "";
    runConfiguration_ = "";
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new RunStartData();
  }

  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_RunStartData_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_RunStartData_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.RunStartData.class, com.google.wireless.android.sdk.stats.RunStartData.Builder.class);
  }

  private int bitField0_;
  public static final int IDE_ACTIVITY_ID_FIELD_NUMBER = 1;
  private int ideActivityId_ = 0;
  /**
   * <pre>
   * Unique id for activity. This number is generated sequentially
   * and exists only across the lifetime of the process.
   * </pre>
   *
   * <code>optional int32 ide_activity_id = 1;</code>
   * @return Whether the ideActivityId field is set.
   */
  @java.lang.Override
  public boolean hasIdeActivityId() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <pre>
   * Unique id for activity. This number is generated sequentially
   * and exists only across the lifetime of the process.
   * </pre>
   *
   * <code>optional int32 ide_activity_id = 1;</code>
   * @return The ideActivityId.
   */
  @java.lang.Override
  public int getIdeActivityId() {
    return ideActivityId_;
  }

  public static final int EXECUTOR_FIELD_NUMBER = 2;
  @SuppressWarnings("serial")
  private volatile java.lang.Object executor_ = "";
  /**
   * <pre>
   * Type of launch performed, i.e. Run, Debug, etc.
   * </pre>
   *
   * <code>optional string executor = 2;</code>
   * @return Whether the executor field is set.
   */
  @java.lang.Override
  public boolean hasExecutor() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <pre>
   * Type of launch performed, i.e. Run, Debug, etc.
   * </pre>
   *
   * <code>optional string executor = 2;</code>
   * @return The executor.
   */
  @java.lang.Override
  public java.lang.String getExecutor() {
    java.lang.Object ref = executor_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      if (bs.isValidUtf8()) {
        executor_ = s;
      }
      return s;
    }
  }
  /**
   * <pre>
   * Type of launch performed, i.e. Run, Debug, etc.
   * </pre>
   *
   * <code>optional string executor = 2;</code>
   * @return The bytes for executor.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString
      getExecutorBytes() {
    java.lang.Object ref = executor_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      executor_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int RUN_CONFIGURATION_FIELD_NUMBER = 3;
  @SuppressWarnings("serial")
  private volatile java.lang.Object runConfiguration_ = "";
  /**
   * <pre>
   * Type of run configuration, i.e. AndroidRunConfigurationType
   * </pre>
   *
   * <code>optional string run_configuration = 3;</code>
   * @return Whether the runConfiguration field is set.
   */
  @java.lang.Override
  public boolean hasRunConfiguration() {
    return ((bitField0_ & 0x00000004) != 0);
  }
  /**
   * <pre>
   * Type of run configuration, i.e. AndroidRunConfigurationType
   * </pre>
   *
   * <code>optional string run_configuration = 3;</code>
   * @return The runConfiguration.
   */
  @java.lang.Override
  public java.lang.String getRunConfiguration() {
    java.lang.Object ref = runConfiguration_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      if (bs.isValidUtf8()) {
        runConfiguration_ = s;
      }
      return s;
    }
  }
  /**
   * <pre>
   * Type of run configuration, i.e. AndroidRunConfigurationType
   * </pre>
   *
   * <code>optional string run_configuration = 3;</code>
   * @return The bytes for runConfiguration.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString
      getRunConfigurationBytes() {
    java.lang.Object ref = runConfiguration_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      runConfiguration_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
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
      output.writeInt32(1, ideActivityId_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 2, executor_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 3, runConfiguration_);
    }
    getUnknownFields().writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (((bitField0_ & 0x00000001) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt32Size(1, ideActivityId_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, executor_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(3, runConfiguration_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof com.google.wireless.android.sdk.stats.RunStartData)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.RunStartData other = (com.google.wireless.android.sdk.stats.RunStartData) obj;

    if (hasIdeActivityId() != other.hasIdeActivityId()) return false;
    if (hasIdeActivityId()) {
      if (getIdeActivityId()
          != other.getIdeActivityId()) return false;
    }
    if (hasExecutor() != other.hasExecutor()) return false;
    if (hasExecutor()) {
      if (!getExecutor()
          .equals(other.getExecutor())) return false;
    }
    if (hasRunConfiguration() != other.hasRunConfiguration()) return false;
    if (hasRunConfiguration()) {
      if (!getRunConfiguration()
          .equals(other.getRunConfiguration())) return false;
    }
    if (!getUnknownFields().equals(other.getUnknownFields())) return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    if (hasIdeActivityId()) {
      hash = (37 * hash) + IDE_ACTIVITY_ID_FIELD_NUMBER;
      hash = (53 * hash) + getIdeActivityId();
    }
    if (hasExecutor()) {
      hash = (37 * hash) + EXECUTOR_FIELD_NUMBER;
      hash = (53 * hash) + getExecutor().hashCode();
    }
    if (hasRunConfiguration()) {
      hash = (37 * hash) + RUN_CONFIGURATION_FIELD_NUMBER;
      hash = (53 * hash) + getRunConfiguration().hashCode();
    }
    hash = (29 * hash) + getUnknownFields().hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.RunStartData parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.RunStartData prototype) {
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
   * Data collected by IntelliJ when a run is started
   * </pre>
   *
   * Protobuf type {@code android_studio.RunStartData}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.RunStartData)
      com.google.wireless.android.sdk.stats.RunStartDataOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_RunStartData_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_RunStartData_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.RunStartData.class, com.google.wireless.android.sdk.stats.RunStartData.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.RunStartData.newBuilder()
    private Builder() {

    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);

    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      bitField0_ = 0;
      ideActivityId_ = 0;
      executor_ = "";
      runConfiguration_ = "";
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_RunStartData_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.RunStartData getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.RunStartData.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.RunStartData build() {
      com.google.wireless.android.sdk.stats.RunStartData result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.RunStartData buildPartial() {
      com.google.wireless.android.sdk.stats.RunStartData result = new com.google.wireless.android.sdk.stats.RunStartData(this);
      if (bitField0_ != 0) { buildPartial0(result); }
      onBuilt();
      return result;
    }

    private void buildPartial0(com.google.wireless.android.sdk.stats.RunStartData result) {
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        result.ideActivityId_ = ideActivityId_;
        to_bitField0_ |= 0x00000001;
      }
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.executor_ = executor_;
        to_bitField0_ |= 0x00000002;
      }
      if (((from_bitField0_ & 0x00000004) != 0)) {
        result.runConfiguration_ = runConfiguration_;
        to_bitField0_ |= 0x00000004;
      }
      result.bitField0_ |= to_bitField0_;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.google.wireless.android.sdk.stats.RunStartData) {
        return mergeFrom((com.google.wireless.android.sdk.stats.RunStartData)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.RunStartData other) {
      if (other == com.google.wireless.android.sdk.stats.RunStartData.getDefaultInstance()) return this;
      if (other.hasIdeActivityId()) {
        setIdeActivityId(other.getIdeActivityId());
      }
      if (other.hasExecutor()) {
        executor_ = other.executor_;
        bitField0_ |= 0x00000002;
        onChanged();
      }
      if (other.hasRunConfiguration()) {
        runConfiguration_ = other.runConfiguration_;
        bitField0_ |= 0x00000004;
        onChanged();
      }
      this.mergeUnknownFields(other.getUnknownFields());
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
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 8: {
              ideActivityId_ = input.readInt32();
              bitField0_ |= 0x00000001;
              break;
            } // case 8
            case 18: {
              executor_ = input.readBytes();
              bitField0_ |= 0x00000002;
              break;
            } // case 18
            case 26: {
              runConfiguration_ = input.readBytes();
              bitField0_ |= 0x00000004;
              break;
            } // case 26
            default: {
              if (!super.parseUnknownField(input, extensionRegistry, tag)) {
                done = true; // was an endgroup tag
              }
              break;
            } // default:
          } // switch (tag)
        } // while (!done)
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.unwrapIOException();
      } finally {
        onChanged();
      } // finally
      return this;
    }
    private int bitField0_;

    private int ideActivityId_ ;
    /**
     * <pre>
     * Unique id for activity. This number is generated sequentially
     * and exists only across the lifetime of the process.
     * </pre>
     *
     * <code>optional int32 ide_activity_id = 1;</code>
     * @return Whether the ideActivityId field is set.
     */
    @java.lang.Override
    public boolean hasIdeActivityId() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <pre>
     * Unique id for activity. This number is generated sequentially
     * and exists only across the lifetime of the process.
     * </pre>
     *
     * <code>optional int32 ide_activity_id = 1;</code>
     * @return The ideActivityId.
     */
    @java.lang.Override
    public int getIdeActivityId() {
      return ideActivityId_;
    }
    /**
     * <pre>
     * Unique id for activity. This number is generated sequentially
     * and exists only across the lifetime of the process.
     * </pre>
     *
     * <code>optional int32 ide_activity_id = 1;</code>
     * @param value The ideActivityId to set.
     * @return This builder for chaining.
     */
    public Builder setIdeActivityId(int value) {

      ideActivityId_ = value;
      bitField0_ |= 0x00000001;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Unique id for activity. This number is generated sequentially
     * and exists only across the lifetime of the process.
     * </pre>
     *
     * <code>optional int32 ide_activity_id = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearIdeActivityId() {
      bitField0_ = (bitField0_ & ~0x00000001);
      ideActivityId_ = 0;
      onChanged();
      return this;
    }

    private java.lang.Object executor_ = "";
    /**
     * <pre>
     * Type of launch performed, i.e. Run, Debug, etc.
     * </pre>
     *
     * <code>optional string executor = 2;</code>
     * @return Whether the executor field is set.
     */
    public boolean hasExecutor() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <pre>
     * Type of launch performed, i.e. Run, Debug, etc.
     * </pre>
     *
     * <code>optional string executor = 2;</code>
     * @return The executor.
     */
    public java.lang.String getExecutor() {
      java.lang.Object ref = executor_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          executor_ = s;
        }
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <pre>
     * Type of launch performed, i.e. Run, Debug, etc.
     * </pre>
     *
     * <code>optional string executor = 2;</code>
     * @return The bytes for executor.
     */
    public com.google.protobuf.ByteString
        getExecutorBytes() {
      java.lang.Object ref = executor_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        executor_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <pre>
     * Type of launch performed, i.e. Run, Debug, etc.
     * </pre>
     *
     * <code>optional string executor = 2;</code>
     * @param value The executor to set.
     * @return This builder for chaining.
     */
    public Builder setExecutor(
        java.lang.String value) {
      if (value == null) { throw new NullPointerException(); }
      executor_ = value;
      bitField0_ |= 0x00000002;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Type of launch performed, i.e. Run, Debug, etc.
     * </pre>
     *
     * <code>optional string executor = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearExecutor() {
      executor_ = getDefaultInstance().getExecutor();
      bitField0_ = (bitField0_ & ~0x00000002);
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Type of launch performed, i.e. Run, Debug, etc.
     * </pre>
     *
     * <code>optional string executor = 2;</code>
     * @param value The bytes for executor to set.
     * @return This builder for chaining.
     */
    public Builder setExecutorBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) { throw new NullPointerException(); }
      executor_ = value;
      bitField0_ |= 0x00000002;
      onChanged();
      return this;
    }

    private java.lang.Object runConfiguration_ = "";
    /**
     * <pre>
     * Type of run configuration, i.e. AndroidRunConfigurationType
     * </pre>
     *
     * <code>optional string run_configuration = 3;</code>
     * @return Whether the runConfiguration field is set.
     */
    public boolean hasRunConfiguration() {
      return ((bitField0_ & 0x00000004) != 0);
    }
    /**
     * <pre>
     * Type of run configuration, i.e. AndroidRunConfigurationType
     * </pre>
     *
     * <code>optional string run_configuration = 3;</code>
     * @return The runConfiguration.
     */
    public java.lang.String getRunConfiguration() {
      java.lang.Object ref = runConfiguration_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          runConfiguration_ = s;
        }
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <pre>
     * Type of run configuration, i.e. AndroidRunConfigurationType
     * </pre>
     *
     * <code>optional string run_configuration = 3;</code>
     * @return The bytes for runConfiguration.
     */
    public com.google.protobuf.ByteString
        getRunConfigurationBytes() {
      java.lang.Object ref = runConfiguration_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        runConfiguration_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <pre>
     * Type of run configuration, i.e. AndroidRunConfigurationType
     * </pre>
     *
     * <code>optional string run_configuration = 3;</code>
     * @param value The runConfiguration to set.
     * @return This builder for chaining.
     */
    public Builder setRunConfiguration(
        java.lang.String value) {
      if (value == null) { throw new NullPointerException(); }
      runConfiguration_ = value;
      bitField0_ |= 0x00000004;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Type of run configuration, i.e. AndroidRunConfigurationType
     * </pre>
     *
     * <code>optional string run_configuration = 3;</code>
     * @return This builder for chaining.
     */
    public Builder clearRunConfiguration() {
      runConfiguration_ = getDefaultInstance().getRunConfiguration();
      bitField0_ = (bitField0_ & ~0x00000004);
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Type of run configuration, i.e. AndroidRunConfigurationType
     * </pre>
     *
     * <code>optional string run_configuration = 3;</code>
     * @param value The bytes for runConfiguration to set.
     * @return This builder for chaining.
     */
    public Builder setRunConfigurationBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) { throw new NullPointerException(); }
      runConfiguration_ = value;
      bitField0_ |= 0x00000004;
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


    // @@protoc_insertion_point(builder_scope:android_studio.RunStartData)
  }

  // @@protoc_insertion_point(class_scope:android_studio.RunStartData)
  private static final com.google.wireless.android.sdk.stats.RunStartData DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.RunStartData();
  }

  public static com.google.wireless.android.sdk.stats.RunStartData getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<RunStartData>
      PARSER = new com.google.protobuf.AbstractParser<RunStartData>() {
    @java.lang.Override
    public RunStartData parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      Builder builder = newBuilder();
      try {
        builder.mergeFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(builder.buildPartial());
      } catch (com.google.protobuf.UninitializedMessageException e) {
        throw e.asInvalidProtocolBufferException().setUnfinishedMessage(builder.buildPartial());
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(e)
            .setUnfinishedMessage(builder.buildPartial());
      }
      return builder.buildPartial();
    }
  };

  public static com.google.protobuf.Parser<RunStartData> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<RunStartData> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.RunStartData getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

