package com.softeno.template.app.kafka.config

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.app.kafka.dto.KafkaMessage
import io.micrometer.observation.ObservationRegistry
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter
import org.springframework.kafka.support.converter.JsonMessageConverter
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.receiver.observation.KafkaReceiverObservation
import reactor.kafka.sender.SenderOptions
import reactor.kafka.sender.observation.KafkaSenderObservation
import java.util.*


@ConfigurationProperties(prefix = "com.softeno.kafka")
data class KafkaApplicationProperties(val tx: String, val rx: String, val keycloak: String)

@Configuration
class JsonMessageConverterConfig {
    @Bean
    fun jsonMessageConverter(): JsonMessageConverter {
        return ByteArrayJsonMessageConverter()
    }
}

@Configuration
class KafkaConsumerConfig {
    @Bean
    fun kafkaReceiverOptions(
        kafkaProperties: KafkaProperties,
        props: KafkaApplicationProperties,
        observationRegistry: ObservationRegistry
    ): ReceiverOptions<String, JsonNode> {
        val basicReceiverOptions: ReceiverOptions<String, JsonNode> =
            ReceiverOptions.create(kafkaProperties.buildConsumerProperties())
        val basicReceiverOptionsWithObs = basicReceiverOptions
            // todo: make better observation handling by reactive kafka, currently the zipkin does not show the traces properly
            .withObservation(observationRegistry, KafkaReceiverObservation.DefaultKafkaReceiverObservationConvention()
        )
        return basicReceiverOptionsWithObs.subscription(Collections.singletonList(props.rx))
    }

    @Bean
    fun kafkaConsumerTemplate(kafkaReceiverOptions: ReceiverOptions<String, JsonNode>): DefaultKafkaConsumerFactory<String, JsonNode> {
        return DefaultKafkaConsumerFactory(kafkaReceiverOptions.consumerProperties())
    }
}

@Configuration
class KafkaProducerConfig {
    @Bean
    fun kafkaProducerTemplate(properties: KafkaProperties, observationRegistry: ObservationRegistry): ProducerFactory<String, KafkaMessage> {
        val props = properties.buildProducerProperties()
        val options = SenderOptions.create<String, KafkaMessage>(props)
            // todo: make better observation handling by reactive kafka, currently the zipkin does not show the traces properly
            .withObservation(observationRegistry, KafkaSenderObservation.DefaultKafkaSenderObservationConvention())
        return DefaultKafkaProducerFactory(options.producerProperties())
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, KafkaMessage>): KafkaTemplate<String, KafkaMessage> {
        return KafkaTemplate(producerFactory)
    }
}

//@Configuration
//class ReactiveKafkaKeycloakConsumerConfig {
//    @Bean(value = ["kafkaKeycloakOptions"])
//    fun kafkaReceiverOptions(
//        kafkaProperties: KafkaProperties,
//        props: KafkaApplicationProperties
//    ): ReceiverOptions<String, JsonNode> {
//        val basicReceiverOptions: ReceiverOptions<String, JsonNode> =
//            ReceiverOptions.create(kafkaProperties.buildConsumerProperties())
//        return basicReceiverOptions.subscription(Collections.singletonList(props.keycloak))
//    }
//
//    @Bean(value = ["kafkaKeycloakConsumerTemplate"])
//    fun kafkaConsumerTemplate(@Qualifier(value = "kafkaKeycloakOptions") kafkaReceiverOptions: ReceiverOptions<String, JsonNode>): DefaultKafkaConsumerFactory<String, JsonNode> {
//        return DefaultKafkaConsumerFactory(kafkaReceiverOptions.consumerProperties())
//    }
//}