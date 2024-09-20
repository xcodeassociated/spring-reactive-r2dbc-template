package com.softeno.template.sample.http.api

import com.softeno.template.sample.http.config.ExternalClientConfig
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

@RestController
@RequestMapping("/external")
@Validated
class ExternalController(
    @Qualifier(value = "external") private val webClient: WebClient,
    private val reactiveCircuitBreakerFactory: ReactiveCircuitBreakerFactory<*, *>,
    private val config: ExternalClientConfig
) {
    private val log = LogFactory.getLog(javaClass)

    @GetMapping("/{id}")
    suspend fun getHandler(@PathVariable id: String): SampleResponseDto {
        log.info("[external]: GET id: $id")
        val response: SampleResponseDto = webClient.get()
            .uri("/${id}")
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .timeout(Duration.ofMillis(1_000))
            .transform {
                val rcb = reactiveCircuitBreakerFactory.create(config.name)
                // note: custom exception might be thrown since exception handler is defined
                rcb.run(it) { Mono.just(SampleResponseDto(data = "FALLBACK")) }
            }
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

}