package com.softeno.template.app.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.app.kafka.config.KafkaApplicationProperties
import com.softeno.template.app.kafka.dto.KafkaMessage
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller

@Controller
class KafkaSampleController {
    private val log = LogFactory.getLog(javaClass)

    @KafkaListener(id = "sample_group_1", topics = ["\${com.softeno.kafka.rx}"])
    fun listen1(record: ConsumerRecord<String, JsonNode>) {
        log.info("[kafka] rx keycloak: ${record.key()}: ${record.value()}")
    }
}

@Component
class KafkaSampleProducer(
    private val producer: KafkaTemplate<String, KafkaMessage>,
    private val props: KafkaApplicationProperties
) {
    private val log = LogFactory.getLog(javaClass)

    fun send(message: KafkaMessage) {
        log.info("[kafka] tx: topic: ${props.tx}, message: $message")
        producer.send(props.tx, message)
    }
}