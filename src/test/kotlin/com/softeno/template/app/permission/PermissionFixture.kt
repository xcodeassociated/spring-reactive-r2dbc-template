package com.softeno.template.app.permission

import com.softeno.template.app.permission.mapper.PermissionDto
import java.util.*

interface PermissionFixture {
    companion object {
        fun aPermission(name: String = UUID.randomUUID().toString(), description: String = UUID.randomUUID().toString(), id: Long?, uuid: UUID?, version: Long = 0): Permission {
            val permission = Permission(name = name, description = description, id = id, uuid = uuid ?: UUID.randomUUID(), version = version)
            return permission
        }

        fun aPermission(name: String = UUID.randomUUID().toString(), description: String = UUID.randomUUID().toString()) = aPermission(name, description, null, null, 0)

        fun aPermissionDto(name: String = UUID.randomUUID().toString(), description: String = UUID.randomUUID().toString()) = PermissionDto(name = name, description = description,
            id = null, version = null, createdBy = null, createdDate = null, modifiedBy = null, modifiedDate = null)
    }
}