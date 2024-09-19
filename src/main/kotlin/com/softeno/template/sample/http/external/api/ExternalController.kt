package com.softeno.template.sample.http.external.api

import com.softeno.template.sample.http.external.client.ExternalServiceClient
import org.apache.commons.logging.LogFactory
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/external")
@Validated
class ExternalController(
    private val externalServiceClient: ExternalServiceClient
) {
    private val log = LogFactory.getLog(javaClass)

    @GetMapping("/{id}")
    fun getExternalResource(@PathVariable id: String): ResponseEntity<SampleResponseDto> {
        val data = externalServiceClient.fetchExternalResource(id)
        log.info("External: Received $id, sending: ${data.toString()}")
        return ResponseEntity.ok(SampleResponseDto(data = data ?: "null"))
    }
}

data class SampleResponseDto(val data: String)