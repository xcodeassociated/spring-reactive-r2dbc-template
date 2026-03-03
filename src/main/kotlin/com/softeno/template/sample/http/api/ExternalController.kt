package com.softeno.template.sample.http.api

import com.softeno.template.grpc.SampleGrpcServiceGrpcKt
import com.softeno.template.grpc.SampleRequest
import com.softeno.template.sample.http.config.ExternalClientConfig
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.Duration

@RestController
@RequestMapping("/external")
@Validated
class ExternalController(
    @Qualifier(value = "external") private val webClient: WebClient,
    reactiveCircuitBreakerFactory: ReactiveCircuitBreakerFactory<*, *>,
    config: ExternalClientConfig,
    private val rcb: ReactiveCircuitBreaker = reactiveCircuitBreakerFactory.create(config.name),
    private val stub: SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub
) {
    private val log = LogFactory.getLog(javaClass)

    @GetMapping("/{id}")
    suspend fun getHandler(@PathVariable id: String): SampleResponseDto {

        log.info("[external]: GET id: $id")
        val response: SampleResponseDto = webClient.get()
            .uri("/${id}")
            .retrieve()
            .bodyToMono<SampleResponseDto>()
            .timeout(Duration.ofMillis(1_000))
            .transform {
                // note: a custom exception might be thrown since the exception handler is defined
                rcb.run(it) { Mono.just(SampleResponseDto(data = "FALLBACK")) }
            }
            .contextCapture()
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

    @GetMapping("/grpc/{data}")
    suspend fun getGrpcHandler(@PathVariable data: String): SampleResponseDto {
        log.info("[external]: GET grpc: $data")

        val response = stub.echo(
            SampleRequest.newBuilder()
                .setData(data)
                .build()
        ).data

        return SampleResponseDto(data = response)
    }

}