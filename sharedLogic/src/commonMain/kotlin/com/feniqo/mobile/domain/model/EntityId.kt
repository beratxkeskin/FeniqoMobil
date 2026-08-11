package com.feniqo.mobile.domain.model

/** Harici teknolojiye bağlı olmayan, boş olamayan kayıt kimliği. */
data class EntityId(val value: String) {
    init {
        require(value.isNotBlank()) { "Kayıt kimliği boş olamaz." }
    }
}
