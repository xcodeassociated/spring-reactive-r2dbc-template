package com.softeno.template.app.kafka

import com.softeno.template.app.BaseIntegrationTest
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Regression guard for ADR-0008 fan-out groups: every instance must consume
 * EVERY event on the broadcast topics (rx bus + keycloak), so each listener
 * joins Kafka under a per-instance group id. A shared group id would make
 * Kafka partition events across replicas — one pod would consume each event
 * and the other pods' local subscribers (SSE/WS) would stay silent.
 */
@TestPropertySource(properties = ["com.softeno.kafka.instance-id=fanout-it"])
class KafkaFanoutTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var registry: KafkaListenerEndpointRegistry

    @Test
    fun `every kafka listener joins a per-instance fan-out group`() {
        // these listeners set only `id` on @KafkaListener, and Spring Kafka uses the
        // resolved id as BOTH the container id and the consumer group — so asserting
        // the resolved container ids proves every replica joins its own group.
        val ids = registry.listenerContainerIds
        check(ids.isNotEmpty()) { "no kafka listeners registered" }
        ids.forEach { g ->
            assertTrue(g.endsWith("-fanout-it")) { "listener group '$g' is not per-instance (ADR-0008 violated)" }
        }
        assertTrue(ids.any { it.contains("-keycloak-fanout-it") }) { "keycloak listener group lacks the instance suffix: $ids" }
    }

    @Test
    fun `distinct consumer groups each receive every event (broadcast semantics)`() {
        val topic = "sample_topic_2"
        val marker = "fanout-" + UUID.randomUUID()
        val gotA = CountDownLatch(1)
        val gotB = CountDownLatch(1)
        val running = AtomicBoolean(true)
        val a = consumer("fanout-instance-a", topic, marker, gotA, running)
        val b = consumer("fanout-instance-b", topic, marker, gotB, running)
        try {
            KafkaProducer<String, String>(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                )
            ).use { p -> p.send(ProducerRecord(topic, "fanout", "{\"content\":\"$marker\"}")).get(15, TimeUnit.SECONDS) }

            assertTrue(gotA.await(30, TimeUnit.SECONDS)) { "instance-A group never saw the event" }
            assertTrue(gotB.await(30, TimeUnit.SECONDS)) { "instance-B group never saw the event — groups behave like a shared queue!" }
        } finally {
            running.set(false)
            a.close(); b.close()
        }
    }

    private fun consumer(group: String, topic: String, marker: String, latch: CountDownLatch, running: AtomicBoolean): KafkaConsumer<String, String> {
        val c = KafkaConsumer<String, String>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to group,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        )
        c.subscribe(listOf(topic))
        thread(isDaemon = true) {
            while (running.get() && latch.count > 0L) {
                for (r in c.poll(Duration.ofMillis(500))) {
                    if (r.value()?.contains(marker) == true) { latch.countDown(); break }
                }
            }
        }
        return c
    }
}
