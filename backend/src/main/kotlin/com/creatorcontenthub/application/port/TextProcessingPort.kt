package com.creatorcontenthub.application.port

interface TextProcessingPort {
    fun process(text: String): String
}