package com.softeno.template

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement
import reactor.core.publisher.Hooks

@SpringBootApplication
@EnableTransactionManagement
@EnableConfigurationProperties
@EnableR2dbcRepositories
@ConfigurationPropertiesScan("com.softeno")
class SoftenoMvcJpaApp

fun main(args: Array<String>) {
	Hooks.enableAutomaticContextPropagation()
	runApplication<SoftenoMvcJpaApp>(*args)
}

