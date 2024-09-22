package com.softeno.template.app

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.ninjasquad.springmockk.MockkBean
import com.softeno.template.SoftenoMvcJpaApp
import com.softeno.template.app.permission.PermissionFixture
import com.softeno.template.app.permission.PermissionFixture.Companion.aPermission
import com.softeno.template.app.permission.PermissionFixture.Companion.aPermissionDto
import com.softeno.template.app.permission.db.PermissionRepository
import com.softeno.template.sample.http.api.SampleResponseDto
import io.mockk.every
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.domain.Pageable
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux

@SpringBootTest(
    classes = [SoftenoMvcJpaApp::class],
    properties = ["spring.profiles.active=integration"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EnableR2dbcRepositories
@AutoConfigureWebTestClient(timeout = "6000")
@EnableConfigurationProperties
@ConfigurationPropertiesScan("com.softeno")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
abstract class BaseIntegrationTest {

    @Autowired
    lateinit var permissionRepository: PermissionRepository

    @Autowired
    lateinit var webTestClient: WebTestClient

    companion object {
        @Container
        var postgreSQLContainer = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("application")
            .withUsername("admin")
            .withPassword("admin")


        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            postgreSQLContainer.start()

            registry.add("spring.liquibase.url") {
                "jdbc:postgresql://${postgreSQLContainer.host}:${postgreSQLContainer.firstMappedPort}/${postgreSQLContainer.databaseName}"
            }
            registry.add("spring.liquibase.user") {
                postgreSQLContainer.username
            }
            registry.add("spring.liquibase.password") { postgreSQLContainer.password }

            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgreSQLContainer.host}:${postgreSQLContainer.firstMappedPort}/${postgreSQLContainer.databaseName}"
            }
            registry.add("spring.r2dbc.username") { postgreSQLContainer.username }
            registry.add("spring.r2dbc.password") { postgreSQLContainer.password }
        }
    }

    @BeforeEach
    fun init() {
        // ...
    }

    @AfterEach
    fun cleanup() {
        runBlocking {
            permissionRepository.deleteAll().awaitFirstOrNull()
        }
    }

}

class ContextLoadsTest : BaseIntegrationTest() {

    @Test
    fun testConnection() {
        assertTrue(postgreSQLContainer.isRunning)
    }
}

class PermissionTest : BaseIntegrationTest(), PermissionFixture {

    @Test
    fun shouldReturnEmptyPermissionResponse() = runTest {
        webTestClient.get().uri("/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]")
    }

    @Test
    fun shouldRetrievePermission() = runTest {
        val aPermission = aPermission()
        permissionRepository.save(aPermission).awaitSingle()

        webTestClient.get().uri("/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("[0].name").isEqualTo(aPermission.name)
            .jsonPath("[0].description").isEqualTo(aPermission.description)
    }

    @Test
    fun shouldPersistPermission() = runTest {
        val aPermissionDto = aPermissionDto()

        webTestClient.post().uri("/permissions")
            .body(BodyInserters.fromValue(aPermissionDto))
            .exchange()
            .expectStatus().isOk

        assertEquals(permissionRepository.findAll().asFlow().count(), 1)
        assertEquals(permissionRepository.findAll().asFlow().first().name, aPermissionDto.name)
        assertEquals(permissionRepository.findAll().asFlow().first().description, aPermissionDto.description)
    }
}

class PermissionTestMockk : BaseIntegrationTest() {

    @MockkBean
    @Order(value = Ordered.HIGHEST_PRECEDENCE)
    lateinit var permissionRepositoryMock: PermissionRepository

    @BeforeEach
    fun initMockkRepository() {
        every { permissionRepositoryMock.deleteAll() }.answers { Mono.empty<Void>() }
    }

    @Test
    fun shouldPersistAndRetrievePermission() = runTest {
        val aPermission = aPermission()

        every { permissionRepositoryMock.findBy(any<Pageable>()) }.answers { listOf(aPermission).toFlux() }

        webTestClient.get().uri("/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("[0].name").isEqualTo(aPermission.name)
            .jsonPath("[0].description").isEqualTo(aPermission.description)
    }
}

class ExternalControllerTest : BaseIntegrationTest(), ExternalApiAbility {

    @Autowired
    private lateinit var webclient: WebClient

    private val wiremock: WireMockServer = WireMockServer(options().port(4500))

    @BeforeEach
    fun `setup wiremock`() {
        wiremock.start()
    }

    @AfterEach
    fun `stop wiremock`() {
        wiremock.stop()
        wiremock.resetAll()
    }

    @Test
    fun `mock external service with wiremock`() = runTest {
        // given
        mockGetId(wiremock)

        val expected = SampleResponseDto(data = "1")

        // expect
        val response = webclient.get().uri("http://localhost:4500/sample/100")
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .block()

        assertEquals(expected, response)
    }

    @Test
    fun `test external controller`() = runTest {
        // given
        mockGetId(wiremock)

        // expect
        webTestClient.get().uri("/external/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("data").isEqualTo("1")
    }
}

interface ExternalApiAbility {

    fun mockGetId(wiremock: WireMockServer) {
        wiremock.stubFor(
            get(urlMatching("/sample/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                    {
                        "data": "1"
                    }
                """.trimIndent()
                        )
                )
        )
    }
}


