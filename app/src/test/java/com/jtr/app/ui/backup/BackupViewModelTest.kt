package com.jtr.app.ui.backup

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.jtr.app.R
import com.jtr.app.data.backup.BackupManager
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Session de sauvegarde à portée d'activité (v7.1.143) : le résultat d'un sélecteur n'est
 * jamais perdu, aucune issue n'est muette, une restauration n'est jamais appliquée sans
 * confirmation et une archive externe ne se ré-arme pas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val app = mockk<Application>(relaxed = true)
    private val uri = mockk<Uri>()

    @Before
    fun setUp() {
        every { app.applicationContext } returns app
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns mockk(relaxed = true)
        mockkConstructor(BackupManager::class)
        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.deleteDocument(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) = BackupViewModel(app, handle)

    @Test
    fun pickerRequest_isConsumedOnceLaunched() {
        val vm = vm()

        vm.requestExport()
        assertThat(vm.pickerRequest.value).isEqualTo(BackupPickerRequest.EXPORT)

        vm.onPickerLaunched()
        assertThat(vm.pickerRequest.value).isNull()
    }

    @Test
    fun exportPickerCancelled_saysSo() {
        val vm = vm()

        vm.onExportDestination(null)

        assertThat(vm.state.value).isEqualTo(BackupUiState.Cancelled(isExport = true))
        assertThat(vm.dialogVisible.value).isTrue()
        coVerify(exactly = 0) { anyConstructed<BackupManager>().exportTo(any()) }
    }

    @Test
    fun importPickerCancelled_saysSo() {
        val vm = vm()

        vm.onImportSource(null)

        assertThat(vm.state.value).isEqualTo(BackupUiState.Cancelled(isExport = false))
        assertThat(vm.pendingImportUri.value).isNull()
    }

    @Test
    fun exportSuccess_keepsTheDocument() {
        coEvery { anyConstructed<BackupManager>().exportTo(uri) } returns Result.success(221)
        val vm = vm()

        vm.onExportDestination(uri)

        assertThat(vm.state.value).isEqualTo(BackupUiState.Success(isExport = true, count = 221))
        verify(exactly = 0) { DocumentsContract.deleteDocument(any(), any()) }
    }

    @Test
    fun exportFailure_deletesTheCreatedDocument() {
        coEvery { anyConstructed<BackupManager>().exportTo(uri) } returns
            Result.failure(IllegalStateException("write failed"))
        val vm = vm()

        vm.onExportDestination(uri)

        assertThat(vm.state.value)
            .isEqualTo(BackupUiState.Error(isExport = true, messageRes = R.string.backup_failed))
        verify(exactly = 1) { DocumentsContract.deleteDocument(any(), uri) }
    }

    @Test
    fun resultOfAnExportFinishedWhileLocked_isShownUntilAcknowledged() {
        val done = CompletableDeferred<Result<Int>>()
        coEvery { anyConstructed<BackupManager>().exportTo(uri) } coAnswers { done.await() }
        val vm = vm()
        vm.openDialog()

        vm.onExportDestination(uri)
        // Le dialogue est refermé (ou retiré par l'écran de verrou) pendant l'écriture.
        vm.closeDialog()
        assertThat(vm.state.value).isEqualTo(BackupUiState.Working(isExport = true))
        assertThat(vm.dialogVisible.value).isTrue()

        done.complete(Result.success(221))
        assertThat(vm.state.value).isEqualTo(BackupUiState.Success(isExport = true, count = 221))
        assertThat(vm.dialogVisible.value).isTrue()

        vm.closeDialog()
        assertThat(vm.state.value).isEqualTo(BackupUiState.Idle)
        assertThat(vm.dialogVisible.value).isFalse()
    }

    @Test
    fun pickedArchive_waitsForConfirmation() {
        val vm = vm()

        vm.onImportSource(uri)

        assertThat(vm.pendingImportUri.value).isEqualTo(uri)
        assertThat(vm.dialogVisible.value).isTrue()
        coVerify(exactly = 0) { anyConstructed<BackupManager>().importFrom(any()) }
    }

    @Test
    fun confirmImport_restoresOnce_andClearsThePendingArchive() {
        coEvery { anyConstructed<BackupManager>().importFrom(uri) } returns Result.success(221)
        val vm = vm()
        vm.onImportSource(uri)

        vm.confirmImport()
        vm.confirmImport()

        assertThat(vm.pendingImportUri.value).isNull()
        assertThat(vm.state.value).isEqualTo(BackupUiState.Success(isExport = false, count = 221))
        coVerify(exactly = 1) { anyConstructed<BackupManager>().importFrom(uri) }
    }

    @Test
    fun cancelImport_clearsThePendingArchive_withoutRestoring() {
        val vm = vm()
        vm.onImportSource(uri)

        vm.cancelImport()

        assertThat(vm.pendingImportUri.value).isNull()
        coVerify(exactly = 0) { anyConstructed<BackupManager>().importFrom(any()) }
    }

    @Test
    fun externalArchive_isOfferedOnce_andNotRearmedOnceAnswered() {
        val vm = vm()

        vm.offerExternalImport(uri)
        assertThat(vm.pendingImportUri.value).isEqualTo(uri)
        assertThat(vm.dialogVisible.value).isTrue()

        vm.cancelImport()
        vm.closeDialog()
        assertThat(vm.pendingImportUri.value).isNull()
        assertThat(vm.dialogVisible.value).isFalse()
    }

    @Test
    fun pendingArchive_survivesProcessDeath_stillUnconfirmed() {
        val handle = SavedStateHandle()
        vm(handle).onImportSource(uri)

        // Nouveau processus : même état sauvegardé, nouvelle instance.
        val restored = vm(handle)

        assertThat(restored.pendingImportUri.value).isEqualTo(uri)
        coVerify(exactly = 0) { anyConstructed<BackupManager>().importFrom(any()) }
    }
}
