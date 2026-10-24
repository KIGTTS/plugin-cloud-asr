package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CloudAsrUiTest {
    @get:Rule val folder = TemporaryFolder()
    private val context = PluginContext("", "")
    @Test fun accountRowOpensAndEditableRowsAlwaysRequestUserInputBeforeSaving() = runTest {
        val plugin = plugin()
        val added = plugin.execute("add/azure", context) as PluginOutcome.OpenPluginPage
        assertEquals(added, plugin.execute(added.pageId, context))
        val page = plugin.page(added.pageId, context)!!
        val key = blocks(page.blocks).filterIsInstance<PageBlock.Input>().first { it.title == "Speech Key" }
        assertEquals("", key.value)
        val edit = plugin.execute(key.actionId, context) as PluginOutcome.TextInput
        assertTrue(edit.secret)
        assertEquals(PluginOutcome.RefreshUi, plugin.execute(edit.actionId, context.copy(inputs = mapOf("value" to "secret-key"))))
        assertEquals("secret-key", blocks(plugin.page(added.pageId, context)!!.blocks).filterIsInstance<PageBlock.Input>().first { it.title == "Speech Key" }.value)
        assertTrue(plugin.execute(edit.actionId, context) is PluginOutcome.Message)
        assertEquals("secret-key", blocks(plugin.page(added.pageId, context)!!.blocks).filterIsInstance<PageBlock.Input>().first { it.title == "Speech Key" }.value)
    }
    @Test fun oldAzureSpellingMigratesToRealEndpointAndCredentialsAreProviderSpecific() {
        assertEquals(CloudAsrProvider.AZURE, CloudAsrProvider.of("arzue"))
        for (provider in CloudAsrProvider.entries) {
            val config = CloudAsrConfig("a", provider, "a", provider.defaultEndpoint, apiKey = "key")
            assertEquals(provider == CloudAsrProvider.AZURE, config.configured)
            assertFalse(asrFields(config).any { it.key == "appId" } && provider == CloudAsrProvider.TENCENT)
        }
    }
    @Test fun badOrObsoleteActionIsAUserMessageInsteadOfAWorkerCrash() = runTest {
        val plugin = plugin()
        assertTrue(plugin.execute("provider/not-base64!", context) is PluginOutcome.Message)
        assertTrue(plugin.execute("save", context) is PluginOutcome.Message)
    }
    private suspend fun plugin() = CloudAsrPlugin().also { plugin ->
        val root = folder.newFolder()
        plugin.attach(object : PluginEnvironment {
            override val storage = PluginStorage(root)
            override val assets = root
            override suspend fun download(url: String, relativePath: String, sha256: String?): File = error("not used")
        })
    }
    private fun blocks(values: List<PageBlock>): List<PageBlock> = values.flatMap { if (it is PageBlock.Group) listOf(it) + blocks(it.children) else listOf(it) }
}
