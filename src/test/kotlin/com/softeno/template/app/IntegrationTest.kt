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
import com.softeno.template.app.permission.db.OperationNotPermittedException
import com.softeno.template.app.permission.db.PermissionRepository
import com.softeno.template.sample.http.api.SampleResponseDto
import io.mockk.every
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

class BatchPermissionRepositoryImplTest : BaseIntegrationTest(), PermissionFixture {

    @Test
    fun `should not insert entities if any of them have id already`() = runTest {
        // given
        val aPermission = aPermission()
        val aPermissionWithId = aPermission().copy(id = 1)

        // expect
        val exception = assertThrows<OperationNotPermittedException> {
            permissionRepository.insertAllReturningIds(listOf(aPermission, aPermissionWithId))
        }
        assertEquals(exception.message, "Cannot insert entities with non null id")
    }

    @Test
    fun `should not update entities if any of them have id null`() = runTest {
        // given aPermission
        val aPermission = aPermission()
        val aPermissionWithId = aPermission().copy(id = 1)

        // expect
        val exception = assertThrows<OperationNotPermittedException> {
            permissionRepository.updateAllReturningIds(listOf(aPermission, aPermissionWithId))
        }
        assertEquals(exception.message, "Cannot update entities with null id")
    }

    @Test
    fun `insert all the entities and return their ids`() = runTest {
        // given
        val aPermission = aPermission()
        val anotherPermission = aPermission()

        // when
        val returned = permissionRepository.insertAllReturningIds(listOf(aPermission, anotherPermission))

        // then
        assertEquals(returned.size, 2)

        assertEquals(permissionRepository.findAll().asFlow().count(), 2)
        val saved = permissionRepository.findAll().asFlow().toList()

        assertEquals(saved[0].name, aPermission.name)
        assertEquals(saved[0].description, aPermission.description)
        assertEquals(saved[0].uuid, aPermission.uuid)

        assertEquals(saved[1].name, anotherPermission.name)
        assertEquals(saved[1].description, anotherPermission.description)
        assertEquals(saved[1].uuid, anotherPermission.uuid)
    }

    @Test
    fun `should insert all the entities and update them keeping the same db ids`() = runTest {
        // given
        val aPermission = aPermission(name = "1", description = "before change 1")
            .copy(createdBy = "user 1", createdDate = 123456L, modifiedBy = "user 2", modifiedDate = 123456L, version = 0)
        val anotherPermission = aPermission(name = "2", description = "before change 2")
            .copy(createdBy = "user 2", createdDate = 654321L, modifiedBy = "user 2", modifiedDate = 654321L, version = 0)

        // when
        val returned = permissionRepository.insertAllReturningIds(listOf(aPermission, anotherPermission))
        val aPermissionWithId = aPermission.copy(id = returned[aPermission])
        val anotherPermissionWithId = anotherPermission.copy(id = returned[anotherPermission])

        // then
        assertEquals(returned.size, 2)

        val saved = permissionRepository.findAll().asFlow().toList()
        assertEquals(saved.size, 2)

        assertEquals(saved[0].uuid, aPermission.uuid)
        assertEquals(saved[0].name, aPermission.name)
        assertEquals(saved[0].description, aPermission.description)
        assertEquals(saved[0].uuid, aPermission.uuid)
        assertEquals(saved[0].version, aPermission.version)
        assertEquals(saved[0].createdDate, aPermission.createdDate)
        assertEquals(saved[0].createdBy, aPermission.createdBy)
        assertEquals(saved[0].modifiedBy, aPermission.modifiedBy)
        assertEquals(saved[0].modifiedDate, aPermission.modifiedDate)

        assertEquals(saved[1].uuid, anotherPermission.uuid)
        assertEquals(saved[1].name, anotherPermission.name)
        assertEquals(saved[1].description, anotherPermission.description)
        assertEquals(saved[1].uuid, anotherPermission.uuid)
        assertEquals(saved[1].version, anotherPermission.version)
        assertEquals(saved[1].createdDate, anotherPermission.createdDate)
        assertEquals(saved[1].createdBy, anotherPermission.createdBy)
        assertEquals(saved[1].modifiedBy, anotherPermission.modifiedBy)
        assertEquals(saved[1].modifiedDate, anotherPermission.modifiedDate)


        // when change
        val changedPermission = aPermissionWithId.copy(modifiedDate = 1111111L , modifiedBy = "user 3", version = saved[0].version + 1)
        val changedAnotherPermission = anotherPermissionWithId.copy(modifiedDate = 2222222L, modifiedBy = "user 4", version = saved[1].version + 1)

        val changedReturned = permissionRepository.updateAllReturningIds(listOf(changedPermission, changedAnotherPermission))

        // then
        assertEquals(changedPermission.uuid, saved[0].uuid)
        assertEquals(changedAnotherPermission.uuid, saved[1].uuid)

        assertEquals(changedReturned.size, 2)

        val changedSaved = permissionRepository.findAll().asFlow().toList()
        assertEquals(changedSaved.size, 2)

        assertEquals(changedSaved[0].id, changedPermission.id)
        assertEquals(changedSaved[0].name, changedPermission.name)
        assertEquals(changedSaved[0].description, changedPermission.description)
        assertEquals(changedSaved[0].uuid, changedPermission.uuid)
        assertEquals(changedSaved[0].version, changedPermission.version)
        assertEquals(changedSaved[0].createdDate, changedPermission.createdDate)
        assertEquals(changedSaved[0].createdBy, changedPermission.createdBy)
        assertEquals(changedSaved[0].modifiedDate, changedPermission.modifiedDate)
        assertEquals(changedSaved[0].modifiedBy, changedPermission.modifiedBy)

        assertEquals(changedSaved[1].id, changedAnotherPermission.id)
        assertEquals(changedSaved[1].name, changedAnotherPermission.name)
        assertEquals(changedSaved[1].description, changedAnotherPermission.description)
        assertEquals(changedSaved[1].uuid, changedAnotherPermission.uuid)
        assertEquals(changedSaved[1].version, changedAnotherPermission.version)
        assertEquals(changedSaved[1].createdDate, changedAnotherPermission.createdDate)
        assertEquals(changedSaved[1].createdBy, changedAnotherPermission.createdBy)
        assertEquals(changedSaved[1].modifiedDate, changedAnotherPermission.modifiedDate)
        assertEquals(changedSaved[1].modifiedBy, changedAnotherPermission.modifiedBy)
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


