package com.kitewatch.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ThemePreferenceRepositoryTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: ThemePreferenceRepository

    @Before
    fun setUp() {
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
            )
        repository = ThemePreferenceRepository(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `isDarkTheme defaults to false`() =
        testScope.runTest {
            assertFalse(repository.isDarkTheme.first())
        }

    @Test
    fun `setDarkTheme true emits true`() =
        testScope.runTest {
            repository.setDarkTheme(true)
            assertTrue(repository.isDarkTheme.first())
        }

    @Test
    fun `setDarkTheme false after true emits false`() =
        testScope.runTest {
            repository.setDarkTheme(true)
            repository.setDarkTheme(false)
            assertFalse(repository.isDarkTheme.first())
        }
}
