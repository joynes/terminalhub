package se.joynes.terminalhub.data.ssh

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Serializes terminal writes and provides a barrier that completes after preceding bytes flush. */
internal class OrderedByteWriter(
    scope: CoroutineScope,
    private val write: (ByteArray) -> Unit
) {
    private sealed interface Request {
        data class Bytes(val value: ByteArray) : Request
        data class Barrier(val completion: CompletableDeferred<Unit>) : Request
    }

    private val requests = Channel<Request>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (request in requests) {
                when (request) {
                    is Request.Bytes -> write(request.value)
                    is Request.Barrier -> request.completion.complete(Unit)
                }
            }
        }
    }

    fun enqueue(bytes: ByteArray): Boolean =
        requests.trySend(Request.Bytes(bytes.copyOf())).isSuccess

    suspend fun awaitDrained() {
        val completion = CompletableDeferred<Unit>()
        requests.send(Request.Barrier(completion))
        completion.await()
    }
}
