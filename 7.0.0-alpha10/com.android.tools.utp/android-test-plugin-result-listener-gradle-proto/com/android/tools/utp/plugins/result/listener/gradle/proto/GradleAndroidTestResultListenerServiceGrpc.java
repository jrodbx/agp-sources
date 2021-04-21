package com.android.tools.utp.plugins.result.listener.gradle.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.21.1)",
    comments = "Source: gradle_android_test_result_listener.proto")
public final class GradleAndroidTestResultListenerServiceGrpc {

  private GradleAndroidTestResultListenerServiceGrpc() {}

  public static final String SERVICE_NAME = "com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent,
      com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse> getRecordTestResultEventMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RecordTestResultEvent",
      requestType = com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent.class,
      responseType = com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
  public static io.grpc.MethodDescriptor<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent,
      com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse> getRecordTestResultEventMethod() {
    io.grpc.MethodDescriptor<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent, com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse> getRecordTestResultEventMethod;
    if ((getRecordTestResultEventMethod = GradleAndroidTestResultListenerServiceGrpc.getRecordTestResultEventMethod) == null) {
      synchronized (GradleAndroidTestResultListenerServiceGrpc.class) {
        if ((getRecordTestResultEventMethod = GradleAndroidTestResultListenerServiceGrpc.getRecordTestResultEventMethod) == null) {
          GradleAndroidTestResultListenerServiceGrpc.getRecordTestResultEventMethod = getRecordTestResultEventMethod =
              io.grpc.MethodDescriptor.<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent, com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
              .setFullMethodName(generateFullMethodName(
                  "com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerService", "RecordTestResultEvent"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new GradleAndroidTestResultListenerServiceMethodDescriptorSupplier("RecordTestResultEvent"))
                  .build();
          }
        }
     }
     return getRecordTestResultEventMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static GradleAndroidTestResultListenerServiceStub newStub(io.grpc.Channel channel) {
    return new GradleAndroidTestResultListenerServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static GradleAndroidTestResultListenerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new GradleAndroidTestResultListenerServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static GradleAndroidTestResultListenerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new GradleAndroidTestResultListenerServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class GradleAndroidTestResultListenerServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * RPC for recording test results. Android Gradle plugin starts this gRPC
     * server and UTP calls this RPC to record results in AGP.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent> recordTestResultEvent(
        io.grpc.stub.StreamObserver<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse> responseObserver) {
      return asyncUnimplementedStreamingCall(getRecordTestResultEventMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getRecordTestResultEventMethod(),
            asyncClientStreamingCall(
              new MethodHandlers<
                com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent,
                com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse>(
                  this, METHODID_RECORD_TEST_RESULT_EVENT)))
          .build();
    }
  }

  /**
   */
  public static final class GradleAndroidTestResultListenerServiceStub extends io.grpc.stub.AbstractStub<GradleAndroidTestResultListenerServiceStub> {
    private GradleAndroidTestResultListenerServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GradleAndroidTestResultListenerServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GradleAndroidTestResultListenerServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GradleAndroidTestResultListenerServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * RPC for recording test results. Android Gradle plugin starts this gRPC
     * server and UTP calls this RPC to record results in AGP.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent> recordTestResultEvent(
        io.grpc.stub.StreamObserver<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse> responseObserver) {
      return asyncClientStreamingCall(
          getChannel().newCall(getRecordTestResultEventMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   */
  public static final class GradleAndroidTestResultListenerServiceBlockingStub extends io.grpc.stub.AbstractStub<GradleAndroidTestResultListenerServiceBlockingStub> {
    private GradleAndroidTestResultListenerServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GradleAndroidTestResultListenerServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GradleAndroidTestResultListenerServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GradleAndroidTestResultListenerServiceBlockingStub(channel, callOptions);
    }
  }

  /**
   */
  public static final class GradleAndroidTestResultListenerServiceFutureStub extends io.grpc.stub.AbstractStub<GradleAndroidTestResultListenerServiceFutureStub> {
    private GradleAndroidTestResultListenerServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GradleAndroidTestResultListenerServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GradleAndroidTestResultListenerServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GradleAndroidTestResultListenerServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_RECORD_TEST_RESULT_EVENT = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final GradleAndroidTestResultListenerServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(GradleAndroidTestResultListenerServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_RECORD_TEST_RESULT_EVENT:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.recordTestResultEvent(
              (io.grpc.stub.StreamObserver<com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class GradleAndroidTestResultListenerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    GradleAndroidTestResultListenerServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("GradleAndroidTestResultListenerService");
    }
  }

  private static final class GradleAndroidTestResultListenerServiceFileDescriptorSupplier
      extends GradleAndroidTestResultListenerServiceBaseDescriptorSupplier {
    GradleAndroidTestResultListenerServiceFileDescriptorSupplier() {}
  }

  private static final class GradleAndroidTestResultListenerServiceMethodDescriptorSupplier
      extends GradleAndroidTestResultListenerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    GradleAndroidTestResultListenerServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (GradleAndroidTestResultListenerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new GradleAndroidTestResultListenerServiceFileDescriptorSupplier())
              .addMethod(getRecordTestResultEventMethod())
              .build();
        }
      }
    }
    return result;
  }
}
