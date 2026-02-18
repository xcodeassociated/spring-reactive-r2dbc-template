package com.softeno.template.app.kafka.config

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.app.kafka.dto.KafkaMessage
import org.springframework.boot.kafka.autoconfigure.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.SenderOptions


@Configuration
class KafkaConsumerConfig {
    @Bean
    fun kafkaReceiverOptions(kafkaProperties: KafkaProperties): ReceiverOptions<String, JsonNode> {
        return ReceiverOptions.create(kafkaProperties.buildConsumerProperties())
    }

    @Bean
    fun kafkaListenerContainerFactory(consumerFactory: ConsumerFactory<String, JsonNode>): ConcurrentKafkaListenerContainerFactory<String, JsonNode> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, JsonNode>()
        factory.setConsumerFactory(consumerFactory)
        return factory
    }

    @Bean
    fun kafkaConsumerTemplate(kafkaReceiverOptions: ReceiverOptions<String, JsonNode>): ConsumerFactory<String, JsonNode> {
        return DefaultKafkaConsumerFactory(kafkaReceiverOptions.consumerProperties())
    }
}

@Configuration
class KafkaProducerConfig {
    @Bean
    fun kafkaProducerTemplate(properties: KafkaProperties): ProducerFactory<String, KafkaMessage> {
        val props = properties.buildProducerProperties()
        val options = SenderOptions.create<String, KafkaMessage>(props)
        return DefaultKafkaProducerFactory(options.producerProperties())
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, KafkaMessage>): KafkaTemplate<String, KafkaMessage> {
        return KafkaTemplate(producerFactory)
    }
}
