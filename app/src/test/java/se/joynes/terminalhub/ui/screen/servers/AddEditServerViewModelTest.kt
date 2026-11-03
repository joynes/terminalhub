package se.joynes.terminalhub.ui.screen.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.security.*
import se.joynes.terminalhub.data.ssh.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddEditServerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo: ServerRepository = mock()
    private val credentials: SecurePrefsManager = mock()
    private val ssh: SshManager = mock()
    private val generator: SshKeyGenerator = mock()
    private val installer: SshPublicKeyInstaller = mock()
    private val hosts: KnownHostRepository = mock()
    private lateinit var vm: AddEditServerViewModel
    private val server = Server(id = 7, name = "Work", host = "host", username = "user")
    private val challenge = HostKeyChallenge(SshEndpoint("host", 22), HostKeyChallengeKind.UNKNOWN,
        "ssh-ed25519", byteArrayOf(1, 2), "SHA256:test")
    private val known = KnownHost(challenge.endpoint, challenge.presentedAlgorithm, challenge.presentedKeyBytes,
        challenge.presentedFingerprint, 1, null)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        whenever(hosts.lookup(any(), any())).thenReturn(KnownHostLookup.Missing)
        vm = AddEditServerViewModel(repo, credentials, ssh, generator, installer, hosts)
    }
    @After fun teardown() { Dispatchers.resetMain() }

    private fun configure() { vm.update { copy(host = " host ", username = " user ", password = "secret", port = "not-a-port") } }
    private suspend fun load(key: Boolean = false) {
        whenever(repo.getById(7)).thenReturn(server)
        whenever(credentials.getPassword(7)).thenReturn("saved-password")
        whenever(credentials.getPrivateKey(7)).thenReturn(if (key) "saved-key" else null)
        vm.loadServer(7)
    }
    private fun connection(connected: Boolean, error: String? = null, key: HostKeyChallenge? = null): SshConnection {
        val connection: SshConnection = mock()
        whenever(connection.sessionId).thenReturn("test-connection")
        whenever(connection.connected).thenReturn(MutableStateFlow(connected))
        whenever(connection.lastErrorMessage).thenReturn(MutableStateFlow(error))
        whenever(connection.hostKeyChallenge).thenReturn(MutableStateFlow(key))
        whenever(ssh.createSession(any(), anyOrNull(), anyOrNull())).thenReturn(connection)
        return connection
    }

    @Test fun missingServerDoesNotOverwriteAnUnsavedForm() = runTest(dispatcher) {
        configure(); val draft = vm.state.value
        vm.loadServer(null); vm.loadServer(99); runCurrent()
        assertEquals(draft, vm.state.value)
        verify(repo, never()).getById(0)
    }

    @Test fun loadingReadsCredentialPresenceWithoutExposingSavedPrivateKeyInForm() = runTest(dispatcher) {
        whenever(hosts.lookup("host", 22)).thenReturn(KnownHostLookup.Trusted(known))
        load(key = true); runCurrent()
        assertEquals("Work", vm.state.value.name)
        assertTrue(vm.state.value.hasSavedPrivateKey)
        assertEquals("", vm.state.value.privateKey)
        assertEquals("saved-password", vm.state.value.password)
        assertEquals(known, vm.state.value.knownHost)
    }

    @Test fun formChangesRefreshHostTrustAndResetObsoleteConnectionResults() = runTest(dispatcher) {
        whenever(hosts.lookup("host", 22)).thenReturn(KnownHostLookup.Corrupt)
        vm.update { copy(host = "host", sshTestStatus = SshTestStatus.Success, keyInstallStatus = KeyInstallStatus.Success) }
        assertTrue(vm.state.value.knownHostStoreCorrupt)
        assertEquals(SshTestStatus.Idle, vm.state.value.sshTestStatus)
        assertEquals(KeyInstallStatus.Idle, vm.state.value.keyInstallStatus)
        vm.update { copy(port = "70000") }
        assertFalse(vm.state.value.knownHostStoreCorrupt)
        vm.update { copy(host = "", port = "22") }
        assertNull(vm.state.value.knownHost)
    }

    @Test fun generatedKeysReplaceSavedKeyPresenceAndUseFallbackComment() = runTest(dispatcher) {
        whenever(generator.generate(any())).thenReturn(GeneratedSshKey("private", "ssh-rsa public"))
        vm.generateKey()
        verify(generator).generate("terminalhub@server-terminalhub")
        assertEquals("private", vm.state.value.privateKey)
        assertEquals("ssh-rsa public", vm.state.value.publicKey)
        assertFalse(vm.state.value.hasSavedPrivateKey)
    }

    @Test fun newServerPasswordIsSavedUnderTheGeneratedDatabaseId() = runTest(dispatcher) {
        configure(); whenever(repo.save(any())).thenReturn(17)
        vm.save(); runCurrent()
        val saved = argumentCaptor<Server>()
        verify(repo).save(saved.capture())
        assertEquals(22, saved.firstValue.port)
        assertEquals("password", saved.firstValue.authType)
        assertEquals(" host ", saved.firstValue.name)
        verify(credentials).savePassword(17, "secret")
        assertTrue(vm.state.value.saved)
    }

    @Test fun newServerWithKeyNeverPersistsTheOneTimePassword() = runTest(dispatcher) {
        configure(); vm.update { copy(privateKey = " private ") }
        whenever(repo.save(any())).thenReturn(18)
        vm.save(); runCurrent()
        verify(credentials).savePrivateKey(18, "private")
        verify(credentials, never()).savePassword(any(), any())
        verify(repo).save(argThat { authType == "key" })
    }

    @Test fun editingWritesKeyAndRemovesPasswordBeforePublishingDatabaseUpdate() = runTest(dispatcher) {
        load(); runCurrent(); vm.update { copy(privateKey = " replacement ") }
        vm.save(); runCurrent()
        inOrder(credentials, repo) {
            verify(credentials).savePrivateKey(7, "replacement")
            verify(credentials).deletePassword(7)
            verify(repo).update(argThat { id == 7L && authType == "key" })
        }
    }

    @Test fun savedKeySurvivesEditingOtherFieldsAndPasswordIsDiscarded() = runTest(dispatcher) {
        load(key = true); runCurrent(); vm.save(); runCurrent()
        verify(credentials, never()).savePrivateKey(any(), any())
        verify(credentials).deletePassword(7)
        verify(repo).update(argThat { authType == "key" })
    }

    @Test fun editedPasswordIsWrittenBeforeDatabaseUpdate() = runTest(dispatcher) {
        load(); runCurrent(); vm.update { copy(password = "changed") }; vm.save(); runCurrent()
        inOrder(credentials, repo) {
            verify(credentials).savePassword(7, "changed")
            verify(repo).update(argThat { id == 7L && authType == "password" })
        }
    }

    @Test fun sshTestUsesSavedKeyInsteadOfPasswordAndAlwaysDestroysTestConnection() = runTest(dispatcher) {
        load(key = true); runCurrent(); connection(true)
        vm.testSshConnection(); runCurrent()
        verify(ssh).createSession(argThat { id == 7L }, isNull(), eq("saved-key"))
        verify(ssh).destroySession("test-connection")
        assertEquals(SshTestStatus.Success, vm.state.value.sshTestStatus)
    }

    @Test fun failedSshShowsErrorAndUnknownKeyNeedsExplicitTrustBeforeRetry() = runTest(dispatcher) {
        configure(); val conn = connection(false, "Verify fingerprint", challenge)
        vm.testSshConnection(); runCurrent()
        assertEquals("Verify fingerprint", vm.state.value.sshTestMessage)
        assertEquals(PendingHostKeyAction.TEST_SSH, vm.state.value.pendingHostKeyAction)
        verify(hosts, never()).trust(any())
        whenever(hosts.trust(challenge)).thenReturn(known)
        whenever(conn.connected).thenReturn(MutableStateFlow(true))
        vm.trustPresentedHostKey(); runCurrent()
        assertEquals(SshTestStatus.Success, vm.state.value.sshTestStatus)
        verify(ssh, times(2)).destroySession("test-connection")
    }

    @Test fun changedHostKeyCannotBeTrustedAndCancelDoesNotRetry() = runTest(dispatcher) {
        configure(); connection(false, "Changed key", challenge.copy(kind = HostKeyChallengeKind.CHANGED))
        vm.testSshConnection(); runCurrent(); vm.trustPresentedHostKey()
        verify(hosts, never()).trust(any())
        vm.cancelHostKeyChallenge()
        assertNull(vm.state.value.hostKeyChallenge)
        assertNull(vm.state.value.pendingHostKeyAction)
        verify(ssh, times(1)).createSession(any(), anyOrNull(), anyOrNull())
    }

    @Test fun trustStorageFailureIsShownWithoutConnectingAgain() = runTest(dispatcher) {
        configure(); connection(false, "Unknown key", challenge)
        vm.testSshConnection(); runCurrent()
        whenever(hosts.trust(challenge)).thenThrow(IllegalStateException("Disk unavailable"))
        vm.trustPresentedHostKey()
        assertEquals("Disk unavailable", vm.state.value.sshTestMessage)
        verify(ssh, times(1)).createSession(any(), anyOrNull(), anyOrNull())
    }

    @Test fun sshTimeoutUsesBoundedVirtualTimeAndDestroysConnection() = runTest(dispatcher) {
        configure(); connection(false)
        vm.testSshConnection(); advanceUntilIdle()
        assertEquals(SshTestStatus.Failure, vm.state.value.sshTestStatus)
        assertTrue(vm.state.value.sshTestMessage.contains("timed out"))
        verify(ssh).destroySession("test-connection")
    }

    @Test fun emptyAddressOrUserCannotStartSshOrKeyInstallation() = runTest(dispatcher) {
        vm.testSshConnection(); vm.installGeneratedKey(); runCurrent()
        verifyNoInteractions(ssh, installer)
        configure(); vm.installGeneratedKey()
        assertEquals("Generate a key first.", vm.state.value.keyInstallMessage)
        verifyNoInteractions(installer)
    }

    @Test fun forgettingTrustClearsChallengeAndRequiresFreshVerification() = runTest(dispatcher) {
        vm.forgetTrustedHostKey(); verify(hosts, never()).forget(any(), any())
        configure(); vm.forgetTrustedHostKey()
        verify(hosts).forget(" host ", 22)
        assertNull(vm.state.value.hostKeyChallenge)
        assertTrue(vm.state.value.sshTestMessage.contains("forgotten"))
    }

    @Test fun publicKeyInstallationUsesOneTimePasswordAndNormalizedEndpoint() = runTest(dispatcher) {
        configure(); vm.update { copy(publicKey = "ssh-rsa public") }
        whenever(installer.install(any(), any(), any())).thenReturn(Result.success(Unit))
        vm.installGeneratedKey(); runCurrent()
        verify(installer).install(argThat { host == "host" && username == "user" && port == 22 }, eq("secret"), eq("ssh-rsa public"))
        assertEquals(KeyInstallStatus.Success, vm.state.value.keyInstallStatus)
        assertTrue(vm.state.value.keyInstallMessage.contains("Public key installed"))
        assertFalse(vm.state.value.saved)
        verify(credentials, never()).savePassword(any(), any())
    }

    @Test fun failedKeyInstallationCanBeRetriedOnlyAfterExplicitUnknownHostTrust() = runTest(dispatcher) {
        load(); runCurrent(); vm.update { copy(password = "", publicKey = "ssh-rsa public") }
        whenever(installer.install(any(), any(), any())).thenReturn(Result.failure(HostKeyVerificationException(challenge, "Check fingerprint")))
        vm.installGeneratedKey(); runCurrent()
        assertEquals(KeyInstallStatus.Failure, vm.state.value.keyInstallStatus)
        assertEquals("Check fingerprint", vm.state.value.keyInstallMessage)
        assertEquals(PendingHostKeyAction.INSTALL_PUBLIC_KEY, vm.state.value.pendingHostKeyAction)
        verify(hosts, never()).trust(any())
        whenever(hosts.trust(challenge)).thenReturn(known)
        whenever(installer.install(any(), any(), any())).thenReturn(Result.success(Unit))
        vm.trustPresentedHostKey(); runCurrent()
        assertEquals(KeyInstallStatus.Success, vm.state.value.keyInstallStatus)
        verify(installer, times(2)).install(any(), eq("saved-password"), eq("ssh-rsa public"))
    }

    @Test fun plainKeyInstallationFailureDoesNotOfferHostTrust() = runTest(dispatcher) {
        configure(); vm.update { copy(publicKey = "ssh-rsa public") }
        whenever(installer.install(any(), any(), any())).thenReturn(Result.failure(IllegalStateException("Wrong password")))
        vm.installGeneratedKey(); runCurrent()
        assertEquals(KeyInstallStatus.Failure, vm.state.value.keyInstallStatus)
        assertEquals("Wrong password", vm.state.value.keyInstallMessage)
        assertNull(vm.state.value.pendingHostKeyAction)
        vm.trustPresentedHostKey()
        verifyNoInteractions(ssh)
    }
}
