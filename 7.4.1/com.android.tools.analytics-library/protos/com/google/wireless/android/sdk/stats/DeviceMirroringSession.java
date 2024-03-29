// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * Protobuf type {@code android_studio.DeviceMirroringSession}
 */
public final class DeviceMirroringSession extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.DeviceMirroringSession)
    DeviceMirroringSessionOrBuilder {
private static final long serialVersionUID = 0L;
  // Use DeviceMirroringSession.newBuilder() to construct.
  private DeviceMirroringSession(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private DeviceMirroringSession() {
    deviceKind_ = 0;
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new DeviceMirroringSession();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private DeviceMirroringSession(
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
            int rawValue = input.readEnum();
              @SuppressWarnings("deprecation")
            com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind value = com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind.valueOf(rawValue);
            if (value == null) {
              unknownFields.mergeVarintField(1, rawValue);
            } else {
              bitField0_ |= 0x00000001;
              deviceKind_ = rawValue;
            }
            break;
          }
          case 16: {
            bitField0_ |= 0x00000002;
            durationSec_ = input.readInt64();
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
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceMirroringSession_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceMirroringSession_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.DeviceMirroringSession.class, com.google.wireless.android.sdk.stats.DeviceMirroringSession.Builder.class);
  }

  /**
   * Protobuf enum {@code android_studio.DeviceMirroringSession.DeviceKind}
   */
  public enum DeviceKind
      implements com.google.protobuf.ProtocolMessageEnum {
    /**
     * <code>UNKNOWN_DEVICE_KIND = 0;</code>
     */
    UNKNOWN_DEVICE_KIND(0),
    /**
     * <code>PHYSICAL = 1;</code>
     */
    PHYSICAL(1),
    /**
     * <code>VIRTUAL = 2;</code>
     */
    VIRTUAL(2),
    ;

    /**
     * <code>UNKNOWN_DEVICE_KIND = 0;</code>
     */
    public static final int UNKNOWN_DEVICE_KIND_VALUE = 0;
    /**
     * <code>PHYSICAL = 1;</code>
     */
    public static final int PHYSICAL_VALUE = 1;
    /**
     * <code>VIRTUAL = 2;</code>
     */
    public static final int VIRTUAL_VALUE = 2;


    public final int getNumber() {
      return value;
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static DeviceKind valueOf(int value) {
      return forNumber(value);
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     */
    public static DeviceKind forNumber(int value) {
      switch (value) {
        case 0: return UNKNOWN_DEVICE_KIND;
        case 1: return PHYSICAL;
        case 2: return VIRTUAL;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<DeviceKind>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        DeviceKind> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<DeviceKind>() {
            public DeviceKind findValueByNumber(int number) {
              return DeviceKind.forNumber(number);
            }
          };

    public final com.google.protobuf.Descriptors.EnumValueDescriptor
        getValueDescriptor() {
      return getDescriptor().getValues().get(ordinal());
    }
    public final com.google.protobuf.Descriptors.EnumDescriptor
        getDescriptorForType() {
      return getDescriptor();
    }
    public static final com.google.protobuf.Descriptors.EnumDescriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.DeviceMirroringSession.getDescriptor().getEnumTypes().get(0);
    }

    private static final DeviceKind[] VALUES = values();

    public static DeviceKind valueOf(
        com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
      if (desc.getType() != getDescriptor()) {
        throw new java.lang.IllegalArgumentException(
          "EnumValueDescriptor is not for this type.");
      }
      return VALUES[desc.getIndex()];
    }

    private final int value;

    private DeviceKind(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:android_studio.DeviceMirroringSession.DeviceKind)
  }

  private int bitField0_;
  public static final int DEVICE_KIND_FIELD_NUMBER = 1;
  private int deviceKind_;
  /**
   * <code>optional .android_studio.DeviceMirroringSession.DeviceKind device_kind = 1;</code>
   * @return Whether the deviceKind field is set.
   */
  @java.lang.Override public boolean hasDeviceKind() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <code>optional .android_studio.DeviceMirroringSession.DeviceKind device_kind = 1;</code>
   * @return The deviceKind.
   */
  @java.lang.Override public com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind getDeviceKind() {
    @SuppressWarnings("deprecation")
    com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind result = com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind.valueOf(deviceKind_);
    return result == null ? com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind.UNKNOWN_DEVICE_KIND : result;
  }

  public static final int DURATION_SEC_FIELD_NUMBER = 2;
  private long durationSec_;
  /**
   * <code>optional int64 duration_sec = 2;</code>
   * @return Whether the durationSec field is set.
   */
  @java.lang.Override
  public boolean hasDurationSec() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <code>optional int64 duration_sec = 2;</code>
   * @return The durationSec.
   */
  @java.lang.Override
  public long getDurationSec() {
    return durationSec_;
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
      output.writeEnum(1, deviceKind_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeInt64(2, durationSec_);
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
        .computeEnumSize(1, deviceKind_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(2, durationSec_);
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
    if (!(obj instanceof com.google.wireless.android.sdk.stats.DeviceMirroringSession)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.DeviceMirroringSession other = (com.google.wireless.android.sdk.stats.DeviceMirroringSession) obj;

    if (hasDeviceKind() != other.hasDeviceKind()) return false;
    if (hasDeviceKind()) {
      if (deviceKind_ != other.deviceKind_) return false;
    }
    if (hasDurationSec() != other.hasDurationSec()) return false;
    if (hasDurationSec()) {
      if (getDurationSec()
          != other.getDurationSec()) return false;
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
    if (hasDeviceKind()) {
      hash = (37 * hash) + DEVICE_KIND_FIELD_NUMBER;
      hash = (53 * hash) + deviceKind_;
    }
    if (hasDurationSec()) {
      hash = (37 * hash) + DURATION_SEC_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getDurationSec());
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.DeviceMirroringSession prototype) {
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
   * Protobuf type {@code android_studio.DeviceMirroringSession}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.DeviceMirroringSession)
      com.google.wireless.android.sdk.stats.DeviceMirroringSessionOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceMirroringSession_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceMirroringSession_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.DeviceMirroringSession.class, com.google.wireless.android.sdk.stats.DeviceMirroringSession.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.DeviceMirroringSession.newBuilder()
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
      deviceKind_ = 0;
      bitField0_ = (bitField0_ & ~0x00000001);
      durationSec_ = 0L;
      bitField0_ = (bitField0_ & ~0x00000002);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceMirroringSession_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceMirroringSession getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.DeviceMirroringSession.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceMirroringSession build() {
      com.google.wireless.android.sdk.stats.DeviceMirroringSession result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceMirroringSession buildPartial() {
      com.google.wireless.android.sdk.stats.DeviceMirroringSession result = new com.google.wireless.android.sdk.stats.DeviceMirroringSession(this);
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        to_bitField0_ |= 0x00000001;
      }
      result.deviceKind_ = deviceKind_;
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.durationSec_ = durationSec_;
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
      if (other instanceof com.google.wireless.android.sdk.stats.DeviceMirroringSession) {
        return mergeFrom((com.google.wireless.android.sdk.stats.DeviceMirroringSession)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.DeviceMirroringSession other) {
      if (other == com.google.wireless.android.sdk.stats.DeviceMirroringSession.getDefaultInstance()) return this;
      if (other.hasDeviceKind()) {
        setDeviceKind(other.getDeviceKind());
      }
      if (other.hasDurationSec()) {
        setDurationSec(other.getDurationSec());
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
      com.google.wireless.android.sdk.stats.DeviceMirroringSession parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.google.wireless.android.sdk.stats.DeviceMirroringSession) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private int deviceKind_ = 0;
    /**
     * <code>optional .android_studio.DeviceMirroringSession.DeviceKind device_kind = 1;</code>
     * @return Whether the deviceKind field is set.
     */
    @java.lang.Override public boolean hasDeviceKind() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <code>optional .android_studio.DeviceMirroringSession.DeviceKind device_kind = 1;</code>
     * @return The deviceKind.
     */
    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind getDeviceKind() {
      @SuppressWarnings("deprecation")
      com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind result = com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind.valueOf(deviceKind_);
      return result == null ? com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind.UNKNOWN_DEVICE_KIND : result;
    }
    /**
     * <code>optional .android_studio.DeviceMirroringSession.DeviceKind device_kind = 1;</code>
     * @param value The deviceKind to set.
     * @return This builder for chaining.
     */
    public Builder setDeviceKind(com.google.wireless.android.sdk.stats.DeviceMirroringSession.DeviceKind value) {
      if (value == null) {
        throw new NullPointerException();
      }
      bitField0_ |= 0x00000001;
      deviceKind_ = value.getNumber();
      onChanged();
      return this;
    }
    /**
     * <code>optional .android_studio.DeviceMirroringSession.DeviceKind device_kind = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearDeviceKind() {
      bitField0_ = (bitField0_ & ~0x00000001);
      deviceKind_ = 0;
      onChanged();
      return this;
    }

    private long durationSec_ ;
    /**
     * <code>optional int64 duration_sec = 2;</code>
     * @return Whether the durationSec field is set.
     */
    @java.lang.Override
    public boolean hasDurationSec() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <code>optional int64 duration_sec = 2;</code>
     * @return The durationSec.
     */
    @java.lang.Override
    public long getDurationSec() {
      return durationSec_;
    }
    /**
     * <code>optional int64 duration_sec = 2;</code>
     * @param value The durationSec to set.
     * @return This builder for chaining.
     */
    public Builder setDurationSec(long value) {
      bitField0_ |= 0x00000002;
      durationSec_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>optional int64 duration_sec = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearDurationSec() {
      bitField0_ = (bitField0_ & ~0x00000002);
      durationSec_ = 0L;
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


    // @@protoc_insertion_point(builder_scope:android_studio.DeviceMirroringSession)
  }

  // @@protoc_insertion_point(class_scope:android_studio.DeviceMirroringSession)
  private static final com.google.wireless.android.sdk.stats.DeviceMirroringSession DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.DeviceMirroringSession();
  }

  public static com.google.wireless.android.sdk.stats.DeviceMirroringSession getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<DeviceMirroringSession>
      PARSER = new com.google.protobuf.AbstractParser<DeviceMirroringSession>() {
    @java.lang.Override
    public DeviceMirroringSession parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new DeviceMirroringSession(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<DeviceMirroringSession> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<DeviceMirroringSession> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.DeviceMirroringSession getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

