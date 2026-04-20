package com.creatorcontenthub.application.dto

import kotlinx.serialization.Serializable

@Serializable // 🔥 NECESSÁRIO PARA call.receive()
data class ExportTextRequest(
    val text: String
)

@Serializable // 🔥 NECESSÁRIO PARA resposta HTTP
data class ExportTextResponse(
    val result: String
)