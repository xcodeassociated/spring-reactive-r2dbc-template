package com.softeno.template.app.permission.api

import com.softeno.template.app.common.PrincipalHandler
import com.softeno.template.app.permission.db.getPageRequest
import com.softeno.template.app.permission.mapper.PermissionDto
import com.softeno.template.app.permission.service.PermissionService
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal

@RestController
@Validated
class PermissionController(
    private val permissionService: PermissionService,
    private val tracer: Tracer
) : PrincipalHandler {
    private val log = LogFactory.getLog(javaClass)

    @GetMapping("/permissions")
    suspend fun getPermissions(@RequestParam(required = false, defaultValue = "0") page: Int,
                       @RequestParam(required = false, defaultValue = "10") size: Int,
                       @RequestParam(required = false, defaultValue = "id") sort: String,
                       @RequestParam(required = false, defaultValue = "ASC") direction: String, monoPrincipal: Mono<Principal>
    ): Flow<PermissionDto> {
        showPrincipal(log, monoPrincipal)

        // debug only
        val traceId = tracer.currentSpan()?.context()?.traceId()
        val mdc = MDC.get("traceId")
        val mdcSpan = MDC.get("spanId")
        log.debug("Show traceId=$traceId, mdcTraceId=$mdc and mdcSpanId=$mdcSpan")

        return permissionService.getAllPermissions(getPageRequest(page, size, sort, direction))
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
    suspend fun updatePermission(@PathVariable id: Long, @RequestBody permissionDto: PermissionDto, monoPrincipal: Mono<Principal>): ResponseEntity<PermissionDto> {
        val result = permissionService.updatePermission(id, permissionDto, principal = monoPrincipal.awaitSingle())
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/permissions/{id}")
    suspend fun deletePermission(@PathVariable id: Long) {
        permissionService.deletePermission(id)
    }
}