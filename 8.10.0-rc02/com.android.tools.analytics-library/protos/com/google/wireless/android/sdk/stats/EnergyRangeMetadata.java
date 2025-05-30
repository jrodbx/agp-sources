// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * Protobuf type {@code android_studio.EnergyRangeMetadata}
 */
public final class EnergyRangeMetadata extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:android_studio.EnergyRangeMetadata)
    EnergyRangeMetadataOrBuilder {
private static final long serialVersionUID = 0L;
  // Use EnergyRangeMetadata.newBuilder() to construct.
  private EnergyRangeMetadata(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private EnergyRangeMetadata() {
    eventCounts_ = java.util.Collections.emptyList();
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new EnergyRangeMetadata();
  }

  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EnergyRangeMetadata_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EnergyRangeMetadata_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.wireless.android.sdk.stats.EnergyRangeMetadata.class, com.google.wireless.android.sdk.stats.EnergyRangeMetadata.Builder.class);
  }

  public static final int EVENT_COUNTS_FIELD_NUMBER = 1;
  @SuppressWarnings("serial")
  private java.util.List<com.google.wireless.android.sdk.stats.EnergyEventCount> eventCounts_;
  /**
   * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
   */
  @java.lang.Override
  public java.util.List<com.google.wireless.android.sdk.stats.EnergyEventCount> getEventCountsList() {
    return eventCounts_;
  }
  /**
   * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
   */
  @java.lang.Override
  public java.util.List<? extends com.google.wireless.android.sdk.stats.EnergyEventCountOrBuilder> 
      getEventCountsOrBuilderList() {
    return eventCounts_;
  }
  /**
   * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
   */
  @java.lang.Override
  public int getEventCountsCount() {
    return eventCounts_.size();
  }
  /**
   * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
   */
  @java.lang.Override
  public com.google.wireless.android.sdk.stats.EnergyEventCount getEventCounts(int index) {
    return eventCounts_.get(index);
  }
  /**
   * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
   */
  @java.lang.Override
  public com.google.wireless.android.sdk.stats.EnergyEventCountOrBuilder getEventCountsOrBuilder(
      int index) {
    return eventCounts_.get(index);
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
    for (int i = 0; i < eventCounts_.size(); i++) {
      output.writeMessage(1, eventCounts_.get(i));
    }
    getUnknownFields().writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    for (int i = 0; i < eventCounts_.size(); i++) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(1, eventCounts_.get(i));
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
    if (!(obj instanceof com.google.wireless.android.sdk.stats.EnergyRangeMetadata)) {
      return super.equals(obj);
    }
    com.google.wireless.android.sdk.stats.EnergyRangeMetadata other = (com.google.wireless.android.sdk.stats.EnergyRangeMetadata) obj;

    if (!getEventCountsList()
        .equals(other.getEventCountsList())) return false;
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
    if (getEventCountsCount() > 0) {
      hash = (37 * hash) + EVENT_COUNTS_FIELD_NUMBER;
      hash = (53 * hash) + getEventCountsList().hashCode();
    }
    hash = (29 * hash) + getUnknownFields().hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata parseFrom(
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
  public static Builder newBuilder(com.google.wireless.android.sdk.stats.EnergyRangeMetadata prototype) {
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
   * Protobuf type {@code android_studio.EnergyRangeMetadata}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:android_studio.EnergyRangeMetadata)
      com.google.wireless.android.sdk.stats.EnergyRangeMetadataOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EnergyRangeMetadata_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EnergyRangeMetadata_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.wireless.android.sdk.stats.EnergyRangeMetadata.class, com.google.wireless.android.sdk.stats.EnergyRangeMetadata.Builder.class);
    }

    // Construct using com.google.wireless.android.sdk.stats.EnergyRangeMetadata.newBuilder()
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
      if (eventCountsBuilder_ == null) {
        eventCounts_ = java.util.Collections.emptyList();
      } else {
        eventCounts_ = null;
        eventCountsBuilder_.clear();
      }
      bitField0_ = (bitField0_ & ~0x00000001);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.wireless.android.sdk.stats.AndroidStudioStats.internal_static_android_studio_EnergyRangeMetadata_descriptor;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.EnergyRangeMetadata getDefaultInstanceForType() {
      return com.google.wireless.android.sdk.stats.EnergyRangeMetadata.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.EnergyRangeMetadata build() {
      com.google.wireless.android.sdk.stats.EnergyRangeMetadata result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.wireless.android.sdk.stats.EnergyRangeMetadata buildPartial() {
      com.google.wireless.android.sdk.stats.EnergyRangeMetadata result = new com.google.wireless.android.sdk.stats.EnergyRangeMetadata(this);
      buildPartialRepeatedFields(result);
      if (bitField0_ != 0) { buildPartial0(result); }
      onBuilt();
      return result;
    }

    private void buildPartialRepeatedFields(com.google.wireless.android.sdk.stats.EnergyRangeMetadata result) {
      if (eventCountsBuilder_ == null) {
        if (((bitField0_ & 0x00000001) != 0)) {
          eventCounts_ = java.util.Collections.unmodifiableList(eventCounts_);
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.eventCounts_ = eventCounts_;
      } else {
        result.eventCounts_ = eventCountsBuilder_.build();
      }
    }

    private void buildPartial0(com.google.wireless.android.sdk.stats.EnergyRangeMetadata result) {
      int from_bitField0_ = bitField0_;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.google.wireless.android.sdk.stats.EnergyRangeMetadata) {
        return mergeFrom((com.google.wireless.android.sdk.stats.EnergyRangeMetadata)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.wireless.android.sdk.stats.EnergyRangeMetadata other) {
      if (other == com.google.wireless.android.sdk.stats.EnergyRangeMetadata.getDefaultInstance()) return this;
      if (eventCountsBuilder_ == null) {
        if (!other.eventCounts_.isEmpty()) {
          if (eventCounts_.isEmpty()) {
            eventCounts_ = other.eventCounts_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureEventCountsIsMutable();
            eventCounts_.addAll(other.eventCounts_);
          }
          onChanged();
        }
      } else {
        if (!other.eventCounts_.isEmpty()) {
          if (eventCountsBuilder_.isEmpty()) {
            eventCountsBuilder_.dispose();
            eventCountsBuilder_ = null;
            eventCounts_ = other.eventCounts_;
            bitField0_ = (bitField0_ & ~0x00000001);
            eventCountsBuilder_ = 
              com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders ?
                 getEventCountsFieldBuilder() : null;
          } else {
            eventCountsBuilder_.addAllMessages(other.eventCounts_);
          }
        }
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
            case 10: {
              com.google.wireless.android.sdk.stats.EnergyEventCount m =
                  input.readMessage(
                      com.google.wireless.android.sdk.stats.EnergyEventCount.PARSER,
                      extensionRegistry);
              if (eventCountsBuilder_ == null) {
                ensureEventCountsIsMutable();
                eventCounts_.add(m);
              } else {
                eventCountsBuilder_.addMessage(m);
              }
              break;
            } // case 10
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

    private java.util.List<com.google.wireless.android.sdk.stats.EnergyEventCount> eventCounts_ =
      java.util.Collections.emptyList();
    private void ensureEventCountsIsMutable() {
      if (!((bitField0_ & 0x00000001) != 0)) {
        eventCounts_ = new java.util.ArrayList<com.google.wireless.android.sdk.stats.EnergyEventCount>(eventCounts_);
        bitField0_ |= 0x00000001;
       }
    }

    private com.google.protobuf.RepeatedFieldBuilderV3<
        com.google.wireless.android.sdk.stats.EnergyEventCount, com.google.wireless.android.sdk.stats.EnergyEventCount.Builder, com.google.wireless.android.sdk.stats.EnergyEventCountOrBuilder> eventCountsBuilder_;

    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public java.util.List<com.google.wireless.android.sdk.stats.EnergyEventCount> getEventCountsList() {
      if (eventCountsBuilder_ == null) {
        return java.util.Collections.unmodifiableList(eventCounts_);
      } else {
        return eventCountsBuilder_.getMessageList();
      }
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public int getEventCountsCount() {
      if (eventCountsBuilder_ == null) {
        return eventCounts_.size();
      } else {
        return eventCountsBuilder_.getCount();
      }
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public com.google.wireless.android.sdk.stats.EnergyEventCount getEventCounts(int index) {
      if (eventCountsBuilder_ == null) {
        return eventCounts_.get(index);
      } else {
        return eventCountsBuilder_.getMessage(index);
      }
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder setEventCounts(
        int index, com.google.wireless.android.sdk.stats.EnergyEventCount value) {
      if (eventCountsBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureEventCountsIsMutable();
        eventCounts_.set(index, value);
        onChanged();
      } else {
        eventCountsBuilder_.setMessage(index, value);
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder setEventCounts(
        int index, com.google.wireless.android.sdk.stats.EnergyEventCount.Builder builderForValue) {
      if (eventCountsBuilder_ == null) {
        ensureEventCountsIsMutable();
        eventCounts_.set(index, builderForValue.build());
        onChanged();
      } else {
        eventCountsBuilder_.setMessage(index, builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder addEventCounts(com.google.wireless.android.sdk.stats.EnergyEventCount value) {
      if (eventCountsBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureEventCountsIsMutable();
        eventCounts_.add(value);
        onChanged();
      } else {
        eventCountsBuilder_.addMessage(value);
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder addEventCounts(
        int index, com.google.wireless.android.sdk.stats.EnergyEventCount value) {
      if (eventCountsBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureEventCountsIsMutable();
        eventCounts_.add(index, value);
        onChanged();
      } else {
        eventCountsBuilder_.addMessage(index, value);
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder addEventCounts(
        com.google.wireless.android.sdk.stats.EnergyEventCount.Builder builderForValue) {
      if (eventCountsBuilder_ == null) {
        ensureEventCountsIsMutable();
        eventCounts_.add(builderForValue.build());
        onChanged();
      } else {
        eventCountsBuilder_.addMessage(builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder addEventCounts(
        int index, com.google.wireless.android.sdk.stats.EnergyEventCount.Builder builderForValue) {
      if (eventCountsBuilder_ == null) {
        ensureEventCountsIsMutable();
        eventCounts_.add(index, builderForValue.build());
        onChanged();
      } else {
        eventCountsBuilder_.addMessage(index, builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder addAllEventCounts(
        java.lang.Iterable<? extends com.google.wireless.android.sdk.stats.EnergyEventCount> values) {
      if (eventCountsBuilder_ == null) {
        ensureEventCountsIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, eventCounts_);
        onChanged();
      } else {
        eventCountsBuilder_.addAllMessages(values);
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder clearEventCounts() {
      if (eventCountsBuilder_ == null) {
        eventCounts_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
      } else {
        eventCountsBuilder_.clear();
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public Builder removeEventCounts(int index) {
      if (eventCountsBuilder_ == null) {
        ensureEventCountsIsMutable();
        eventCounts_.remove(index);
        onChanged();
      } else {
        eventCountsBuilder_.remove(index);
      }
      return this;
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public com.google.wireless.android.sdk.stats.EnergyEventCount.Builder getEventCountsBuilder(
        int index) {
      return getEventCountsFieldBuilder().getBuilder(index);
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public com.google.wireless.android.sdk.stats.EnergyEventCountOrBuilder getEventCountsOrBuilder(
        int index) {
      if (eventCountsBuilder_ == null) {
        return eventCounts_.get(index);  } else {
        return eventCountsBuilder_.getMessageOrBuilder(index);
      }
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public java.util.List<? extends com.google.wireless.android.sdk.stats.EnergyEventCountOrBuilder> 
         getEventCountsOrBuilderList() {
      if (eventCountsBuilder_ != null) {
        return eventCountsBuilder_.getMessageOrBuilderList();
      } else {
        return java.util.Collections.unmodifiableList(eventCounts_);
      }
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public com.google.wireless.android.sdk.stats.EnergyEventCount.Builder addEventCountsBuilder() {
      return getEventCountsFieldBuilder().addBuilder(
          com.google.wireless.android.sdk.stats.EnergyEventCount.getDefaultInstance());
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public com.google.wireless.android.sdk.stats.EnergyEventCount.Builder addEventCountsBuilder(
        int index) {
      return getEventCountsFieldBuilder().addBuilder(
          index, com.google.wireless.android.sdk.stats.EnergyEventCount.getDefaultInstance());
    }
    /**
     * <code>repeated .android_studio.EnergyEventCount event_counts = 1;</code>
     */
    public java.util.List<com.google.wireless.android.sdk.stats.EnergyEventCount.Builder> 
         getEventCountsBuilderList() {
      return getEventCountsFieldBuilder().getBuilderList();
    }
    private com.google.protobuf.RepeatedFieldBuilderV3<
        com.google.wireless.android.sdk.stats.EnergyEventCount, com.google.wireless.android.sdk.stats.EnergyEventCount.Builder, com.google.wireless.android.sdk.stats.EnergyEventCountOrBuilder> 
        getEventCountsFieldBuilder() {
      if (eventCountsBuilder_ == null) {
        eventCountsBuilder_ = new com.google.protobuf.RepeatedFieldBuilderV3<
            com.google.wireless.android.sdk.stats.EnergyEventCount, com.google.wireless.android.sdk.stats.EnergyEventCount.Builder, com.google.wireless.android.sdk.stats.EnergyEventCountOrBuilder>(
                eventCounts_,
                ((bitField0_ & 0x00000001) != 0),
                getParentForChildren(),
                isClean());
        eventCounts_ = null;
      }
      return eventCountsBuilder_;
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


    // @@protoc_insertion_point(builder_scope:android_studio.EnergyRangeMetadata)
  }

  // @@protoc_insertion_point(class_scope:android_studio.EnergyRangeMetadata)
  private static final com.google.wireless.android.sdk.stats.EnergyRangeMetadata DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.google.wireless.android.sdk.stats.EnergyRangeMetadata();
  }

  public static com.google.wireless.android.sdk.stats.EnergyRangeMetadata getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  @java.lang.Deprecated public static final com.google.protobuf.Parser<EnergyRangeMetadata>
      PARSER = new com.google.protobuf.AbstractParser<EnergyRangeMetadata>() {
    @java.lang.Override
    public EnergyRangeMetadata parsePartialFrom(
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

  public static com.google.protobuf.Parser<EnergyRangeMetadata> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<EnergyRangeMetadata> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.wireless.android.sdk.stats.EnergyRangeMetadata getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

