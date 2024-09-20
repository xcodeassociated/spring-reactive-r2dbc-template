package com.softeno.template.sample.http.api

import org.apache.commons.logging.LogFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.RequestPredicates.*
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono

@Configuration
class SampleRouter {
    // OAuth2 secured sample resource
    @Bean
    fun routes(service: SampleService): RouterFunction<ServerResponse> {
        return route(GET("/sample-secured/{id}")) { req ->
            ok().body(service.getHandler(req.pathVariable("id")))
        }.andRoute(POST("/sample-secured")) { req ->
            req.bodyToMono(SampleResponseDto::class.java).map {
                service.postHandler(it)
            }.flatMap { ok().body(it) }
        }.andRoute(PUT("/sample-secured/{id}")) { req ->
            req.bodyToMono(SampleResponseDto::class.java).map {
                service.putHandler(req.pathVariable("id"), it)
            }.flatMap { ok().bodyValue(it) }
        }.andRoute(DELETE("/sample-secured/{id}")) { req ->
            ok().body(service.deleteHandler(req.pathVariable("id")))
        }
    }
}

@Service
class SampleService {
    private val log = LogFactory.getLog(javaClass)

    fun postHandler(request: SampleResponseDto): Mono<SampleResponseDto> {
        log.info("[sample-service]: POST request: $request")
        return Mono.just(request)
    }

    fun getHandler(id: String): Mono<SampleResponseDto> {
        log.info("[sample-service]: GET id: $id")
        return Mono.just(SampleResponseDto(data = id))
    }

    fun putHandler(id: String, request: SampleResponseDto): Mono<SampleResponseDto> {
        log.info("[sample-service]: PUT id: $id, request: $request")
        return Mono.just(request)
    }

    fun deleteHandler(id: String): Mono<Void> {
        log.info("[sample-service]: GET id: $id")
        return Mono.empty()
    }

}