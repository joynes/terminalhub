package se.joynes.aiterminalhub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.ssh.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SshModule {

    @Provides
    @Singleton
    fun provideConnectionFactory(logger: AppLogger): SshConnectionFactory =
        SshConnectionFactory(logger)

    @Provides
    @Singleton
    fun provideSshManager(logger: AppLogger, factory: SshConnectionFactory): SshManager =
        SshManager(logger, factory)

    @Provides
    fun provideSshReconnectPolicy(): SshReconnectPolicy = SshReconnectPolicy()

    @Provides
    fun provideTmuxIntegration(logger: AppLogger): TmuxIntegration = TmuxIntegration(logger)

    @Provides
    fun provideServerStatusPoller(logger: AppLogger): ServerStatusPoller = ServerStatusPoller(logger)

    @Provides
    fun provideSftpUploader(logger: AppLogger): SftpUploader = SftpUploader(logger)
}
