package com.softeno.template.app.kafka

import com.fasterxml.jackson.databind.JsonNode
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller


@Controller
class KafkaKeycloakController {
    private val log = LogFactory.getLog(javaClass)

    @KafkaListener(id = "\${spring.kafka.consumer.group-id}-keycloak", topics = ["\${com.softeno.kafka.keycloak}"])
    fun listen1(record: ConsumerRecord<String, JsonNode>) {
        log.info("[kafka] rx keycloak: ${record.key()}: ${record.value()}")
    }
}