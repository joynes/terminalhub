package se.joynes.aiterminalhub.data.model

data class Server(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String = "password",
    val keyAlias: String? = null
)
