package com.softeno.template.app.config.security

import org.apache.commons.logging.LogFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.ReactiveAuditorAware
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt
import reactor.core.publisher.Mono
import kotlin.jvm.javaClass


class AuditorAwareImpl : ReactiveAuditorAware<String> {
    private val log = LogFactory.getLog(javaClass)

    override fun getCurrentAuditor(): Mono<String> {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getPrincipal)
            .switchIfEmpty(Mono.just("anonymous"))
            .flatMap { principal ->
                if (principal is Jwt) {
                    Mono.just(principal.claims["sub"] as String)
                } else {
                    Mono.just("anonymous")
                }
            }
    }
}

@Configuration
@EnableR2dbcAuditing(auditorAwareRef = "auditorProvider")
internal class ReactiveMongoAuditorConfig {
    @Bean
    fun auditorProvider(): ReactiveAuditorAware<String> {
        return AuditorAwareImpl()
    }
}