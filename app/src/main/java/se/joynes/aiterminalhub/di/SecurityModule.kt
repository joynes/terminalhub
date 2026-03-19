package se.joynes.aiterminalhub.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule
// BiometricAuthManager, SecurePrefsManager, KeyStoreHelper are @Singleton with @Inject constructors
