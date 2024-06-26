// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * <pre>
 * The task identifier used in build attribution
 * </pre>
 *
 * Protobuf type {@code android_studio.BuildAttribuitionTaskIdentifier}
 */
public final class BuildAttribuitionTaskIdentifier extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.BuildAttribuitionTaskIdentifier)
    BuildAttribuitionTaskIdentifierOrBuilder {
private static final long serialVersionUID = 0L;
  // Use BuildAttribuitionTaskIdentifier.newBuilder() to construct.
  private BuildAttribuitionTaskIdentifier(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private BuildAttribuitionTaskIdentifier() {
    taskClassName_ = "";
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new BuildAttribuitionTaskIdentifier();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private BuildAttribuitionTaskIdentifier(
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
            taskClassName_ = bs;
            break;
          }
          case 18: {
            com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.Builder subBuilder = null;
            if (((bitField0_ & 0x00000002) != 0)) {
              subBuilder = originPlugin_.toBuilder();
            }
            originPlugin_ = input.readMessage(com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.PARSER, extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(originPlugin_);
              originPlugin_ = subBuilder.buildPartial();
            }
            bitField0_ |= 0x00000002;
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
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_BuildAttribuitionTaskIdentifier_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_BuildAttribuitionTaskIdentifier_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier.class, com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier.Builder.class);
  }

  private int bitField0_;
  public static final int TASK_CLASS_NAME_FIELD_NUMBER = 1;
  private volatile java.lang.Object taskClassName_;
  /**
   * <pre>
   * The class name of the task
   * ex: MergeResources, JavaPreCompileTask
   * </pre>
   *
   * <code>optional string task_class_name = 1;</code>
   * @return Whether the taskClassName field is set.
   */
  @java.lang.Override
  public boolean hasTaskClassName() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <pre>
   * The class name of the task
   * ex: MergeResources, JavaPreCompileTask
   * </pre>
   *
   * <code>optional string task_class_name = 1;</code>
   * @return The taskClassName.
   */
  @java.lang.Override
  public java.lang.String getTaskClassName() {
    java.lang.Object ref = taskClassName_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      if (bs.isValidUtf8()) {
        taskClassName_ = s;
      }
      return s;
    }
  }
  /**
   * <pre>
   * The class name of the task
   * ex: MergeResources, JavaPreCompileTask
   * </pre>
   *
   * <code>optional string task_class_name = 1;</code>
   * @return The bytes for taskClassName.
   */
  @java.lang.Override
  public com.google.protobuf.ByteString
      getTaskClassNameBytes() {
    java.lang.Object ref = taskClassName_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      taskClassName_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int ORIGIN_PLUGIN_FIELD_NUMBER = 2;
  private com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier originPlugin_;
  /**
   * <pre>
   * The plugin that registered this task
   * </pre>
   *
   * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
   * @return Whether the originPlugin field is set.
   */
  @java.lang.Override
  public boolean hasOriginPlugin() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <pre>
   * The plugin that registered this task
   * </pre>
   *
   * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
   * @return The originPlugin.
   */
  @java.lang.Override
  public com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier getOriginPlugin() {
    return originPlugin_ == null ? com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.getDefaultInstance() : originPlugin_;
  }
  /**
   * <pre>
   * The plugin that registered this task
   * </pre>
   *
   * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
   */
  @java.lang.Override
  public com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifierOrBuilder getOriginPluginOrBuilder() {
    return originPlugin_ == null ? com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.getDefaultInstance() : originPlugin_;
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
      com.google.protobuf.GeneratedMessageV3.writeString(output, 1, taskClassName_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeMessage(2, getOriginPlugin());
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (((bitField0_ & 0x00000001) != 0)) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, taskClassName_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(2, getOriginPlugin());
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
    if (!(obj instanceof com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier other = (com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier) obj;

    if (hasTaskClassName() != other.hasTaskClassName()) return false;
    if (hasTaskClassName()) {
      if (!getTaskClassName()
          .equals(other.getTaskClassName())) return false;
    }
    if (hasOriginPlugin() != other.hasOriginPlugin()) return false;
    if (hasOriginPlugin()) {
      if (!getOriginPlugin()
          .equals(other.getOriginPlugin())) return false;
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
    if (hasTaskClassName()) {
      hash = (37 * hash) + TASK_CLASS_NAME_FIELD_NUMBER;
      hash = (53 * hash) + getTaskClassName().hashCode();
    }
    if (hasOriginPlugin()) {
      hash = (37 * hash) + ORIGIN_PLUGIN_FIELD_NUMBER;
      hash = (53 * hash) + getOriginPlugin().hashCode();
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier prototype) {
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
   * The task identifier used in build attribution
   * </pre>
   *
   * Protobuf type {@code android_studio.BuildAttribuitionTaskIdentifier}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.BuildAttribuitionTaskIdentifier)
      com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifierOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_BuildAttribuitionTaskIdentifier_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_BuildAttribuitionTaskIdentifier_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier.class, com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier.newBuilder()
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
        getOriginPluginFieldBuilder();
      }
    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      taskClassName_ = "";
      bitField0_ = (bitField0_ & ~0x00000001);
      if (originPluginBuilder_ == null) {
        originPlugin_ = null;
      } else {
        originPluginBuilder_.clear();
      }
      bitField0_ = (bitField0_ & ~0x00000002);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_BuildAttribuitionTaskIdentifier_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier build() {
      com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier buildPartial() {
      com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier result = new com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier(this);
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        to_bitField0_ |= 0x00000001;
      }
      result.taskClassName_ = taskClassName_;
      if (((from_bitField0_ & 0x00000002) != 0)) {
        if (originPluginBuilder_ == null) {
          result.originPlugin_ = originPlugin_;
        } else {
          result.originPlugin_ = originPluginBuilder_.build();
        }
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
      if (other instanceof com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier) {
        return mergeFrom((com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier other) {
      if (other == com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier.getDefaultInstance()) return this;
      if (other.hasTaskClassName()) {
        bitField0_ |= 0x00000001;
        taskClassName_ = other.taskClassName_;
        onChanged();
      }
      if (other.hasOriginPlugin()) {
        mergeOriginPlugin(other.getOriginPlugin());
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
      com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private java.lang.Object taskClassName_ = "";
    /**
     * <pre>
     * The class name of the task
     * ex: MergeResources, JavaPreCompileTask
     * </pre>
     *
     * <code>optional string task_class_name = 1;</code>
     * @return Whether the taskClassName field is set.
     */
    public boolean hasTaskClassName() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <pre>
     * The class name of the task
     * ex: MergeResources, JavaPreCompileTask
     * </pre>
     *
     * <code>optional string task_class_name = 1;</code>
     * @return The taskClassName.
     */
    public java.lang.String getTaskClassName() {
      java.lang.Object ref = taskClassName_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          taskClassName_ = s;
        }
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <pre>
     * The class name of the task
     * ex: MergeResources, JavaPreCompileTask
     * </pre>
     *
     * <code>optional string task_class_name = 1;</code>
     * @return The bytes for taskClassName.
     */
    public com.google.protobuf.ByteString
        getTaskClassNameBytes() {
      java.lang.Object ref = taskClassName_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        taskClassName_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <pre>
     * The class name of the task
     * ex: MergeResources, JavaPreCompileTask
     * </pre>
     *
     * <code>optional string task_class_name = 1;</code>
     * @param value The taskClassName to set.
     * @return This builder for chaining.
     */
    public Builder setTaskClassName(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000001;
      taskClassName_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * The class name of the task
     * ex: MergeResources, JavaPreCompileTask
     * </pre>
     *
     * <code>optional string task_class_name = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearTaskClassName() {
      bitField0_ = (bitField0_ & ~0x00000001);
      taskClassName_ = getDefaultInstance().getTaskClassName();
      onChanged();
      return this;
    }
    /**
     * <pre>
     * The class name of the task
     * ex: MergeResources, JavaPreCompileTask
     * </pre>
     *
     * <code>optional string task_class_name = 1;</code>
     * @param value The bytes for taskClassName to set.
     * @return This builder for chaining.
     */
    public Builder setTaskClassNameBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000001;
      taskClassName_ = value;
      onChanged();
      return this;
    }

    private com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier originPlugin_;
    private com.google.protobuf.SingleFieldBuilderV3<
        com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier, com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.Builder, com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifierOrBuilder> originPluginBuilder_;
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     * @return Whether the originPlugin field is set.
     */
    public boolean hasOriginPlugin() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     * @return The originPlugin.
     */
    public com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier getOriginPlugin() {
      if (originPluginBuilder_ == null) {
        return originPlugin_ == null ? com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.getDefaultInstance() : originPlugin_;
      } else {
        return originPluginBuilder_.getMessage();
      }
    }
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     */
    public Builder setOriginPlugin(com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier value) {
      if (originPluginBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        originPlugin_ = value;
        onChanged();
      } else {
        originPluginBuilder_.setMessage(value);
      }
      bitField0_ |= 0x00000002;
      return this;
    }
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     */
    public Builder setOriginPlugin(
        com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.Builder builderForValue) {
      if (originPluginBuilder_ == null) {
        originPlugin_ = builderForValue.build();
        onChanged();
      } else {
        originPluginBuilder_.setMessage(builderForValue.build());
      }
      bitField0_ |= 0x00000002;
      return this;
    }
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     */
    public Builder mergeOriginPlugin(com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier value) {
      if (originPluginBuilder_ == null) {
        if (((bitField0_ & 0x00000002) != 0) &&
            originPlugin_ != null &&
            originPlugin_ != com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.getDefaultInstance()) {
          originPlugin_ =
            com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.newBuilder(originPlugin_).mergeFrom(value).buildPartial();
        } else {
          originPlugin_ = value;
        }
        onChanged();
      } else {
        originPluginBuilder_.mergeFrom(value);
      }
      bitField0_ |= 0x00000002;
      return this;
    }
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     */
    public Builder clearOriginPlugin() {
      if (originPluginBuilder_ == null) {
        originPlugin_ = null;
        onChanged();
      } else {
        originPluginBuilder_.clear();
      }
      bitField0_ = (bitField0_ & ~0x00000002);
      return this;
    }
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     */
    public com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.Builder getOriginPluginBuilder() {
      bitField0_ |= 0x00000002;
      onChanged();
      return getOriginPluginFieldBuilder().getBuilder();
    }
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     */
    public com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifierOrBuilder getOriginPluginOrBuilder() {
      if (originPluginBuilder_ != null) {
        return originPluginBuilder_.getMessageOrBuilder();
      } else {
        return originPlugin_ == null ?
            com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.getDefaultInstance() : originPlugin_;
      }
    }
    /**
     * <pre>
     * The plugin that registered this task
     * </pre>
     *
     * <code>optional .android_studio.BuildAttributionPluginIdentifier origin_plugin = 2;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<
        com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier, com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.Builder, com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifierOrBuilder> 
        getOriginPluginFieldBuilder() {
      if (originPluginBuilder_ == null) {
        originPluginBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<
            com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier, com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier.Builder, com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifierOrBuilder>(
                getOriginPlugin(),
                getParentForChildren(),
                isClean());
        originPlugin_ = null;
      }
      return originPluginBuilder_;
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


    // @@protoc_insertion_point(builder_scope:android_studio.BuildAttribuitionTaskIdentifier)
  }

  // @@protoc_insertion_point(class_scope:android_studio.BuildAttribuitionTaskIdentifier)
  private static final com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier();
  }

  public static com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<BuildAttribuitionTaskIdentifier>
      PARSER = new com.google.protobuf.AbstractParser<BuildAttribuitionTaskIdentifier>() {
    @java.lang.Override
    public BuildAttribuitionTaskIdentifier parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new BuildAttribuitionTaskIdentifier(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<BuildAttribuitionTaskIdentifier> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<BuildAttribuitionTaskIdentifier> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

