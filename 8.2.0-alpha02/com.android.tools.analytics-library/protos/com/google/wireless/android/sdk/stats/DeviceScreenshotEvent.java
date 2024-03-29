// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * Protobuf type {@code android_studio.DeviceScreenshotEvent}
 */
public final class DeviceScreenshotEvent extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.DeviceScreenshotEvent)
    DeviceScreenshotEventOrBuilder {
private static final long serialVersionUID = 0L;
  // Use DeviceScreenshotEvent.newBuilder() to construct.
  private DeviceScreenshotEvent(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private DeviceScreenshotEvent() {
    deviceType_ = 0;
    decorationOption_ = 0;
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new DeviceScreenshotEvent();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private DeviceScreenshotEvent(
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
            com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType value = com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType.valueOf(rawValue);
            if (value == null) {
              unknownFields.mergeVarintField(1, rawValue);
            } else {
              bitField0_ |= 0x00000001;
              deviceType_ = rawValue;
            }
            break;
          }
          case 16: {
            int rawValue = input.readEnum();
              @SuppressWarnings("deprecation")
            com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption value = com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption.valueOf(rawValue);
            if (value == null) {
              unknownFields.mergeVarintField(2, rawValue);
            } else {
              bitField0_ |= 0x00000002;
              decorationOption_ = rawValue;
            }
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
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceScreenshotEvent_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceScreenshotEvent_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.class, com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.Builder.class);
  }

  /**
   * Protobuf enum {@code android_studio.DeviceScreenshotEvent.DeviceType}
   */
  public enum DeviceType
      implements com.google.protobuf.ProtocolMessageEnum {
    /**
     * <code>UNKNOWN_DEVICE_TYPE = 0;</code>
     */
    UNKNOWN_DEVICE_TYPE(0),
    /**
     * <code>PHONE = 1;</code>
     */
    PHONE(1),
    /**
     * <code>WEAR = 2;</code>
     */
    WEAR(2),
    /**
     * <code>TV = 3;</code>
     */
    TV(3),
    ;

    /**
     * <code>UNKNOWN_DEVICE_TYPE = 0;</code>
     */
    public static final int UNKNOWN_DEVICE_TYPE_VALUE = 0;
    /**
     * <code>PHONE = 1;</code>
     */
    public static final int PHONE_VALUE = 1;
    /**
     * <code>WEAR = 2;</code>
     */
    public static final int WEAR_VALUE = 2;
    /**
     * <code>TV = 3;</code>
     */
    public static final int TV_VALUE = 3;


    public final int getNumber() {
      return value;
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static DeviceType valueOf(int value) {
      return forNumber(value);
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     */
    public static DeviceType forNumber(int value) {
      switch (value) {
        case 0: return UNKNOWN_DEVICE_TYPE;
        case 1: return PHONE;
        case 2: return WEAR;
        case 3: return TV;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<DeviceType>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        DeviceType> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<DeviceType>() {
            public DeviceType findValueByNumber(int number) {
              return DeviceType.forNumber(number);
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
      return com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.getDescriptor().getEnumTypes().get(0);
    }

    private static final DeviceType[] VALUES = values();

    public static DeviceType valueOf(
        com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
      if (desc.getType() != getDescriptor()) {
        throw new java.lang.IllegalArgumentException(
          "EnumValueDescriptor is not for this type.");
      }
      return VALUES[desc.getIndex()];
    }

    private final int value;

    private DeviceType(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:android_studio.DeviceScreenshotEvent.DeviceType)
  }

  /**
   * Protobuf enum {@code android_studio.DeviceScreenshotEvent.DecorationOption}
   */
  public enum DecorationOption
      implements com.google.protobuf.ProtocolMessageEnum {
    /**
     * <code>UNKNOWN_DECORATION_OPTION = 0;</code>
     */
    UNKNOWN_DECORATION_OPTION(0),
    /**
     * <code>RECTANGULAR = 1;</code>
     */
    RECTANGULAR(1),
    /**
     * <code>DISPLAY_SHAPE_CLIP = 2;</code>
     */
    DISPLAY_SHAPE_CLIP(2),
    /**
     * <code>PLAY_COMPATIBLE = 3;</code>
     */
    PLAY_COMPATIBLE(3),
    /**
     * <code>FRAMED = 4;</code>
     */
    FRAMED(4),
    ;

    /**
     * <code>UNKNOWN_DECORATION_OPTION = 0;</code>
     */
    public static final int UNKNOWN_DECORATION_OPTION_VALUE = 0;
    /**
     * <code>RECTANGULAR = 1;</code>
     */
    public static final int RECTANGULAR_VALUE = 1;
    /**
     * <code>DISPLAY_SHAPE_CLIP = 2;</code>
     */
    public static final int DISPLAY_SHAPE_CLIP_VALUE = 2;
    /**
     * <code>PLAY_COMPATIBLE = 3;</code>
     */
    public static final int PLAY_COMPATIBLE_VALUE = 3;
    /**
     * <code>FRAMED = 4;</code>
     */
    public static final int FRAMED_VALUE = 4;


    public final int getNumber() {
      return value;
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static DecorationOption valueOf(int value) {
      return forNumber(value);
    }

    /**
     * @param value The numeric wire value of the corresponding enum entry.
     * @return The enum associated with the given numeric wire value.
     */
    public static DecorationOption forNumber(int value) {
      switch (value) {
        case 0: return UNKNOWN_DECORATION_OPTION;
        case 1: return RECTANGULAR;
        case 2: return DISPLAY_SHAPE_CLIP;
        case 3: return PLAY_COMPATIBLE;
        case 4: return FRAMED;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<DecorationOption>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        DecorationOption> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<DecorationOption>() {
            public DecorationOption findValueByNumber(int number) {
              return DecorationOption.forNumber(number);
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
      return com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.getDescriptor().getEnumTypes().get(1);
    }

    private static final DecorationOption[] VALUES = values();

    public static DecorationOption valueOf(
        com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
      if (desc.getType() != getDescriptor()) {
        throw new java.lang.IllegalArgumentException(
          "EnumValueDescriptor is not for this type.");
      }
      return VALUES[desc.getIndex()];
    }

    private final int value;

    private DecorationOption(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:android_studio.DeviceScreenshotEvent.DecorationOption)
  }

  private int bitField0_;
  public static final int DEVICE_TYPE_FIELD_NUMBER = 1;
  private int deviceType_;
  /**
   * <pre>
   * The type of the device the screenshot is taken on
   * </pre>
   *
   * <code>optional .android_studio.DeviceScreenshotEvent.DeviceType device_type = 1;</code>
   * @return Whether the deviceType field is set.
   */
  @java.lang.Override public boolean hasDeviceType() {
    return ((bitField0_ & 0x00000001) != 0);
  }
  /**
   * <pre>
   * The type of the device the screenshot is taken on
   * </pre>
   *
   * <code>optional .android_studio.DeviceScreenshotEvent.DeviceType device_type = 1;</code>
   * @return The deviceType.
   */
  @java.lang.Override public com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType getDeviceType() {
    @SuppressWarnings("deprecation")
    com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType result = com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType.valueOf(deviceType_);
    return result == null ? com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType.UNKNOWN_DEVICE_TYPE : result;
  }

  public static final int DECORATION_OPTION_FIELD_NUMBER = 2;
  private int decorationOption_;
  /**
   * <pre>
   * The type of decoration used for the screenshot
   * </pre>
   *
   * <code>optional .android_studio.DeviceScreenshotEvent.DecorationOption decoration_option = 2;</code>
   * @return Whether the decorationOption field is set.
   */
  @java.lang.Override public boolean hasDecorationOption() {
    return ((bitField0_ & 0x00000002) != 0);
  }
  /**
   * <pre>
   * The type of decoration used for the screenshot
   * </pre>
   *
   * <code>optional .android_studio.DeviceScreenshotEvent.DecorationOption decoration_option = 2;</code>
   * @return The decorationOption.
   */
  @java.lang.Override public com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption getDecorationOption() {
    @SuppressWarnings("deprecation")
    com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption result = com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption.valueOf(decorationOption_);
    return result == null ? com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption.UNKNOWN_DECORATION_OPTION : result;
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
      output.writeEnum(1, deviceType_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      output.writeEnum(2, decorationOption_);
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
        .computeEnumSize(1, deviceType_);
    }
    if (((bitField0_ & 0x00000002) != 0)) {
      size += com.google.protobuf.CodedOutputStream
        .computeEnumSize(2, decorationOption_);
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
    if (!(obj instanceof com.google.wireless.android.sdk.stats.DeviceScreenshotEvent)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.DeviceScreenshotEvent other = (com.google.wireless.android.sdk.stats.DeviceScreenshotEvent) obj;

    if (hasDeviceType() != other.hasDeviceType()) return false;
    if (hasDeviceType()) {
      if (deviceType_ != other.deviceType_) return false;
    }
    if (hasDecorationOption() != other.hasDecorationOption()) return false;
    if (hasDecorationOption()) {
      if (decorationOption_ != other.decorationOption_) return false;
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
    if (hasDeviceType()) {
      hash = (37 * hash) + DEVICE_TYPE_FIELD_NUMBER;
      hash = (53 * hash) + deviceType_;
    }
    if (hasDecorationOption()) {
      hash = (37 * hash) + DECORATION_OPTION_FIELD_NUMBER;
      hash = (53 * hash) + decorationOption_;
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.DeviceScreenshotEvent prototype) {
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
   * Protobuf type {@code android_studio.DeviceScreenshotEvent}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.DeviceScreenshotEvent)
      com.google.wireless.android.sdk.stats.DeviceScreenshotEventOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceScreenshotEvent_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceScreenshotEvent_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.class, com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.newBuilder()
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
      deviceType_ = 0;
      bitField0_ = (bitField0_ & ~0x00000001);
      decorationOption_ = 0;
      bitField0_ = (bitField0_ & ~0x00000002);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_DeviceScreenshotEvent_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceScreenshotEvent getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceScreenshotEvent build() {
      com.google.wireless.android.sdk.stats.DeviceScreenshotEvent result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceScreenshotEvent buildPartial() {
      com.google.wireless.android.sdk.stats.DeviceScreenshotEvent result = new com.google.wireless.android.sdk.stats.DeviceScreenshotEvent(this);
      int from_bitField0_ = bitField0_;
      int to_bitField0_ = 0;
      if (((from_bitField0_ & 0x00000001) != 0)) {
        to_bitField0_ |= 0x00000001;
      }
      result.deviceType_ = deviceType_;
      if (((from_bitField0_ & 0x00000002) != 0)) {
        to_bitField0_ |= 0x00000002;
      }
      result.decorationOption_ = decorationOption_;
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
      if (other instanceof com.google.wireless.android.sdk.stats.DeviceScreenshotEvent) {
        return mergeFrom((com.google.wireless.android.sdk.stats.DeviceScreenshotEvent)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.DeviceScreenshotEvent other) {
      if (other == com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.getDefaultInstance()) return this;
      if (other.hasDeviceType()) {
        setDeviceType(other.getDeviceType());
      }
      if (other.hasDecorationOption()) {
        setDecorationOption(other.getDecorationOption());
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
      com.google.wireless.android.sdk.stats.DeviceScreenshotEvent parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.google.wireless.android.sdk.stats.DeviceScreenshotEvent) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private int deviceType_ = 0;
    /**
     * <pre>
     * The type of the device the screenshot is taken on
     * </pre>
     *
     * <code>optional .android_studio.DeviceScreenshotEvent.DeviceType device_type = 1;</code>
     * @return Whether the deviceType field is set.
     */
    @java.lang.Override public boolean hasDeviceType() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <pre>
     * The type of the device the screenshot is taken on
     * </pre>
     *
     * <code>optional .android_studio.DeviceScreenshotEvent.DeviceType device_type = 1;</code>
     * @return The deviceType.
     */
    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType getDeviceType() {
      @SuppressWarnings("deprecation")
      com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType result = com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType.valueOf(deviceType_);
      return result == null ? com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType.UNKNOWN_DEVICE_TYPE : result;
    }
    /**
     * <pre>
     * The type of the device the screenshot is taken on
     * </pre>
     *
     * <code>optional .android_studio.DeviceScreenshotEvent.DeviceType device_type = 1;</code>
     * @param value The deviceType to set.
     * @return This builder for chaining.
     */
    public Builder setDeviceType(com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DeviceType value) {
      if (value == null) {
        throw new NullPointerException();
      }
      bitField0_ |= 0x00000001;
      deviceType_ = value.getNumber();
      onChanged();
      return this;
    }
    /**
     * <pre>
     * The type of the device the screenshot is taken on
     * </pre>
     *
     * <code>optional .android_studio.DeviceScreenshotEvent.DeviceType device_type = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearDeviceType() {
      bitField0_ = (bitField0_ & ~0x00000001);
      deviceType_ = 0;
      onChanged();
      return this;
    }

    private int decorationOption_ = 0;
    /**
     * <pre>
     * The type of decoration used for the screenshot
     * </pre>
     *
     * <code>optional .android_studio.DeviceScreenshotEvent.DecorationOption decoration_option = 2;</code>
     * @return Whether the decorationOption field is set.
     */
    @java.lang.Override public boolean hasDecorationOption() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <pre>
     * The type of decoration used for the screenshot
     * </pre>
     *
     * <code>optional .android_studio.DeviceScreenshotEvent.DecorationOption decoration_option = 2;</code>
     * @return The decorationOption.
     */
    @java.lang.Override
    public com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption getDecorationOption() {
      @SuppressWarnings("deprecation")
      com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption result = com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption.valueOf(decorationOption_);
      return result == null ? com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption.UNKNOWN_DECORATION_OPTION : result;
    }
    /**
     * <pre>
     * The type of decoration used for the screenshot
     * </pre>
     *
     * <code>optional .android_studio.DeviceScreenshotEvent.DecorationOption decoration_option = 2;</code>
     * @param value The decorationOption to set.
     * @return This builder for chaining.
     */
    public Builder setDecorationOption(com.google.wireless.android.sdk.stats.DeviceScreenshotEvent.DecorationOption value) {
      if (value == null) {
        throw new NullPointerException();
      }
      bitField0_ |= 0x00000002;
      decorationOption_ = value.getNumber();
      onChanged();
      return this;
    }
    /**
     * <pre>
     * The type of decoration used for the screenshot
     * </pre>
     *
     * <code>optional .android_studio.DeviceScreenshotEvent.DecorationOption decoration_option = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearDecorationOption() {
      bitField0_ = (bitField0_ & ~0x00000002);
      decorationOption_ = 0;
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


    // @@protoc_insertion_point(builder_scope:android_studio.DeviceScreenshotEvent)
  }

  // @@protoc_insertion_point(class_scope:android_studio.DeviceScreenshotEvent)
  private static final com.google.wireless.android.sdk.stats.DeviceScreenshotEvent DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.DeviceScreenshotEvent();
  }

  public static com.google.wireless.android.sdk.stats.DeviceScreenshotEvent getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<DeviceScreenshotEvent>
      PARSER = new com.google.protobuf.AbstractParser<DeviceScreenshotEvent>() {
    @java.lang.Override
    public DeviceScreenshotEvent parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new DeviceScreenshotEvent(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<DeviceScreenshotEvent> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<DeviceScreenshotEvent> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.DeviceScreenshotEvent getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

