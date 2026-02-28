package com.softeno.template.app.permission.api

import com.softeno.template.app.common.PrincipalHandler
import com.softeno.template.app.permission.mapper.PermissionDto
import com.softeno.template.app.permission.service.PermissionService
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.security.Principal

@RestController
@Validated
class PermissionController(
    private val permissionService: PermissionService,
    private val tracer: Tracer
) : PrincipalHandler {
    private val log = LogFactory.getLog(javaClass)

    data class PermissionSearch(
        val search: String?,
        val createdBy: String?,
        val createdFrom: Long?,
        val createdTo: Long?
    )

    @GetMapping("/permissions")
    suspend fun getPermissions(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
        @RequestParam(required = false) search: String? = null,
        @RequestParam(required = false) createdBy: String? = null,
        @RequestParam(required = false) createdFrom: Long? = null,
        @RequestParam(required = false) createdTo: Long? = null,
        monoPrincipal: Mono<Principal>
    ): Flow<PermissionDto> {
        showPrincipal(log, monoPrincipal)

        // debug only
        val traceId = tracer.currentSpan()?.context()?.traceId()
        val mdc = MDC.get("traceId")
        val mdcSpan = MDC.get("spanId")
        log.debug("Show traceId=$traceId, mdcTraceId=$mdc and mdcSpanId=$mdcSpan")

        log.debug("Show request params: " +
                "page=$page, size=$size, sort=$sort, direction=$direction, " +
                "search=$search, createdBy=$createdBy, createdFrom=$createdFrom, createdTo=$createdTo"
        )

        return permissionService.getAllPermissions(
            getPageRequest(page, size, sort, direction),
            PermissionSearch(search, createdBy, createdFrom, createdTo)
        )
    }

    @GetMapping("/permissions/count")
    suspend fun getPermissionsCount(
        @RequestParam(required = false) search: String? = null,
        @RequestParam(required = false) createdBy: String? = null,
        @RequestParam(required = false) createdFrom: Long? = null,
        @RequestParam(required = false) createdTo: Long? = null,
    ): Long {
        return permissionService.countPermissions(PermissionSearch(search, createdBy, createdFrom, createdTo))
    }

    @GetMapping("/permissions/{id}")
    suspend fun getPermission(@PathVariable id: Long): ResponseEntity<PermissionDto> {
        val result = permissionService.getPermission(id)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/permissions")
    suspend fun createPermission(@RequestBody permissionDto: PermissionDto): ResponseEntity<PermissionDto> {
        val result = permissionService.createPermission(permissionDto)
        log.info("sending event: PERMISSION_CREATED: ${result.id}")
        return ResponseEntity.ok(result)
    }

    @PutMapping("/permissions/{id}")
    suspend fun updatePermission(
        @PathVariable id: Long,
        @RequestBody permissionDto: PermissionDto,
        monoPrincipal: Mono<Principal>
    ): ResponseEntity<PermissionDto> {
        val result = permissionService.updatePermission(id, permissionDto, principal = monoPrincipal.awaitSingle())
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/permissions/{id}")
    suspend fun deletePermission(@PathVariable id: Long) {
        permissionService.deletePermission(id)
    }
}

fun getPageRequest(page: Int, size: Int, sort: String, direction: String) =
    Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
        .let { PageRequest.of(page, size, it) }