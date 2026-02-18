package com.softeno.template

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.transaction.annotation.EnableTransactionManagement
import reactor.core.publisher.Hooks

@SpringBootApplication
@EnableTransactionManagement
@EnableConfigurationProperties
@EnableR2dbcRepositories
@EnableR2dbcAuditing
@EnableKafka
@ComponentScan("com.softeno")
@ConfigurationPropertiesScan("com.softeno")
class SoftenoMvcJpaApp

fun main(args: Array<String>) {
	Hooks.enableAutomaticContextPropagation()
	runApplication<SoftenoMvcJpaApp>(*args)
}
