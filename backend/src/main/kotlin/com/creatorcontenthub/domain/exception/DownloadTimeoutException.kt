package com.creatorcontenthub.domain.exception

class DownloadTimeoutException(
    message: String = "Download timeout"
) : RuntimeException(message)