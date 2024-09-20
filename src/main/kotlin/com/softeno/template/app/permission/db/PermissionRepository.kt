package com.softeno.template.app.permission.db

import com.softeno.template.app.permission.Permission
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface PermissionRepository : R2dbcRepository<Permission, Long> {//, QuerydslPredicateExecutor<Permission> {
    fun findBy(pageable: Pageable): Flux<Permission>

    @Query("SELECT p.version FROM permissions as p WHERE p.id = :id")
    fun findVersionById(@Param("id") id: Long): Mono<Long>
}

fun getPageRequest(page: Int, size: Int, sort: String, direction: String) =
    Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
        .let { PageRequest.of(page, size, it) }