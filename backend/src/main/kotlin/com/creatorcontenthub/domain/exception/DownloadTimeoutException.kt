package com.creatorcontenthub.domain.exception

import java.util.concurrent.TimeoutException

class DownloadTimeoutException(
    message: String = "Download timeout"
) : TimeoutException(message)