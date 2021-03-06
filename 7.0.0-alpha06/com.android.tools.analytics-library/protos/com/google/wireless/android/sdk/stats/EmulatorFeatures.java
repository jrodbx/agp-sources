// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * <pre>
 * Set of emulator feature flags to report ones used during current session.
 * </pre>
 *
 * Protobuf type {@code android_studio.EmulatorFeatures}
 */
public  final class EmulatorFeatures extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.EmulatorFeatures)
    EmulatorFeaturesOrBuilder {
private static final long serialVersionUID = 0L;
  // Use EmulatorFeatures.newBuilder() to construct.
  private EmulatorFeatures(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private EmulatorFeatures() {
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new EmulatorFeatures();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private EmulatorFeatures(
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
            gps_ = input.readBool();
            break;
          }
          case 16: {
            bitField0_ |= 0x00000002;
            sensors_ = input.readBool();
            break;
          }
          case 24: {
            bitField0_ |= 0x00000004;
            virtualsceneConfig_ = input.readBool();
            break;
          }
          case 32: {
            bitField0_ |= 0x00000008;
            containerLaunch_ = input.readBool();
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
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EmulatorFeatures_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EmulatorFeatures_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.EmulatorFeatures.class, com.google.wireless.android.sdk.stats.EmulatorFeatures.Builder.class);
  }

  private int bitField0_;
  public static final int GPS_FIELD_NUMBER = 1;
  private boolean gps_;
  /**
   * <code>optional bool gps = 1;</code>
   * @return Whether the gps field is set.
   */
  public boolean hasGps() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <code>optional bool gps = 1;</code>
   * @return The gps.
   */
  public boolean getGps() {
    return gps_;
  }

  public static final int SENSORS_FIELD_NUMBER = 2;
  private boolean sensors_;
  /**
   * <code>optional bool sensors = 2;</code>
   * @return Whether the sensors field is set.
   */
  public boolean hasSensors() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <code>optional bool sensors = 2;</code>
   * @return The sensors.
   */
  public boolean getSensors() {
    return sensors_;
  }

  public static final int VIRTUALSCENE_CONFIG_FIELD_NUMBER = 3;
  private boolean virtualsceneConfig_;
  /**
   * <code>optional bool virtualscene_config = 3;</code>
   * @return Whether the virtualsceneConfig field is set.
   */
  public boolean hasVirtualsceneConfig() {
    return ((bitField0_ & 0x00000004) != 0);
  }
  /**
   * <code>optional bool virtualscene_config = 3;</code>
   * @return The virtualsceneConfig.
   */
  public boolean getVirtualsceneConfig() {
    return virtualsceneConfig_;
  }

  public static final int CONTAINER_LAUNCH_FIELD_NUMBER = 4;
  private boolean containerLaunch_;
  /**
   * <pre>
   * True if the emulator is running standalone.
   * </pre>
   *
   * <code>optional bool container_launch = 4;</code>
   * @return Whether the containerLaunch field is set.
   */
  public boolean hasContainerLaunch() {
    return ((bitField0_ & 0x00000008) != 0);
  }
  /**
   * <pre>
   * True if the emulator is running standalone.
   * </pre>
   *
   * <code>optional bool container_launch = 4;</code>
   * @return The containerLaunch.
   */
  public boolean getContainerLaunch() {
    return containerLaunch_;
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
      output.writeBool(1, gps_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeBool(2, sensors_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      output.writeBool(3, virtualsceneConfig_);
    }
    if (((bitField0_ & 0x00000008) != 0)) {
      output.writeBool(4, containerLaunch_);
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
        .computeBoolSize(1, gps_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeBoolSize(2, sensors_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeBoolSize(3, virtualsceneConfig_);
    }
    if (((bitField0_ & 0x00000008) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeBoolSize(4, containerLaunch_);
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
    if (!(obj instanceof com.google.wireless.android.sdk.stats.EmulatorFeatures)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.EmulatorFeatures other = (com.google.wireless.android.sdk.stats.EmulatorFeatures) obj;

    if (hasGps() != other.hasGps()) return false;
    if (hasGps()) {
      if (getGps()
          != other.getGps()) return false;
    }
    if (hasSensors() != other.hasSensors()) return false;
    if (hasSensors()) {
      if (getSensors()
          != other.getSensors()) return false;
    }
    if (hasVirtualsceneConfig() != other.hasVirtualsceneConfig()) return false;
    if (hasVirtualsceneConfig()) {
      if (getVirtualsceneConfig()
          != other.getVirtualsceneConfig()) return false;
    }
    if (hasContainerLaunch() != other.hasContainerLaunch()) return false;
    if (hasContainerLaunch()) {
      if (getContainerLaunch()
          != other.getContainerLaunch()) return false;
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
    if (hasGps()) {
      hash = (37 * hash) + GPS_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(
          getGps());
    }
    if (hasSensors()) {
      hash = (37 * hash) + SENSORS_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(
          getSensors());
    }
    if (hasVirtualsceneConfig()) {
      hash = (37 * hash) + VIRTUALSCENE_CONFIG_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(
          getVirtualsceneConfig());
    }
    if (hasContainerLaunch()) {
      hash = (37 * hash) + CONTAINER_LAUNCH_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(
          getContainerLaunch());
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.EmulatorFeatures parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.EmulatorFeatures prototype) {
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
   * Set of emulator feature flags to report ones used during current session.
   * </pre>
   *
   * Protobuf type {@code android_studio.EmulatorFeatures}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.EmulatorFeatures)
      com.google.wireless.android.sdk.stats.EmulatorFeaturesOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EmulatorFeatures_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EmulatorFeatures_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.EmulatorFeatures.class, com.google.wireless.android.sdk.stats.EmulatorFeatures.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.EmulatorFeatures.newBuilder()
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
      gps_ = false;
      bitField0_ = (bitField0_ & ~0x00000001);
      sensors_ = false;
      bitField0_ = (bitField0_ & ~0x00000002);
      virtualsceneConfig_ = false;
      bitField0_ = (bitField0_ & ~0x00000004);
      containerLaunch_ = false;
      bitField0_ = (bitField0_ & ~0x00000008);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EmulatorFeatures_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.EmulatorFeatures getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.EmulatorFeatures.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.EmulatorFeatures build() {
      com.google.wireless.android.sdk.stats.EmulatorFeatures result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.EmulatorFeatures buildPartial() {
      com.google.wireless.android.sdk.stats.EmulatorFeatures result = new com.google.wireless.android.sdk.stats.EmulatorFeatures(this);
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        result.gps_ = gps_;
        to_bitField0_ |= 0x00000001;
      }
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.sensors_ = sensors_;
        to_bitField0_ |= 0x00000002;
      }
      if (((from_bitField0_ & 0x00000004) != 0)) {
        result.virtualsceneConfig_ = virtualsceneConfig_;
        to_bitField0_ |= 0x00000004;
      }
      if (((from_bitField0_ & 0x00000008) != 0)) {
        result.containerLaunch_ = containerLaunch_;
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
      if (other instanceof com.google.wireless.android.sdk.stats.EmulatorFeatures) {
        return mergeFrom((com.google.wireless.android.sdk.stats.EmulatorFeatures)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.EmulatorFeatures other) {
      if (other == com.google.wireless.android.sdk.stats.EmulatorFeatures.getDefaultInstance()) return this;
      if (other.hasGps()) {
        setGps(other.getGps());
      }
      if (other.hasSensors()) {
        setSensors(other.getSensors());
      }
      if (other.hasVirtualsceneConfig()) {
        setVirtualsceneConfig(other.getVirtualsceneConfig());
      }
      if (other.hasContainerLaunch()) {
        setContainerLaunch(other.getContainerLaunch());
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
      com.google.wireless.android.sdk.stats.EmulatorFeatures parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.google.wireless.android.sdk.stats.EmulatorFeatures) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private boolean gps_ ;
    /**
     * <code>optional bool gps = 1;</code>
     * @return Whether the gps field is set.
     */
    public boolean hasGps() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <code>optional bool gps = 1;</code>
     * @return The gps.
     */
    public boolean getGps() {
      return gps_;
    }
    /**
     * <code>optional bool gps = 1;</code>
     * @param value The gps to set.
     * @return This builder for chaining.
     */
    public Builder setGps(boolean value) {
      bitField0_ |= 0x00000001;
      gps_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional bool gps = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearGps() {
      bitField0_ = (bitField0_ & ~0x00000001);
      gps_ = false;
      onChanged();
      return this;
    }

    private boolean sensors_ ;
    /**
     * <code>optional bool sensors = 2;</code>
     * @return Whether the sensors field is set.
     */
    public boolean hasSensors() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <code>optional bool sensors = 2;</code>
     * @return The sensors.
     */
    public boolean getSensors() {
      return sensors_;
    }
    /**
     * <code>optional bool sensors = 2;</code>
     * @param value The sensors to set.
     * @return This builder for chaining.
     */
    public Builder setSensors(boolean value) {
      bitField0_ |= 0x00000002;
      sensors_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional bool sensors = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearSensors() {
      bitField0_ = (bitField0_ & ~0x00000002);
      sensors_ = false;
      onChanged();
      return this;
    }

    private boolean virtualsceneConfig_ ;
    /**
     * <code>optional bool virtualscene_config = 3;</code>
     * @return Whether the virtualsceneConfig field is set.
     */
    public boolean hasVirtualsceneConfig() {
      return ((bitField0_ & 0x00000004) != 0);
    }
    /**
     * <code>optional bool virtualscene_config = 3;</code>
     * @return The virtualsceneConfig.
     */
    public boolean getVirtualsceneConfig() {
      return virtualsceneConfig_;
    }
    /**
     * <code>optional bool virtualscene_config = 3;</code>
     * @param value The virtualsceneConfig to set.
     * @return This builder for chaining.
     */
    public Builder setVirtualsceneConfig(boolean value) {
      bitField0_ |= 0x00000004;
      virtualsceneConfig_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional bool virtualscene_config = 3;</code>
     * @return This builder for chaining.
     */
    public Builder clearVirtualsceneConfig() {
      bitField0_ = (bitField0_ & ~0x00000004);
      virtualsceneConfig_ = false;
      onChanged();
      return this;
    }

    private boolean containerLaunch_ ;
    /**
     * <pre>
     * True if the emulator is running standalone.
     * </pre>
     *
     * <code>optional bool container_launch = 4;</code>
     * @return Whether the containerLaunch field is set.
     */
    public boolean hasContainerLaunch() {
      return ((bitField0_ & 0x00000008) != 0);
    }
    /**
     * <pre>
     * True if the emulator is running standalone.
     * </pre>
     *
     * <code>optional bool container_launch = 4;</code>
     * @return The containerLaunch.
     */
    public boolean getContainerLaunch() {
      return containerLaunch_;
    }
    /**
     * <pre>
     * True if the emulator is running standalone.
     * </pre>
     *
     * <code>optional bool container_launch = 4;</code>
     * @param value The containerLaunch to set.
     * @return This builder for chaining.
     */
    public Builder setContainerLaunch(boolean value) {
      bitField0_ |= 0x00000008;
      containerLaunch_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * True if the emulator is running standalone.
     * </pre>
     *
     * <code>optional bool container_launch = 4;</code>
     * @return This builder for chaining.
     */
    public Builder clearContainerLaunch() {
      bitField0_ = (bitField0_ & ~0x00000008);
      containerLaunch_ = false;
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


    // @@protoc_insertion_point(builder_scope:android_studio.EmulatorFeatures)
  }

  // @@protoc_insertion_point(class_scope:android_studio.EmulatorFeatures)
  private static final com.google.wireless.android.sdk.stats.EmulatorFeatures DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.EmulatorFeatures();
  }

  public static com.google.wireless.android.sdk.stats.EmulatorFeatures getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<EmulatorFeatures>
      PARSER = new com.google.protobuf.AbstractParser<EmulatorFeatures>() {
    @java.lang.Override
    public EmulatorFeatures parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new EmulatorFeatures(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<EmulatorFeatures> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<EmulatorFeatures> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.EmulatorFeatures getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

