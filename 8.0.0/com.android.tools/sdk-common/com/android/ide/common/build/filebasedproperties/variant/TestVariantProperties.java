// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: src/main/proto/variant_properties.proto

package com.android.ide.common.build.filebasedproperties.variant;

/**
 * <pre>
 * Properties of Test variants.
 * </pre>
 *
 * Protobuf type {@code TestVariantProperties}
 */
public final class TestVariantProperties extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:TestVariantProperties)
    TestVariantPropertiesOrBuilder {
private static final long serialVersionUID = 0L;
  // Use TestVariantProperties.newBuilder() to construct.
  private TestVariantProperties(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private TestVariantProperties() {
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new TestVariantProperties();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private TestVariantProperties(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new java.lang.NullPointerException();
    }
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
            com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.Builder subBuilder = null;
            if (artifactOutputProperties_ != null) {
              subBuilder = artifactOutputProperties_.toBuilder();
            }
            artifactOutputProperties_ = input.readMessage(com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(artifactOutputProperties_);
              artifactOutputProperties_ = subBuilder.buildPartial();
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
    return com.android.ide.common.build.filebasedproperties.variant.VariantPropertiesOuterClass.internal_static_TestVariantProperties_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.android.ide.common.build.filebasedproperties.variant.VariantPropertiesOuterClass.internal_static_TestVariantProperties_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties.class, com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties.Builder.class);
  }

  public static final int ARTIFACTOUTPUTPROPERTIES_FIELD_NUMBER = 1;
  private com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties artifactOutputProperties_;
  /**
   * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
   * @return Whether the artifactOutputProperties field is set.
   */
  @java.lang.Override
  public boolean hasArtifactOutputProperties() {
    return artifactOutputProperties_ != null;
  }
  /**
   * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
   * @return The artifactOutputProperties.
   */
  @java.lang.Override
  public com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties getArtifactOutputProperties() {
    return artifactOutputProperties_ == null ? com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.getDefaultInstance() : artifactOutputProperties_;
  }
  /**
   * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
   */
  @java.lang.Override
  public com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputPropertiesOrBuilder getArtifactOutputPropertiesOrBuilder() {
    return getArtifactOutputProperties();
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
    if (artifactOutputProperties_ != null) {
      output.writeMessage(1, getArtifactOutputProperties());
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (artifactOutputProperties_ != null) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(1, getArtifactOutputProperties());
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
    if (!(obj instanceof com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties)) {
      return super.equals(obj);
    }
    com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties other = (com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties) obj;

    if (hasArtifactOutputProperties() != other.hasArtifactOutputProperties()) return false;
    if (hasArtifactOutputProperties()) {
      if (!getArtifactOutputProperties()
          .equals(other.getArtifactOutputProperties())) return false;
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
    if (hasArtifactOutputProperties()) {
      hash = (37 * hash) + ARTIFACTOUTPUTPROPERTIES_FIELD_NUMBER;
      hash = (53 * hash) + getArtifactOutputProperties().hashCode();
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parseFrom(
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
  public static Builder newBuilder(com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties prototype) {
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
   * Properties of Test variants.
   * </pre>
   *
   * Protobuf type {@code TestVariantProperties}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:TestVariantProperties)
      com.android.ide.common.build.filebasedproperties.variant.TestVariantPropertiesOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.android.ide.common.build.filebasedproperties.variant.VariantPropertiesOuterClass.internal_static_TestVariantProperties_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.android.ide.common.build.filebasedproperties.variant.VariantPropertiesOuterClass.internal_static_TestVariantProperties_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties.class, com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties.Builder.class);
    }

    // Construct using com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties.newBuilder()
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
      if (artifactOutputPropertiesBuilder_ == null) {
        artifactOutputProperties_ = null;
      } else {
        artifactOutputProperties_ = null;
        artifactOutputPropertiesBuilder_ = null;
      }
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.android.ide.common.build.filebasedproperties.variant.VariantPropertiesOuterClass.internal_static_TestVariantProperties_descriptor;
    }

    @java.lang.Override
    public com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties getDefaultInstanceForType() {
      return com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties.getDefaultInstance();
    }

    @java.lang.Override
    public com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties build() {
      com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties buildPartial() {
      com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties result = new com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties(this);
      if (artifactOutputPropertiesBuilder_ == null) {
        result.artifactOutputProperties_ = artifactOutputProperties_;
      } else {
        result.artifactOutputProperties_ = artifactOutputPropertiesBuilder_.build();
      }
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
      if (other instanceof com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties) {
        return mergeFrom((com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties other) {
      if (other == com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties.getDefaultInstance()) return this;
      if (other.hasArtifactOutputProperties()) {
        mergeArtifactOutputProperties(other.getArtifactOutputProperties());
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
      com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties artifactOutputProperties_;
    private com.google.protobuf.SingleFieldBuilderV3<
        com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties, com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.Builder, com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputPropertiesOrBuilder> artifactOutputPropertiesBuilder_;
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     * @return Whether the artifactOutputProperties field is set.
     */
    public boolean hasArtifactOutputProperties() {
      return artifactOutputPropertiesBuilder_ != null || artifactOutputProperties_ != null;
    }
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     * @return The artifactOutputProperties.
     */
    public com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties getArtifactOutputProperties() {
      if (artifactOutputPropertiesBuilder_ == null) {
        return artifactOutputProperties_ == null ? com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.getDefaultInstance() : artifactOutputProperties_;
      } else {
        return artifactOutputPropertiesBuilder_.getMessage();
      }
    }
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     */
    public Builder setArtifactOutputProperties(com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties value) {
      if (artifactOutputPropertiesBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        artifactOutputProperties_ = value;
        onChanged();
      } else {
        artifactOutputPropertiesBuilder_.setMessage(value);
      }

      return this;
    }
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     */
    public Builder setArtifactOutputProperties(
        com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.Builder builderForValue) {
      if (artifactOutputPropertiesBuilder_ == null) {
        artifactOutputProperties_ = builderForValue.build();
        onChanged();
      } else {
        artifactOutputPropertiesBuilder_.setMessage(builderForValue.build());
      }

      return this;
    }
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     */
    public Builder mergeArtifactOutputProperties(com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties value) {
      if (artifactOutputPropertiesBuilder_ == null) {
        if (artifactOutputProperties_ != null) {
          artifactOutputProperties_ =
            com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.newBuilder(artifactOutputProperties_).mergeFrom(value).buildPartial();
        } else {
          artifactOutputProperties_ = value;
        }
        onChanged();
      } else {
        artifactOutputPropertiesBuilder_.mergeFrom(value);
      }

      return this;
    }
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     */
    public Builder clearArtifactOutputProperties() {
      if (artifactOutputPropertiesBuilder_ == null) {
        artifactOutputProperties_ = null;
        onChanged();
      } else {
        artifactOutputProperties_ = null;
        artifactOutputPropertiesBuilder_ = null;
      }

      return this;
    }
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     */
    public com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.Builder getArtifactOutputPropertiesBuilder() {

      onChanged();
      return getArtifactOutputPropertiesFieldBuilder().getBuilder();
    }
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     */
    public com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputPropertiesOrBuilder getArtifactOutputPropertiesOrBuilder() {
      if (artifactOutputPropertiesBuilder_ != null) {
        return artifactOutputPropertiesBuilder_.getMessageOrBuilder();
      } else {
        return artifactOutputProperties_ == null ?
            com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.getDefaultInstance() : artifactOutputProperties_;
      }
    }
    /**
     * <code>.ArtifactOutputProperties artifactOutputProperties = 1;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<
        com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties, com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.Builder, com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputPropertiesOrBuilder>
        getArtifactOutputPropertiesFieldBuilder() {
      if (artifactOutputPropertiesBuilder_ == null) {
        artifactOutputPropertiesBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<
            com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties, com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputProperties.Builder, com.android.ide.common.build.filebasedproperties.variant.ArtifactOutputPropertiesOrBuilder>(
                getArtifactOutputProperties(),
                getParentForChildren(),
                isClean());
        artifactOutputProperties_ = null;
      }
      return artifactOutputPropertiesBuilder_;
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


    // @@protoc_insertion_point(builder_scope:TestVariantProperties)
  }

  // @@protoc_insertion_point(class_scope:TestVariantProperties)
  private static final com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties();
  }

  public static com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<TestVariantProperties>
      PARSER = new com.google.protobuf.AbstractParser<TestVariantProperties>() {
    @java.lang.Override
    public TestVariantProperties parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new TestVariantProperties(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<TestVariantProperties> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<TestVariantProperties> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.android.ide.common.build.filebasedproperties.variant.TestVariantProperties getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

