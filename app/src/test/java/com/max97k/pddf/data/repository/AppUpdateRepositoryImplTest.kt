package com.max97k.pddf.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.max97k.pddf.domain.model.AppUpdateStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeAppUpdateManager: FakeAppUpdateManager
    private lateinit var repository: AppUpdateRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeAppUpdateManager = FakeAppUpdateManager(context)
        repository = AppUpdateRepositoryImpl(fakeAppUpdateManager, testDispatcher)
    }

    @Test
    fun testInitialState_isIdle() {
        assertEquals(AppUpdateStatus.Idle, repository.updateStatus.value)
    }

    @Test
    fun testCheckForUpdate_whenNoUpdateAvailable_setsUpToDate() = runTest(testDispatcher) {
        fakeAppUpdateManager.setUpdateNotAvailable()

        repository.checkForUpdate()
        advanceUntilIdle()

        assertEquals(AppUpdateStatus.UpToDate, repository.updateStatus.value)
    }

    @Test
    fun testCheckForUpdate_whenUpdateAvailable_setsAvailable() = runTest(testDispatcher) {
        fakeAppUpdateManager.setUpdateAvailable(2)

        repository.checkForUpdate()
        advanceUntilIdle()

        val status = repository.updateStatus.value
        assertTrue(status is AppUpdateStatus.Available)
        assertEquals(2, (status as AppUpdateStatus.Available).availableVersionCode)
    }

    @Test
    fun testResetStatus_setsIdle() = runTest(testDispatcher) {
        fakeAppUpdateManager.setUpdateNotAvailable()
        repository.checkForUpdate()
        advanceUntilIdle()
        assertEquals(AppUpdateStatus.UpToDate, repository.updateStatus.value)

        repository.resetStatus()
        assertEquals(AppUpdateStatus.Idle, repository.updateStatus.value)
    }
}
