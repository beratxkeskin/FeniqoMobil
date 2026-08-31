package com.feniqo.mobile.domain.model

fun interface EntityIdGenerator {
    fun nextId(): EntityId
}
