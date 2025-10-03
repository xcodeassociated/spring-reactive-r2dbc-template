package com.softeno.template.app.permission.db

import com.softeno.template.app.permission.BaseEntity
import com.softeno.template.app.permission.Permission
import com.softeno.template.app.permission.api.PermissionController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.domain.Pageable
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.*
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

@Repository
interface PermissionRepository : CoroutineCrudRepository<Permission, Long>, BatchPermissionRepository<Permission> {

    @Query("""
        SELECT p.* FROM permissions p 
        WHERE (:#{#search.search} IS NULL OR (
            p.name LIKE CONCAT('%', :#{#search.search}, '%') OR
            p.description LIKE CONCAT('%', :#{#search.search}, '%')
        )) 
        AND (:#{#search.createdFrom} IS NULL OR p.created_date >= :#{#search.createdFrom}) 
        AND (:#{#search.createdTo} IS NULL OR p.created_date <= :#{#search.createdTo}) 
        AND (:#{#search.createdBy} IS NULL OR p.created_by = :#{#search.createdBy})
        ORDER BY 
            CASE WHEN :#{#pageable.sort.toString()} = 'id: ASC' THEN p.id END ASC, 
            CASE WHEN :#{#pageable.sort.toString()} = 'id: DESC' THEN p.id END DESC
        LIMIT :#{#pageable.pageSize} 
        OFFSET :#{#pageable.offset} 
    """)
    suspend fun findBy(search: PermissionController.PermissionSearch, pageable: Pageable): Flow<Permission>

    @Query("""
        SELECT COUNT(*) FROM permissions p 
        WHERE (:#{#search.search} IS NULL OR (
            p.name LIKE CONCAT('%', :#{#search.search}, '%') OR
            p.description LIKE CONCAT('%', :#{#search.search}, '%')
        )) 
        AND (:#{#search.createdFrom} IS NULL OR p.created_date >= :#{#search.createdFrom}) 
        AND (:#{#search.createdTo} IS NULL OR p.created_date <= :#{#search.createdTo}) 
        AND (:#{#search.createdBy} IS NULL OR p.created_by = :#{#search.createdBy})
    """)
    suspend fun countBy(search: PermissionController.PermissionSearch): Long

    @Query("SELECT p.version FROM permissions as p WHERE p.id = :id")
    fun findVersionById(@Param("id") id: Long): Mono<Long>
}

/**
 *  Batch operations for @Table annotated entities
 */
interface BatchPermissionRepository<T : BaseEntity> {
    /**
     *  Inserts all @Column annotated props except id and uuid
     *  Returns map of inserted entities to their db ids
     */
    suspend fun insertAllReturningIds(entities: List<T>): Map<T, Long>

    /**
     *  Updates all @Column annotated props except id and uuid, entities must have the same uuid
     *  Returns map of updated entities to their db ids
     */
    suspend fun updateAllReturningIds(entities: List<T>): Map<T, Long>
}

@Component
class BatchPermissionRepositoryImpl<T : BaseEntity>(private val databaseClient: DatabaseClient) : BatchPermissionRepository<T> {

    private suspend fun <T : BaseEntity> specEntitiesToIds(
        spec: DatabaseClient.GenericExecuteSpec,
        entities: List<T>
    ): Map<T, Long> {
        val rows = spec.map { row, _ ->
            val uuid = row.get("uuid", UUID::class.java)!!
            val id = row.get("id", java.lang.Long::class.java)!!.toLong()
            uuid to id
        }
            .all()
            .collectList()
            .awaitSingle()

        val uuidToId = rows.toMap()
        return entities.associateWith { uuidToId[it.uuid]!! }
    }

    override suspend fun insertAllReturningIds(entities: List<T>): Map<T, Long> {
        if (entities.isEmpty()) return emptyMap()

        if (entities.any { it.id != null })
            throw OperationNotPermittedException("Cannot insert entities with non null id")

        val sample = entities.first()
        val tableName = sample::class.findAnnotation<Table>()?.value
            ?: throw OperationNotPermittedException("Missing @Table annotation on ${sample::class.simpleName}")

        val properties = sample::class.memberProperties
            .filter { it.javaField?.isAnnotationPresent(Column::class.java) == true }
            .map { prop -> prop to prop.javaField!!.getAnnotation(Column::class.java).value }

        val activeProps = properties.filter { (prop, _) ->
            entities.any { (prop as KProperty1<T, *>).get(it) != null }
        }

        val columnNames = activeProps.map { it.second }
        val valuePlaceholders = entities.mapIndexed { idx, _ ->
            "(" + activeProps.joinToString(", ") { (prop, _) -> ":${prop.name}$idx" } + ")"
        }.joinToString(", ")

        val sql =
            "INSERT INTO $tableName (${columnNames.joinToString(", ")}) VALUES $valuePlaceholders RETURNING id, uuid"

        var spec = databaseClient.sql(sql)

        entities.forEachIndexed { idx, entity ->
            activeProps.forEach { (prop, _) ->
                val value = (prop as KProperty1<T, *>).get(entity)
                spec = spec.bind("${prop.name}$idx", value)
            }
        }

        return specEntitiesToIds(spec, entities)
    }

    override suspend fun updateAllReturningIds(entities: List<T>): Map<T, Long> {
        if (entities.isEmpty()) return emptyMap()

        if (entities.any { it.id == null })
            throw OperationNotPermittedException("Cannot update entities with null id")

        val sample = entities.first()
        val tableName = sample::class.findAnnotation<Table>()?.value
            ?: throw OperationNotPermittedException("Missing @Table annotation on ${sample::class.simpleName}")

        val properties = sample::class.memberProperties
            .filter { it.javaField?.isAnnotationPresent(Column::class.java) == true }
            .map { prop -> prop to prop.javaField!!.getAnnotation(Column::class.java).value }
            .filter { (_, colName) -> colName != "id" && colName != "uuid" }

        val setClauses = properties.mapNotNull { (prop, colName) ->
            val cases = entities.mapIndexed { idx, entity ->
                val value = (prop as KProperty1<T, *>).get(entity)
                if (value != null) {
                    "WHEN :uuid$idx THEN :${prop.name}$idx"
                } else null
            }.filterNotNull()

            if (cases.isEmpty()) null
            else "$colName = CASE uuid ${cases.joinToString(" ")} ELSE $colName END"
        }

        if (setClauses.isEmpty()) return emptyMap()

        val sql = """
        UPDATE $tableName
        SET ${setClauses.joinToString(", ")}
        WHERE uuid IN (${entities.indices.joinToString(", ") { ":uuid$it" }})
        RETURNING id, uuid
        """.trimIndent()

        var spec = databaseClient.sql(sql)

        entities.forEachIndexed { idx, entity ->
            spec = spec.bind("uuid$idx", entity.uuid)
            properties.forEach { (prop, _) ->
                val value = (prop as KProperty1<T, *>).get(entity)
                if (value != null) {
                    spec = spec.bind("${prop.name}$idx", value)
                }
            }
        }

        return specEntitiesToIds(spec, entities)
    }
}

class OperationNotPermittedException(message: String) : RuntimeException(message)
