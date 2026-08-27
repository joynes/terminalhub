package se.joynes.terminalhub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.ssh.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SshModule {

    @Provides
    @Singleton
    fun provideConnectionFactory(
        logger: AppLogger,
        settingsRepository: AppSettingsRepository,
        runtimeRepository: AppRuntimeRepository,
        hostKeyVerifier: TerminalHubHostKeyVerifier
    ): SshConnectionFactory = SshConnectionFactory(logger, settingsRepository, runtimeRepository, hostKeyVerifier)

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

    @Provides
    fun provideScpDownloader(logger: AppLogger, hostKeyVerifier: TerminalHubHostKeyVerifier): ScpDownloader =
        ScpDownloader(logger, hostKeyVerifier)
}
