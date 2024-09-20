package com.softeno.template.app.permission

import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table
import java.util.UUID
import kotlin.jvm.javaClass


@Table(value = "permissions")
data class Permission(
	val uuid: UUID,

	@Id
	var id: Long? = null,

	@CreatedDate
	val createdDate: Long? = null,

	@LastModifiedDate
	val modifiedDate: Long? = null,

	@CreatedBy
	val createdBy: String? = null,

	@LastModifiedBy
	val modifiedBy: String? = null,

	@Version
	val version: Long = 0,

	val name: String,

	val description: String)
{
	constructor(name: String, description: String) :
			this(
				uuid = UUID.randomUUID(),
				name = name,
				description = description)

	override fun equals(other: Any?): Boolean {
		return other is Permission && (uuid == other.uuid)
	}

	override fun hashCode(): Int {
		return uuid.hashCode()
	}

	override fun toString(): String {
		return "${javaClass.simpleName}(id = $id, uuid = $uuid, version = $version)"
	}

}