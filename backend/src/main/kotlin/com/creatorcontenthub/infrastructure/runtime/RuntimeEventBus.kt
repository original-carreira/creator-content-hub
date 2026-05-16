package com.creatorcontenthub.infrastructure.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

class RuntimeEventBus {

    private val events = MutableSharedFlow<RuntimeEvent>(
        replay = 50,
        extraBufferCapacity = 256
    )

    fun publish(event: RuntimeEvent) {
        events.tryEmit(event)
    }

    fun subscribe(jobId: String): Flow<RuntimeEvent> {
        return events
            .asSharedFlow()
            .filter { it.jobId == jobId }
    }
}