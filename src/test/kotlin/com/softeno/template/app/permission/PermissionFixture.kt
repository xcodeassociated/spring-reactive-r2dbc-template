package com.softeno.template.app.permission

import com.softeno.template.app.permission.mapper.PermissionDto

interface PermissionFixture {
    companion object {
        fun aPermission(name: String = "some permission", description: String = "some description"): Permission {
            val permission = Permission(name = name, description = description)
            return permission
        }

        fun aPermissionDto(name: String = "some permission", description: String = "some description") = PermissionDto(name = name, description = description,
            id = null, version = null, createdBy = null, createdDate = null, modifiedBy = null, modifiedDate = null)
    }
}