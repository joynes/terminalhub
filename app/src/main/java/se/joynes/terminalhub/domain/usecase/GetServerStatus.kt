package se.joynes.terminalhub.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import se.joynes.terminalhub.data.model.ServerStatus
import javax.inject.Inject

class GetServerStatus @Inject constructor() {
    operator fun invoke(serverId: Long): Flow<ServerStatus> = emptyFlow()
}
