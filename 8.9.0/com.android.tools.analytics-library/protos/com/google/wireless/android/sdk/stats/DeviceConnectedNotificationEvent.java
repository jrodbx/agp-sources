// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * <pre>
 * An event fired up each time a device connects
 * </pre>
 *
 * Protobuf type {@code android_studio.DeviceConnectedNotificationEvent}
 */
public final class DeviceConnectedNotificationEvent extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.DeviceConnectedNotificationEvent)
    DeviceConnectedNotificationEventOrBuilder {
private static final long serialVersionUID = 0L;
  // Use DeviceConnectedNotificationEvent.newBuilder() to construct.
  private DeviceConnectedNotificationEvent(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private DeviceConnectedNotificationEvent() {
    type_ = 0;
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new DeviceConnectedNotificationEvent();
  }

  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceConnectedNotificationEvent_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceConnectedNotificationEvent_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.class, com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.Builder.class);
  }

  /**
   * Protobuf enum {@code android_studio.DeviceConnectedNotificationEvent.DeviceConnectionType}
   */
  public enum DeviceConnectionType
      implements com.google.protobuf.ProtocolMessageEnum {
    /**
     * <code>UNKNOWN_DEVICE_CONNECTION_TYPE = 0;</code>
     */
    UNKNOWN_DEVICE_CONNECTION_TYPE(0),
    /**
     * <code>USB = 1;</code>
     */
    USB(1),
    /**
     * <code>SOCKET = 2;</code>
     */
    SOCKET(2),
    ;

    /**
     * <code>UNKNOWN_DEVICE_CONNECTION_TYPE = 0;</code>
     */
    public static final int UNKNOWN_DEVICE_CONNECTION_TYPE_VALUE = 0;
    /**
     * <code>USB = 1;</code>
     */
    public static final int USB_VALUE = 1;
    /**
     * <code>SOCKET = 2;</code>
     */
    public static final int SOCKET_VALUE = 2;


    public final int getNumber() {
      return value;
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static DeviceConnectionType valueOf(int value) {
      return forNumber(value);
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     */
    public static DeviceConnectionType forNumber(int value) {
      switch (value) {
        case 0: return UNKNOWN_DEVICE_CONNECTION_TYPE;
        case 1: return USB;
        case 2: return SOCKET;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<DeviceConnectionType>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        DeviceConnectionType> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<DeviceConnectionType>() {
            public DeviceConnectionType findValueByNumber(int number) {
              return DeviceConnectionType.forNumber(number);
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
      return com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.getDescriptor().getEnumTypes().get(0);
    }

    private static final DeviceConnectionType[] VALUES = values();

    public static DeviceConnectionType valueOf(
        com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
      if (desc.getType() != getDescriptor()) {
        throw new java.lang.IllegalArgumentException(
          "EnumValueDescriptor is not for this type.");
      }
      return VALUES[desc.getIndex()];
    }

    private final int value;

    private DeviceConnectionType(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:android_studio.DeviceConnectedNotificationEvent.DeviceConnectionType)
  }

  private int bitField0_;
  public static final int TYPE_FIELD_NUMBER = 1;
  private int type_ = 0;
  /**
   * <code>optional .android_studio.DeviceConnectedNotificationEvent.DeviceConnectionType type = 1;</code>
   * @return Whether the type field is set.
   */
  @java.lang.Override public boolean hasType() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <code>optional .android_studio.DeviceConnectedNotificationEvent.DeviceConnectionType type = 1;</code>
   * @return The type.
   */
  @java.lang.Override public com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType getType() {
    com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType result = com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType.forNumber(type_);
    return result == null ? com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType.UNKNOWN_DEVICE_CONNECTION_TYPE : result;
  }

  public static final int MAX_SPEED_MBPS_FIELD_NUMBER = 2;
  private long maxSpeedMbps_ = 0L;
  /**
   * <pre>
   * ADB's detected device maximum speed (Mbps)
   * </pre>
   *
   * <code>optional uint64 max_speed_mbps = 2;</code>
   * @return Whether the maxSpeedMbps field is set.
   */
  @java.lang.Override
  public boolean hasMaxSpeedMbps() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <pre>
   * ADB's detected device maximum speed (Mbps)
   * </pre>
   *
   * <code>optional uint64 max_speed_mbps = 2;</code>
   * @return The maxSpeedMbps.
   */
  @java.lang.Override
  public long getMaxSpeedMbps() {
    return maxSpeedMbps_;
  }

  public static final int NEGOTIATED_SPEED_MBPS_FIELD_NUMBER = 3;
  private long negotiatedSpeedMbps_ = 0L;
  /**
   * <pre>
   * ADB's detected device negotiated speed (Mbps)
   * </pre>
   *
   * <code>optional uint64 negotiated_speed_mbps = 3;</code>
   * @return Whether the negotiatedSpeedMbps field is set.
   */
  @java.lang.Override
  public boolean hasNegotiatedSpeedMbps() {
    return ((bitField0_ & 0x00000004) != 0);
  }
  /**
   * <pre>
   * ADB's detected device negotiated speed (Mbps)
   * </pre>
   *
   * <code>optional uint64 negotiated_speed_mbps = 3;</code>
   * @return The negotiatedSpeedMbps.
   */
  @java.lang.Override
  public long getNegotiatedSpeedMbps() {
    return negotiatedSpeedMbps_;
  }

  public static final int SPEED_NOTIFICATIONS_STUDIO_DISABLED_FIELD_NUMBER = 4;
  private boolean speedNotificationsStudioDisabled_ = false;
  /**
   * <pre>
   * Is the notification disabled by StudioFlags
   * </pre>
   *
   * <code>optional bool speed_notifications_studio_disabled = 4;</code>
   * @return Whether the speedNotificationsStudioDisabled field is set.
   */
  @java.lang.Override
  public boolean hasSpeedNotificationsStudioDisabled() {
    return ((bitField0_ & 0x00000008) != 0);
  }
  /**
   * <pre>
   * Is the notification disabled by StudioFlags
   * </pre>
   *
   * <code>optional bool speed_notifications_studio_disabled = 4;</code>
   * @return The speedNotificationsStudioDisabled.
   */
  @java.lang.Override
  public boolean getSpeedNotificationsStudioDisabled() {
    return speedNotificationsStudioDisabled_;
  }

  public static final int SPEED_NOTIFICATIONS_USER_DISABLED_FIELD_NUMBER = 5;
  private boolean speedNotificationsUserDisabled_ = false;
  /**
   * <pre>
   * Is the notification disabled by user preferences
   * </pre>
   *
   * <code>optional bool speed_notifications_user_disabled = 5;</code>
   * @return Whether the speedNotificationsUserDisabled field is set.
   */
  @java.lang.Override
  public boolean hasSpeedNotificationsUserDisabled() {
    return ((bitField0_ & 0x00000010) != 0);
  }
  /**
   * <pre>
   * Is the notification disabled by user preferences
   * </pre>
   *
   * <code>optional bool speed_notifications_user_disabled = 5;</code>
   * @return The speedNotificationsUserDisabled.
   */
  @java.lang.Override
  public boolean getSpeedNotificationsUserDisabled() {
    return speedNotificationsUserDisabled_;
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
      output.writeEnum(1, type_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeUInt64(2, maxSpeedMbps_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      output.writeUInt64(3, negotiatedSpeedMbps_);
    }
    if (((bitField0_ & 0x00000008) != 0)) {
      output.writeBool(4, speedNotificationsStudioDisabled_);
    }
    if (((bitField0_ & 0x00000010) != 0)) {
      output.writeBool(5, speedNotificationsUserDisabled_);
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
        .computeEnumSize(1, type_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeUInt64Size(2, maxSpeedMbps_);
    }
    if (((bitField0_ & 0x00000004) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeUInt64Size(3, negotiatedSpeedMbps_);
    }
    if (((bitField0_ & 0x00000008) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeBoolSize(4, speedNotificationsStudioDisabled_);
    }
    if (((bitField0_ & 0x00000010) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeBoolSize(5, speedNotificationsUserDisabled_);
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
    if (!(obj instanceof com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent other = (com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent) obj;

    if (hasType() != other.hasType()) return false;
    if (hasType()) {
      if (type_ != other.type_) return false;
    }
    if (hasMaxSpeedMbps() != other.hasMaxSpeedMbps()) return false;
    if (hasMaxSpeedMbps()) {
      if (getMaxSpeedMbps()
          != other.getMaxSpeedMbps()) return false;
    }
    if (hasNegotiatedSpeedMbps() != other.hasNegotiatedSpeedMbps()) return false;
    if (hasNegotiatedSpeedMbps()) {
      if (getNegotiatedSpeedMbps()
          != other.getNegotiatedSpeedMbps()) return false;
    }
    if (hasSpeedNotificationsStudioDisabled() != other.hasSpeedNotificationsStudioDisabled()) return false;
    if (hasSpeedNotificationsStudioDisabled()) {
      if (getSpeedNotificationsStudioDisabled()
          != other.getSpeedNotificationsStudioDisabled()) return false;
    }
    if (hasSpeedNotificationsUserDisabled() != other.hasSpeedNotificationsUserDisabled()) return false;
    if (hasSpeedNotificationsUserDisabled()) {
      if (getSpeedNotificationsUserDisabled()
          != other.getSpeedNotificationsUserDisabled()) return false;
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
    if (hasType()) {
      hash = (37 * hash) + TYPE_FIELD_NUMBER;
      hash = (53 * hash) + type_;
    }
    if (hasMaxSpeedMbps()) {
      hash = (37 * hash) + MAX_SPEED_MBPS_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getMaxSpeedMbps());
    }
    if (hasNegotiatedSpeedMbps()) {
      hash = (37 * hash) + NEGOTIATED_SPEED_MBPS_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
          getNegotiatedSpeedMbps());
    }
    if (hasSpeedNotificationsStudioDisabled()) {
      hash = (37 * hash) + SPEED_NOTIFICATIONS_STUDIO_DISABLED_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(
          getSpeedNotificationsStudioDisabled());
    }
    if (hasSpeedNotificationsUserDisabled()) {
      hash = (37 * hash) + SPEED_NOTIFICATIONS_USER_DISABLED_FIELD_NUMBER;
      hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(
          getSpeedNotificationsUserDisabled());
    }
    hash = (29 * hash) + getUnknownFields().hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent prototype) {
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
   * An event fired up each time a device connects
   * </pre>
   *
   * Protobuf type {@code android_studio.DeviceConnectedNotificationEvent}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.DeviceConnectedNotificationEvent)
      com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEventOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceConnectedNotificationEvent_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceConnectedNotificationEvent_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.class, com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.newBuilder()
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
      type_ = 0;
      maxSpeedMbps_ = 0L;
      negotiatedSpeedMbps_ = 0L;
      speedNotificationsStudioDisabled_ = false;
      speedNotificationsUserDisabled_ = false;
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceConnectedNotificationEvent_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent build() {
      com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent buildPartial() {
      com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent result = new com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent(this);
      if (bitField0_ != 0) { buildPartial0(result); }
      onBuilt();
      return result;
    }

    private void buildPartial0(com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent result) {
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        result.type_ = type_;
        to_bitField0_ |= 0x00000001;
      }
      if (((from_bitField0_ & 0x00000002) != 0)) {
        result.maxSpeedMbps_ = maxSpeedMbps_;
        to_bitField0_ |= 0x00000002;
      }
      if (((from_bitField0_ & 0x00000004) != 0)) {
        result.negotiatedSpeedMbps_ = negotiatedSpeedMbps_;
        to_bitField0_ |= 0x00000004;
      }
      if (((from_bitField0_ & 0x00000008) != 0)) {
        result.speedNotificationsStudioDisabled_ = speedNotificationsStudioDisabled_;
        to_bitField0_ |= 0x00000008;
      }
      if (((from_bitField0_ & 0x00000010) != 0)) {
        result.speedNotificationsUserDisabled_ = speedNotificationsUserDisabled_;
        to_bitField0_ |= 0x00000010;
      }
      result.bitField0_ |= to_bitField0_;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent) {
        return mergeFrom((com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent other) {
      if (other == com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.getDefaultInstance()) return this;
      if (other.hasType()) {
        setType(other.getType());
      }
      if (other.hasMaxSpeedMbps()) {
        setMaxSpeedMbps(other.getMaxSpeedMbps());
      }
      if (other.hasNegotiatedSpeedMbps()) {
        setNegotiatedSpeedMbps(other.getNegotiatedSpeedMbps());
      }
      if (other.hasSpeedNotificationsStudioDisabled()) {
        setSpeedNotificationsStudioDisabled(other.getSpeedNotificationsStudioDisabled());
      }
      if (other.hasSpeedNotificationsUserDisabled()) {
        setSpeedNotificationsUserDisabled(other.getSpeedNotificationsUserDisabled());
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
              int tmpRaw = input.readEnum();
              com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType tmpValue =
                  com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType.forNumber(tmpRaw);
              if (tmpValue == null) {
                mergeUnknownVarintField(1, tmpRaw);
              } else {
                type_ = tmpRaw;
                bitField0_ |= 0x00000001;
              }
              break;
            } // case 8
            case 16: {
              maxSpeedMbps_ = input.readUInt64();
              bitField0_ |= 0x00000002;
              break;
            } // case 16
            case 24: {
              negotiatedSpeedMbps_ = input.readUInt64();
              bitField0_ |= 0x00000004;
              break;
            } // case 24
            case 32: {
              speedNotificationsStudioDisabled_ = input.readBool();
              bitField0_ |= 0x00000008;
              break;
            } // case 32
            case 40: {
              speedNotificationsUserDisabled_ = input.readBool();
              bitField0_ |= 0x00000010;
              break;
            } // case 40
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

    private int type_ = 0;
    /**
     * <code>optional .android_studio.DeviceConnectedNotificationEvent.DeviceConnectionType type = 1;</code>
     * @return Whether the type field is set.
     */
    @java.lang.Override public boolean hasType() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <code>optional .android_studio.DeviceConnectedNotificationEvent.DeviceConnectionType type = 1;</code>
     * @return The type.
     */
    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType getType() {
      com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType result = com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType.forNumber(type_);
      return result == null ? com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType.UNKNOWN_DEVICE_CONNECTION_TYPE : result;
    }
    /**
     * <code>optional .android_studio.DeviceConnectedNotificationEvent.DeviceConnectionType type = 1;</code>
     * @param value The type to set.
     * @return This builder for chaining.
     */
    public Builder setType(com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType value) {
      if (value == null) {
        throw new NullPointerException();
      }
      bitField0_ |= 0x00000001;
      type_ = value.getNumber();
      onChanged();
      return this;
    }
    /**
     * <code>optional .android_studio.DeviceConnectedNotificationEvent.DeviceConnectionType type = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearType() {
      bitField0_ = (bitField0_ & ~0x00000001);
      type_ = 0;
      onChanged();
      return this;
    }

    private long maxSpeedMbps_ ;
    /**
     * <pre>
     * ADB's detected device maximum speed (Mbps)
     * </pre>
     *
     * <code>optional uint64 max_speed_mbps = 2;</code>
     * @return Whether the maxSpeedMbps field is set.
     */
    @java.lang.Override
    public boolean hasMaxSpeedMbps() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <pre>
     * ADB's detected device maximum speed (Mbps)
     * </pre>
     *
     * <code>optional uint64 max_speed_mbps = 2;</code>
     * @return The maxSpeedMbps.
     */
    @java.lang.Override
    public long getMaxSpeedMbps() {
      return maxSpeedMbps_;
    }
    /**
     * <pre>
     * ADB's detected device maximum speed (Mbps)
     * </pre>
     *
     * <code>optional uint64 max_speed_mbps = 2;</code>
     * @param value The maxSpeedMbps to set.
     * @return This builder for chaining.
     */
    public Builder setMaxSpeedMbps(long value) {

      maxSpeedMbps_ = value;
      bitField0_ |= 0x00000002;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * ADB's detected device maximum speed (Mbps)
     * </pre>
     *
     * <code>optional uint64 max_speed_mbps = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearMaxSpeedMbps() {
      bitField0_ = (bitField0_ & ~0x00000002);
      maxSpeedMbps_ = 0L;
      onChanged();
      return this;
    }

    private long negotiatedSpeedMbps_ ;
    /**
     * <pre>
     * ADB's detected device negotiated speed (Mbps)
     * </pre>
     *
     * <code>optional uint64 negotiated_speed_mbps = 3;</code>
     * @return Whether the negotiatedSpeedMbps field is set.
     */
    @java.lang.Override
    public boolean hasNegotiatedSpeedMbps() {
      return ((bitField0_ & 0x00000004) != 0);
    }
    /**
     * <pre>
     * ADB's detected device negotiated speed (Mbps)
     * </pre>
     *
     * <code>optional uint64 negotiated_speed_mbps = 3;</code>
     * @return The negotiatedSpeedMbps.
     */
    @java.lang.Override
    public long getNegotiatedSpeedMbps() {
      return negotiatedSpeedMbps_;
    }
    /**
     * <pre>
     * ADB's detected device negotiated speed (Mbps)
     * </pre>
     *
     * <code>optional uint64 negotiated_speed_mbps = 3;</code>
     * @param value The negotiatedSpeedMbps to set.
     * @return This builder for chaining.
     */
    public Builder setNegotiatedSpeedMbps(long value) {

      negotiatedSpeedMbps_ = value;
      bitField0_ |= 0x00000004;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * ADB's detected device negotiated speed (Mbps)
     * </pre>
     *
     * <code>optional uint64 negotiated_speed_mbps = 3;</code>
     * @return This builder for chaining.
     */
    public Builder clearNegotiatedSpeedMbps() {
      bitField0_ = (bitField0_ & ~0x00000004);
      negotiatedSpeedMbps_ = 0L;
      onChanged();
      return this;
    }

    private boolean speedNotificationsStudioDisabled_ ;
    /**
     * <pre>
     * Is the notification disabled by StudioFlags
     * </pre>
     *
     * <code>optional bool speed_notifications_studio_disabled = 4;</code>
     * @return Whether the speedNotificationsStudioDisabled field is set.
     */
    @java.lang.Override
    public boolean hasSpeedNotificationsStudioDisabled() {
      return ((bitField0_ & 0x00000008) != 0);
    }
    /**
     * <pre>
     * Is the notification disabled by StudioFlags
     * </pre>
     *
     * <code>optional bool speed_notifications_studio_disabled = 4;</code>
     * @return The speedNotificationsStudioDisabled.
     */
    @java.lang.Override
    public boolean getSpeedNotificationsStudioDisabled() {
      return speedNotificationsStudioDisabled_;
    }
    /**
     * <pre>
     * Is the notification disabled by StudioFlags
     * </pre>
     *
     * <code>optional bool speed_notifications_studio_disabled = 4;</code>
     * @param value The speedNotificationsStudioDisabled to set.
     * @return This builder for chaining.
     */
    public Builder setSpeedNotificationsStudioDisabled(boolean value) {

      speedNotificationsStudioDisabled_ = value;
      bitField0_ |= 0x00000008;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Is the notification disabled by StudioFlags
     * </pre>
     *
     * <code>optional bool speed_notifications_studio_disabled = 4;</code>
     * @return This builder for chaining.
     */
    public Builder clearSpeedNotificationsStudioDisabled() {
      bitField0_ = (bitField0_ & ~0x00000008);
      speedNotificationsStudioDisabled_ = false;
      onChanged();
      return this;
    }

    private boolean speedNotificationsUserDisabled_ ;
    /**
     * <pre>
     * Is the notification disabled by user preferences
     * </pre>
     *
     * <code>optional bool speed_notifications_user_disabled = 5;</code>
     * @return Whether the speedNotificationsUserDisabled field is set.
     */
    @java.lang.Override
    public boolean hasSpeedNotificationsUserDisabled() {
      return ((bitField0_ & 0x00000010) != 0);
    }
    /**
     * <pre>
     * Is the notification disabled by user preferences
     * </pre>
     *
     * <code>optional bool speed_notifications_user_disabled = 5;</code>
     * @return The speedNotificationsUserDisabled.
     */
    @java.lang.Override
    public boolean getSpeedNotificationsUserDisabled() {
      return speedNotificationsUserDisabled_;
    }
    /**
     * <pre>
     * Is the notification disabled by user preferences
     * </pre>
     *
     * <code>optional bool speed_notifications_user_disabled = 5;</code>
     * @param value The speedNotificationsUserDisabled to set.
     * @return This builder for chaining.
     */
    public Builder setSpeedNotificationsUserDisabled(boolean value) {

      speedNotificationsUserDisabled_ = value;
      bitField0_ |= 0x00000010;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Is the notification disabled by user preferences
     * </pre>
     *
     * <code>optional bool speed_notifications_user_disabled = 5;</code>
     * @return This builder for chaining.
     */
    public Builder clearSpeedNotificationsUserDisabled() {
      bitField0_ = (bitField0_ & ~0x00000010);
      speedNotificationsUserDisabled_ = false;
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


    // @@protoc_insertion_point(builder_scope:android_studio.DeviceConnectedNotificationEvent)
  }

  // @@protoc_insertion_point(class_scope:android_studio.DeviceConnectedNotificationEvent)
  private static final com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent();
  }

  public static com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<DeviceConnectedNotificationEvent>
      PARSER = new com.google.protobuf.AbstractParser<DeviceConnectedNotificationEvent>() {
    @java.lang.Override
    public DeviceConnectedNotificationEvent parsePartialFrom(
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

  public static com.google.protobuf.Parser<DeviceConnectedNotificationEvent> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<DeviceConnectedNotificationEvent> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

