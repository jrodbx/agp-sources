// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: src/main/proto/sync_variant.proto

package com.android.ide.model.sync;

/**
 * <pre>
 * Dynamic Feature VariantType specific model.
 * </pre>
 *
 * Protobuf type {@code DynamicFeatureVariantModel}
 */
public  final class DynamicFeatureVariantModel extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:DynamicFeatureVariantModel)
    DynamicFeatureVariantModelOrBuilder {
private static final long serialVersionUID = 0L;
  // Use DynamicFeatureVariantModel.newBuilder() to construct.
  private DynamicFeatureVariantModel(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private DynamicFeatureVariantModel() {
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new DynamicFeatureVariantModel();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private DynamicFeatureVariantModel(
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
            com.android.ide.model.sync.ModuleVariantModel.Builder subBuilder = null;
            if (moduleCommonModel_ != null) {
              subBuilder = moduleCommonModel_.toBuilder();
            }
            moduleCommonModel_ = input.readMessage(com.android.ide.model.sync.ModuleVariantModel.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(moduleCommonModel_);
              moduleCommonModel_ = subBuilder.buildPartial();
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
    return com.android.ide.model.sync.SyncVariant.internal_static_DynamicFeatureVariantModel_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.android.ide.model.sync.SyncVariant.internal_static_DynamicFeatureVariantModel_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.android.ide.model.sync.DynamicFeatureVariantModel.class, com.android.ide.model.sync.DynamicFeatureVariantModel.Builder.class);
  }

  public static final int MODULECOMMONMODEL_FIELD_NUMBER = 1;
  private com.android.ide.model.sync.ModuleVariantModel moduleCommonModel_;
  /**
   * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
   * @return Whether the moduleCommonModel field is set.
   */
  public boolean hasModuleCommonModel() {
    return moduleCommonModel_ != null;
  }
  /**
   * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
   * @return The moduleCommonModel.
   */
  public com.android.ide.model.sync.ModuleVariantModel getModuleCommonModel() {
    return moduleCommonModel_ == null ? com.android.ide.model.sync.ModuleVariantModel.getDefaultInstance() : moduleCommonModel_;
  }
  /**
   * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
   */
  public com.android.ide.model.sync.ModuleVariantModelOrBuilder getModuleCommonModelOrBuilder() {
    return getModuleCommonModel();
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
    if (moduleCommonModel_ != null) {
      output.writeMessage(1, getModuleCommonModel());
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (moduleCommonModel_ != null) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(1, getModuleCommonModel());
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
    if (!(obj instanceof com.android.ide.model.sync.DynamicFeatureVariantModel)) {
      return super.equals(obj);
    }
    com.android.ide.model.sync.DynamicFeatureVariantModel other = (com.android.ide.model.sync.DynamicFeatureVariantModel) obj;

    if (hasModuleCommonModel() != other.hasModuleCommonModel()) return false;
    if (hasModuleCommonModel()) {
      if (!getModuleCommonModel()
          .equals(other.getModuleCommonModel())) return false;
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
    if (hasModuleCommonModel()) {
      hash = (37 * hash) + MODULECOMMONMODEL_FIELD_NUMBER;
      hash = (53 * hash) + getModuleCommonModel().hashCode();
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.android.ide.model.sync.DynamicFeatureVariantModel parseFrom(
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
  public static Builder newBuilder(com.android.ide.model.sync.DynamicFeatureVariantModel prototype) {
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
   * Dynamic Feature VariantType specific model.
   * </pre>
   *
   * Protobuf type {@code DynamicFeatureVariantModel}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:DynamicFeatureVariantModel)
      com.android.ide.model.sync.DynamicFeatureVariantModelOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.android.ide.model.sync.SyncVariant.internal_static_DynamicFeatureVariantModel_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.android.ide.model.sync.SyncVariant.internal_static_DynamicFeatureVariantModel_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.android.ide.model.sync.DynamicFeatureVariantModel.class, com.android.ide.model.sync.DynamicFeatureVariantModel.Builder.class);
    }

    // Construct using com.android.ide.model.sync.DynamicFeatureVariantModel.newBuilder()
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
      if (moduleCommonModelBuilder_ == null) {
        moduleCommonModel_ = null;
      } else {
        moduleCommonModel_ = null;
        moduleCommonModelBuilder_ = null;
      }
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.android.ide.model.sync.SyncVariant.internal_static_DynamicFeatureVariantModel_descriptor;
    }

    @java.lang.Override
    public com.android.ide.model.sync.DynamicFeatureVariantModel getDefaultInstanceForType() {
      return com.android.ide.model.sync.DynamicFeatureVariantModel.getDefaultInstance();
    }

    @java.lang.Override
    public com.android.ide.model.sync.DynamicFeatureVariantModel build() {
      com.android.ide.model.sync.DynamicFeatureVariantModel result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.android.ide.model.sync.DynamicFeatureVariantModel buildPartial() {
      com.android.ide.model.sync.DynamicFeatureVariantModel result = new com.android.ide.model.sync.DynamicFeatureVariantModel(this);
      if (moduleCommonModelBuilder_ == null) {
        result.moduleCommonModel_ = moduleCommonModel_;
      } else {
        result.moduleCommonModel_ = moduleCommonModelBuilder_.build();
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
      if (other instanceof com.android.ide.model.sync.DynamicFeatureVariantModel) {
        return mergeFrom((com.android.ide.model.sync.DynamicFeatureVariantModel)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.android.ide.model.sync.DynamicFeatureVariantModel other) {
      if (other == com.android.ide.model.sync.DynamicFeatureVariantModel.getDefaultInstance()) return this;
      if (other.hasModuleCommonModel()) {
        mergeModuleCommonModel(other.getModuleCommonModel());
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
      com.android.ide.model.sync.DynamicFeatureVariantModel parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.android.ide.model.sync.DynamicFeatureVariantModel) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private com.android.ide.model.sync.ModuleVariantModel moduleCommonModel_;
    private com.google.protobuf.SingleFieldBuilderV3<
        com.android.ide.model.sync.ModuleVariantModel, com.android.ide.model.sync.ModuleVariantModel.Builder, com.android.ide.model.sync.ModuleVariantModelOrBuilder> moduleCommonModelBuilder_;
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     * @return Whether the moduleCommonModel field is set.
     */
    public boolean hasModuleCommonModel() {
      return moduleCommonModelBuilder_ != null || moduleCommonModel_ != null;
    }
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     * @return The moduleCommonModel.
     */
    public com.android.ide.model.sync.ModuleVariantModel getModuleCommonModel() {
      if (moduleCommonModelBuilder_ == null) {
        return moduleCommonModel_ == null ? com.android.ide.model.sync.ModuleVariantModel.getDefaultInstance() : moduleCommonModel_;
      } else {
        return moduleCommonModelBuilder_.getMessage();
      }
    }
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     */
    public Builder setModuleCommonModel(com.android.ide.model.sync.ModuleVariantModel value) {
      if (moduleCommonModelBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        moduleCommonModel_ = value;
        onChanged();
      } else {
        moduleCommonModelBuilder_.setMessage(value);
      }

      return this;
    }
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     */
    public Builder setModuleCommonModel(
        com.android.ide.model.sync.ModuleVariantModel.Builder builderForValue) {
      if (moduleCommonModelBuilder_ == null) {
        moduleCommonModel_ = builderForValue.build();
        onChanged();
      } else {
        moduleCommonModelBuilder_.setMessage(builderForValue.build());
      }

      return this;
    }
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     */
    public Builder mergeModuleCommonModel(com.android.ide.model.sync.ModuleVariantModel value) {
      if (moduleCommonModelBuilder_ == null) {
        if (moduleCommonModel_ != null) {
          moduleCommonModel_ =
            com.android.ide.model.sync.ModuleVariantModel.newBuilder(moduleCommonModel_).mergeFrom(value).buildPartial();
        } else {
          moduleCommonModel_ = value;
        }
        onChanged();
      } else {
        moduleCommonModelBuilder_.mergeFrom(value);
      }

      return this;
    }
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     */
    public Builder clearModuleCommonModel() {
      if (moduleCommonModelBuilder_ == null) {
        moduleCommonModel_ = null;
        onChanged();
      } else {
        moduleCommonModel_ = null;
        moduleCommonModelBuilder_ = null;
      }

      return this;
    }
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     */
    public com.android.ide.model.sync.ModuleVariantModel.Builder getModuleCommonModelBuilder() {

      onChanged();
      return getModuleCommonModelFieldBuilder().getBuilder();
    }
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     */
    public com.android.ide.model.sync.ModuleVariantModelOrBuilder getModuleCommonModelOrBuilder() {
      if (moduleCommonModelBuilder_ != null) {
        return moduleCommonModelBuilder_.getMessageOrBuilder();
      } else {
        return moduleCommonModel_ == null ?
            com.android.ide.model.sync.ModuleVariantModel.getDefaultInstance() : moduleCommonModel_;
      }
    }
    /**
     * <code>.ModuleVariantModel moduleCommonModel = 1;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<
        com.android.ide.model.sync.ModuleVariantModel, com.android.ide.model.sync.ModuleVariantModel.Builder, com.android.ide.model.sync.ModuleVariantModelOrBuilder>
        getModuleCommonModelFieldBuilder() {
      if (moduleCommonModelBuilder_ == null) {
        moduleCommonModelBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<
            com.android.ide.model.sync.ModuleVariantModel, com.android.ide.model.sync.ModuleVariantModel.Builder, com.android.ide.model.sync.ModuleVariantModelOrBuilder>(
                getModuleCommonModel(),
                getParentForChildren(),
                isClean());
        moduleCommonModel_ = null;
      }
      return moduleCommonModelBuilder_;
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


    // @@protoc_insertion_point(builder_scope:DynamicFeatureVariantModel)
  }

  // @@protoc_insertion_point(class_scope:DynamicFeatureVariantModel)
  private static final com.android.ide.model.sync.DynamicFeatureVariantModel DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.android.ide.model.sync.DynamicFeatureVariantModel();
  }

  public static com.android.ide.model.sync.DynamicFeatureVariantModel getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<DynamicFeatureVariantModel>
      PARSER = new com.google.protobuf.AbstractParser<DynamicFeatureVariantModel>() {
    @java.lang.Override
    public DynamicFeatureVariantModel parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new DynamicFeatureVariantModel(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<DynamicFeatureVariantModel> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<DynamicFeatureVariantModel> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.android.ide.model.sync.DynamicFeatureVariantModel getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

