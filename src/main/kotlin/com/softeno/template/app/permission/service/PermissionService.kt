package com.softeno.template.app.permission.service

import com.softeno.template.app.kafka.ReactiveKafkaSampleProducer
import com.softeno.template.app.kafka.dto.KafkaMessage
import com.softeno.template.app.permission.Permission
import com.softeno.template.app.permission.db.PermissionRepository
import com.softeno.template.app.permission.mapper.PermissionDto
import com.softeno.template.app.permission.mapper.toDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrElse
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.data.domain.Pageable
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria.where
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val kafkaPublisher: ReactiveKafkaSampleProducer,
    private val template: R2dbcEntityTemplate
) {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getAllPermissions(pageable: Pageable): Flow<PermissionDto> =
        withContext(MDCContext()) {
            return@withContext permissionRepository.findBy(pageable).map { it.toDto() }.asFlow()
        }

    suspend fun getPermission(id: Long): PermissionDto =
        withContext(MDCContext()) {
            return@withContext permissionRepository.findById(id).awaitFirstOrElse { throw Exception("Not Found: $id") }.toDto()
        }

    @Transactional
    suspend fun createPermission(permissionDto: PermissionDto): PermissionDto =
        withContext(MDCContext()) {
            val created = permissionRepository.save(Permission(name = permissionDto.name, description = permissionDto.description)).awaitSingle().toDto()
            kafkaPublisher.send(KafkaMessage(content = "CREATED_PREMISSION: ${created.id}", traceId = MDC.get("traceId"), spanId = MDC.get("spanId")))
            return@withContext created
        }

    @Transactional
    suspend fun updatePermission(id: Long, permissionDto: PermissionDto): PermissionDto =
        withContext(MDCContext()) {
            val currentVersion = permissionRepository.findVersionById(id).awaitSingle()
            if (currentVersion != permissionDto.version) {
                throw RuntimeException("Version mismatch")
            }

            template.update(Permission::class.java).matching(Query.query(where("id").`is`(id)))
                .apply(Update.update("name", permissionDto.name)
                    .set("description", permissionDto.description)
                    .set("version", permissionDto.version + 1)).awaitSingle()
            return@withContext template.selectOne(Query.query(where("id").`is`(id)), Permission::class.java).awaitSingle().toDto()
        }

    suspend fun deletePermission(id: Long) =
        withContext(MDCContext()) {
            permissionRepository.deleteById(id).awaitSingleOrNull()
        }
}