package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class CloudAsrProtocolsTest {
    @get:Rule val folder = TemporaryFolder()
    private val time = Instant.ofEpochSecond(1551113065)
    private val audio = PcmAudio(byteArrayOf(1, 0, 2, 0))
    private fun config(provider: CloudAsrProvider) = CloudAsrConfig("account", provider, provider.label, provider.defaultEndpoint, "test-key", "test-secret", "test-app")

    @Test fun tc3MatchesIndependentFixtureAndHashesTheExactBody() {
        val body = """{"Limit":1,"Filters":[{"Values":["unnamed"],"Name":"instance-name"}]}""".toByteArray()
        val headers = SpeechSigning.tencent("https://cvm.tencentcloudapi.com", "cvm", "DescribeInstances", "2017-03-12", "test-key", "test-secret", body, time)
        assertEquals("TC3-HMAC-SHA256 Credential=test-key/2019-02-25/cvm/tc3_request, SignedHeaders=content-type;host, Signature=57c73f3a11a21a00507ce40b7a988b04adfcba944ea5c84229e54cef461ca538", headers["Authorization"])
    }
    @Test fun aliyunRpcSignatureMatchesIndependentFixtureAndRfc3986Encoding() {
        val parameters = mapOf("AccessKeyId" to "my_access_key_id", "Action" to "CreateToken", "Format" to "JSON", "RegionId" to "cn-shanghai",
            "SignatureMethod" to "HMAC-SHA1", "SignatureNonce" to "b924c8c3-6d03-4c5d-ad36-d984d3116788", "SignatureVersion" to "1.0", "Timestamp" to "2019-04-18T08:32:31Z", "Version" to "2019-02-28")
        assertEquals("rXeRDy2gduA2HYnKcSASKWrU7Rc=", query(SpeechSigning.aliyun(parameters, "test-secret"))["Signature"])
        assertEquals("%E4%B8%AD%20%2B%2A~", SpeechSigning.encode("中 +*~"))
    }
    @Test fun tencentUsesOfficialFieldsAndParsesResponse() = runTest {
        val transport = FakeTransport { request ->
            assertEquals("SentenceRecognition", request.headers["X-TC-Action"])
            assertEquals("2019-06-14", request.headers["X-TC-Version"])
            assertFalse(request.headers.containsKey("X-Api-Key"))
            val body = JSONObject(String(request.body!!)); assertEquals("16k_en", body.getString("EngSerViceType"))
            assertEquals(4, body.getInt("DataLen")); assertEquals("pcm", body.getString("VoiceFormat"))
            assertArrayEquals(audio.bytes, Base64.getDecoder().decode(body.getString("Data")))
            response("""{"Response":{"Result":"hello"}}""")
        }
        assertEquals("hello", CloudAsrProtocols(transport) { time }.recognize(config(CloudAsrProvider.TENCENT), audio, "en-US"))
    }
    @Test fun aliyunUsesTokenAndRawPcmAndReusesUnexpiredToken() = runTest {
        var auth = 0
        val transport = FakeTransport { request ->
            if (request.url.contains("nls-meta")) { auth++; assertTrue(request.url.contains("Signature=")); response("""{"Token":{"Id":"nls-token","ExpireTime":9999999999}}""") }
            else { assertEquals("nls-token", request.headers["X-NLS-Token"]); assertArrayEquals(audio.bytes, request.body); assertTrue(request.url.contains("sample_rate=16000")); response("""{"status":20000000,"result":"你好"}""") }
        }
        val protocols = CloudAsrProtocols(transport) { time }; repeat(2) { assertEquals("你好", protocols.recognize(config(CloudAsrProvider.ALIYUN), audio, "zh-CN")) }; assertEquals(1, auth)
    }
    @Test fun azureWrapsPcmInWavAndDoesNotTreatNoMatchAsFailure() = runTest {
        val transport = FakeTransport { request ->
            assertEquals("test-key", request.headers["Ocp-Apim-Subscription-Key"])
            assertEquals("RIFF", String(request.body!!, 0, 4)); assertEquals(audio.bytes.size + 44, request.body.size)
            assertTrue(request.url.contains("language=zh-CN")); response("""{"RecognitionStatus":"NoMatch"}""")
        }
        assertEquals("", CloudAsrProtocols(transport).recognize(config(CloudAsrProvider.AZURE), audio, "zh-CN"))
    }
    @Test fun baiduObtainsOAuthTokenAndSendsRawByteLength() = runTest {
        val transport = FakeTransport { request ->
            if (request.url.contains("oauth")) { assertTrue(String(request.body!!).contains("grant_type=client_credentials")); response("""{"access_token":"bd-token","expires_in":3600}""") }
            else { val body = JSONObject(String(request.body!!)); assertEquals(4, body.getInt("len")); assertEquals("bd-token", body.getString("token")); assertEquals(1737, body.getInt("dev_pid")); response("""{"err_no":0,"result":["hello"]}""") }
        }
        assertEquals("hello", CloudAsrProtocols(transport).recognize(config(CloudAsrProvider.BAIDU), audio, "en-US"))
    }
    @Test fun xfyunSignsActualPathUsesPacedFramesAndAppliesReplacements() = runTest {
        val c = config(CloudAsrProvider.IFlytek)
        val url = SpeechSigning.xfyun(c.endpoint, c.apiKey, c.apiSecret, Instant.parse("2019-07-10T07:35:43Z"))
        val authorization = String(Base64.getDecoder().decode(query(URI(url).rawQuery).getValue("authorization")))
        assertTrue(authorization.contains("psYk88Wgobv96HENv675YEdlFKHtJiU/EW11HCz7FIw="))
        val transport = object : SpeechTransport {
            override suspend fun http(request: SpeechHttpRequest) = error("not HTTP")
            override suspend fun socket(url: String, frames: List<SpeechFrame>, onMessage: (String) -> Boolean) {
                assertTrue(url.startsWith("wss://iat-api.xfyun.cn/v2/iat?")); assertEquals(40L, frames.first().delayAfterMillis)
                assertEquals(0, JSONObject(frames.first().text).getJSONObject("data").getInt("status"))
                assertEquals(2, JSONObject(frames.last().text).getJSONObject("data").getInt("status"))
                assertFalse(onMessage("""{"code":0,"data":{"status":1,"result":{"sn":1,"ws":[{"cw":[{"w":"旧"}]}]}}}"""))
                assertTrue(onMessage("""{"code":0,"data":{"status":2,"result":{"sn":2,"pgs":"rpl","rg":[1,1],"ws":[{"cw":[{"w":"新"}]}]}}}"""))
            }
        }
        assertEquals("新", CloudAsrProtocols(transport).recognize(c, audio, "zh-CN"))
    }
    @Test fun providerErrorsRemainErrorsAndSecretsAreRemoved() = runTest {
        val transport = FakeTransport { response("""{"Response":{"Error":{"Code":"AuthFailure","Message":"test-key bad test-secret"}}}""") }
        try { CloudAsrProtocols(transport).recognize(config(CloudAsrProvider.TENCENT), audio, "zh-CN"); fail() }
        catch (error: EngineException) { assertTrue(error.message!!.contains("AuthFailure")); assertFalse(error.message!!.contains("test-key")); assertFalse(error.message!!.contains("test-secret")) }
    }
    @Test fun invalidPcmAndOverlongAudioFailBeforeNetwork() = runTest {
        val transport = FakeTransport { error("no network") }
        val protocols = CloudAsrProtocols(transport)
        for (sample in listOf(PcmAudio(byteArrayOf()), PcmAudio(ByteArray(2), 8000), PcmAudio(ByteArray(60 * 32000 + 2)), PcmAudio("RIFFxx".toByteArray()))) {
            try { protocols.recognize(config(CloudAsrProvider.AZURE), sample, "zh-CN"); fail() } catch (error: EngineException) { assertEquals(EngineError.UNSUPPORTED_FORMAT, error.code) }
        }
    }
    @Test fun streamingNeverCollectsAnUnboundedWholeRecording() = runTest {
        val settings = CloudAsrSettings(PluginStorage(folder.newFolder())); val service = settings.add(CloudAsrProvider.AZURE)
        settings.update(service.id, "apiKey", "key")
        val sizes = mutableListOf<Int>()
        val engine = CloudAsrEngine(settings, CloudAsrProtocols(FakeTransport { sizes += it.body!!.size; response("""{"RecognitionStatus":"Success","DisplayText":"文"}""") }))
        val result = engine.recognizeStream(flowOf(PcmAudio(ByteArray(31 * 32000))), RecognitionConfig()).toList()
        assertEquals(listOf(15 * 32000 + 44, 15 * 32000 + 44, 32000 + 44), sizes)
        assertEquals(3, result.filterIsInstance<RecognitionEvent.Final>().size)
    }
    private class FakeTransport(val handler: (SpeechHttpRequest) -> SpeechHttpResponse) : SpeechTransport {
        override suspend fun http(request: SpeechHttpRequest) = handler(request)
        override suspend fun socket(url: String, frames: List<SpeechFrame>, onMessage: (String) -> Boolean) = error("unexpected websocket")
    }
    private fun response(text: String) = SpeechHttpResponse(200, text.toByteArray(), "application/json")
    private fun query(text: String) = text.split('&').associate { val pair = it.split('=', limit = 2); URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8") }
}
