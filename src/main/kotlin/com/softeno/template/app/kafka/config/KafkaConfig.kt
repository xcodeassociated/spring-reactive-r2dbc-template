package com.softeno.template.app.kafka.config

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.app.kafka.KafkaMessage
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@Profile(value = ["!integration"])
@Configuration
@EnableKafka
class KafkaConfig {

    @Bean
    fun kafkaListenerContainerFactory(consumerFactory: ConsumerFactory<String, JsonNode>) =
        ConcurrentKafkaListenerContainerFactory<String, JsonNode>().also { it.consumerFactory = consumerFactory }

    @Bean
    fun consumerFactory() = DefaultKafkaConsumerFactory<String, JsonNode>(consumerProps)

    val consumerProps = mapOf(
        // todo: change it
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "kafka-broker:9094",
        ConsumerConfig.GROUP_ID_CONFIG to "sample-group-jvm-jpa",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
        JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
        JsonDeserializer.TRUSTED_PACKAGES to "*",
        JsonDeserializer.VALUE_DEFAULT_TYPE to JsonNode::class.java,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
    )

    @Bean
    fun producerFactory() = DefaultKafkaProducerFactory<String, KafkaMessage>(senderProps)

    val senderProps = mapOf(
        // todo: change it
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "kafka-broker:9094",
        ProducerConfig.LINGER_MS_CONFIG to 10,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java
    )

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, KafkaMessage>) = KafkaTemplate(producerFactory)
}