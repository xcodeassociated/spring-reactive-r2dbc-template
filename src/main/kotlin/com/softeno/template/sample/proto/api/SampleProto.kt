package com.softeno.template.sample.proto.api

import com.softeno.template.grpc.SampleGrpcServiceGrpcKt
import com.softeno.template.grpc.SampleRequest
import com.softeno.template.grpc.SampleResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import org.apache.commons.logging.LogFactory
import org.springframework.grpc.server.service.GrpcService

@GrpcService
class SampleGrpcServiceImpl :
    SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineImplBase() {
    private val log = LogFactory.getLog(javaClass)

    // Unary
    override suspend fun echo(request: SampleRequest): SampleResponse {
        return SampleResponse.newBuilder()
            .setData("Echo: ${request.data}")
            .build()
    }

    // Server Streaming
    override fun echoServerStream(request: SampleRequest): Flow<SampleResponse> = flow {
        repeat(5) { index ->
            emit(
                SampleResponse.newBuilder()
                    .setData("Stream[$index]: ${request.data}")
                    .build()
            )
            delay(500) // simulate async work
        }
    }

    // Client Streaming
    override suspend fun echoClientStream(
        requests: Flow<SampleRequest>
    ): SampleResponse {

        val collected = requests
            .toList()
            .joinToString(", ") { it.data }

        return SampleResponse.newBuilder()
            .setData("Collected: $collected")
            .build()
    }

    // Bidirectional Streaming
    override fun echoBidirectional(
        requests: Flow<SampleRequest>
    ): Flow<SampleResponse> = flow {

        requests.collect { incoming ->
            emit(
                SampleResponse.newBuilder()
                    .setData("Reply: ${incoming.data}")
                    .build()
            )
        }
    }
}

