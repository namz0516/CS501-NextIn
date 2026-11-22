package com.example.nextin.domain.queue

class ServerClock(
    private val deviceMillis: () -> Long = System::currentTimeMillis
) {
    @Volatile
    var offsetMillis: Long = 0L
        private set

    fun now(): Long = deviceMillis() + offsetMillis

    fun sync(serverMillis: Long, requestStartedAt: Long, responseReceivedAt: Long) {
        require(responseReceivedAt >= requestStartedAt) {
            "The response cannot arrive before the request starts."
        }
        val midpoint = requestStartedAt + (responseReceivedAt - requestStartedAt) / 2
        offsetMillis = serverMillis - midpoint
    }
}
