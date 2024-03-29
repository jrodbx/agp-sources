// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: cxx_attribution.proto

package com.android.build.gradle.internal.cxx.attribution;

public final class CxxAttribution {
  private CxxAttribution() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_BuildTaskAttribution_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_BuildTaskAttribution_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_EncodedBuildTaskAttribution_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_EncodedBuildTaskAttribution_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_AttributionKey_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_AttributionKey_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_EncodedAttributionKey_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_EncodedAttributionKey_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_BuildTaskAttributions_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_BuildTaskAttributions_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_EncodedBuildTaskAttributions_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_EncodedBuildTaskAttributions_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\025cxx_attribution.proto\"e\n\024BuildTaskAttr" +
      "ibution\022\023\n\013output_file\030\001 \001(\t\022\034\n\024start_ti" +
      "me_offset_ms\030\003 \001(\005\022\032\n\022end_time_offset_ms" +
      "\030\004 \001(\005\"o\n\033EncodedBuildTaskAttribution\022\026\n" +
      "\016output_file_id\030\001 \001(\005\022\034\n\024start_time_offs" +
      "et_ms\030\003 \001(\005\022\032\n\022end_time_offset_ms\030\004 \001(\005\"" +
      ">\n\016AttributionKey\022\016\n\006module\030\001 \001(\t\022\017\n\007var" +
      "iant\030\002 \001(\t\022\013\n\003abi\030\003 \001(\t\"N\n\025EncodedAttrib" +
      "utionKey\022\021\n\tmodule_id\030\001 \001(\005\022\022\n\nvariant_i" +
      "d\030\002 \001(\005\022\016\n\006abi_id\030\003 \001(\005\"\303\001\n\025BuildTaskAtt" +
      "ributions\022\034\n\003key\030\001 \001(\0132\017.AttributionKey\022" +
      "\024\n\014build_folder\030\002 \001(\t\022\017\n\007library\030\003 \003(\t\022\034" +
      "\n\024ninja_log_start_line\030\004 \001(\005\022\033\n\023build_st" +
      "art_time_ms\030\005 \001(\003\022*\n\013attribution\030\006 \003(\0132\025" +
      ".BuildTaskAttribution\"\336\001\n\034EncodedBuildTa" +
      "skAttributions\022#\n\003key\030\001 \001(\0132\026.EncodedAtt" +
      "ributionKey\022\027\n\017build_folder_id\030\002 \001(\005\022\022\n\n" +
      "library_id\030\003 \003(\005\022\034\n\024ninja_log_start_line" +
      "\030\004 \001(\005\022\033\n\023build_start_time_ms\030\005 \001(\003\0221\n\013a" +
      "ttribution\030\006 \003(\0132\034.EncodedBuildTaskAttri" +
      "butionB5\n1com.android.build.gradle.inter" +
      "nal.cxx.attributionP\001b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_BuildTaskAttribution_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_BuildTaskAttribution_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_BuildTaskAttribution_descriptor,
        new java.lang.String[] { "OutputFile", "StartTimeOffsetMs", "EndTimeOffsetMs", });
    internal_static_EncodedBuildTaskAttribution_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_EncodedBuildTaskAttribution_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_EncodedBuildTaskAttribution_descriptor,
        new java.lang.String[] { "OutputFileId", "StartTimeOffsetMs", "EndTimeOffsetMs", });
    internal_static_AttributionKey_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_AttributionKey_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_AttributionKey_descriptor,
        new java.lang.String[] { "Module", "Variant", "Abi", });
    internal_static_EncodedAttributionKey_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_EncodedAttributionKey_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_EncodedAttributionKey_descriptor,
        new java.lang.String[] { "ModuleId", "VariantId", "AbiId", });
    internal_static_BuildTaskAttributions_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_BuildTaskAttributions_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_BuildTaskAttributions_descriptor,
        new java.lang.String[] { "Key", "BuildFolder", "Library", "NinjaLogStartLine", "BuildStartTimeMs", "Attribution", });
    internal_static_EncodedBuildTaskAttributions_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_EncodedBuildTaskAttributions_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_EncodedBuildTaskAttributions_descriptor,
        new java.lang.String[] { "Key", "BuildFolderId", "LibraryId", "NinjaLogStartLine", "BuildStartTimeMs", "Attribution", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
