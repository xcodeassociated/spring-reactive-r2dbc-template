package com.softeno.template.sample.proto

import com.softeno.template.grpc.SampleGrpcServiceGrpcKt
import com.softeno.template.grpc.SampleRequest
import com.softeno.template.grpc.SampleResponse
import io.grpc.Channel
import org.apache.commons.logging.LogFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.grpc.server.service.GrpcService

@GrpcService
class SampleGrpcServiceImpl :
    SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineImplBase() {
    private val log = LogFactory.getLog(javaClass)

    override suspend fun echo(request: SampleRequest): SampleResponse {
        log.info("[grpc]: echo: $request")

        return SampleResponse.newBuilder()
            .setData("Echo: ${request.data}")
            .build()
    }
}

@Configuration
class GrpcClientConfig(
    private val channelFactory: GrpcChannelFactory
) {

    @Bean
    fun selfStub(): SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub {
        val channel: Channel = channelFactory.createChannel("external")
        return SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub(channel)
    }
}