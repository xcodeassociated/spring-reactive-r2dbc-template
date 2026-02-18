package com.softeno.template.app.permission

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.*
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.util.*

open class BaseEntity(
    @Transient
    open val uuid: UUID,

    @Transient
    open var id: Long? = null,
)

@Table(value = "permissions")
data class Permission(
    @Column(value = "uuid")
    override val uuid: UUID,

    @Id
    @Column(value = "id")
    override var id: Long? = null,

    @CreatedDate
    @Column(value = "created_date")
    val createdDate: Long? = null,

    @LastModifiedDate
    @Column(value = "modified_date")
    val modifiedDate: Long? = null,

    @CreatedBy
    @Column(value = "created_by")
    val createdBy: String? = null,

    @LastModifiedBy
    @Column(value = "modified_by")
    val modifiedBy: String? = null,

    @Version
    @Column(value = "version")
    val version: Long = 0,

    @NotBlank
    @Column(value = "name")
    val name: String,

    @NotNull
    @Column(value = "description")
    val description: String
) : BaseEntity(uuid) {
    constructor(name: String, description: String) :
            this(
                uuid = UUID.randomUUID(),
                name = name,
                description = description
            )

    override fun equals(other: Any?) = other is Permission && (uuid == other.uuid)

    override fun hashCode() = uuid.hashCode()

    override fun toString() = "${javaClass.simpleName}(id = $id, uuid = $uuid, version = $version)"
}