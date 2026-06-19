package se.joynes.terminalhub.data.model

import se.joynes.terminalhub.data.db.entity.ServerEntity

data class Server(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String = "password",
    val keyAlias: String? = null,
    val projectsFolder: String = "~/terminalhub",
    val setupScript: String = ServerEntity.DEFAULT_SETUP_SCRIPT
)
