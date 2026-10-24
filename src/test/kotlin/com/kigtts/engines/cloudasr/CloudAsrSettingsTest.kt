package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.PluginStorage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudAsrSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun providersAreAddedOnlyWhenTheUserChoosesThemAndSurviveReload() {
        val first = CloudAsrSettings(PluginStorage(folder.newFolder("one")))
        assertTrue(first.all().isEmpty())
        val tencent = first.add(CloudAsrProvider.TENCENT)
        first.update(tencent.id, "apiKey", "local-key")
        first.update(tencent.id, "apiSecret", "local-secret")
        first.update(tencent.id, "endpoint", "https://example.invalid/asr")
        first.add(CloudAsrProvider.BAIDU)
        assertEquals(listOf("腾讯云", "百度云"), first.all().map { it.provider.label })
        val reloaded = CloudAsrSettings(PluginStorage(folder.root.resolve("one")))
        assertEquals(2, reloaded.all().size)
        assertEquals("local-key", reloaded.all().first().apiKey)
        reloaded.select(tencent.id)
        assertEquals("腾讯云", reloaded.current()?.provider?.label)
    }

    @Test fun invalidEndpointCannotBeSaved() {
        val settings = CloudAsrSettings(PluginStorage(folder.newFolder("two")))
        val config = settings.add(CloudAsrProvider.AZURE)
        try { settings.update(config.id, "endpoint", "http://insecure.example") ; fail("expected validation") }
        catch (_: IllegalArgumentException) { }
    }
}
