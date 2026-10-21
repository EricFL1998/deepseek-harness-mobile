package com.deepseek.dsh.runtime.host

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 插件最新版本的查询与缓存。
 *
 * 缓存不是性能优化，而是可用性约束：未授权的 GitHub API 每小时只有 60 次额度，
 * 设置页每刷新一次就问一次，没有缓存时很快会把自己限流，之后连「有没有更新」
 * 都判断不了。
 */
class BridgeReleaseCheckerTest {

    @Test
    fun `a successful lookup is cached for later refreshes`() = runBlocking {
        var calls = 0
        val checker = BridgeReleaseChecker(
            OkHttpClient.Builder().addInterceptor { chain ->
                calls += 1
                jsonResponse(chain.request(), releases(release("v1.2.3")))
            }.build(),
        )
        val repository = "https://github.com/cachetest/success"
        assertEquals("1.2.3", checker.latestVersion(repository))
        assertEquals("1.2.3", checker.latestVersion(repository))
        assertEquals("one HTTP request for two lookups", 1, calls)
    }

    @Test
    fun `a failed lookup is cached briefly instead of hammering the API`() = runBlocking {
        var calls = 0
        val checker = BridgeReleaseChecker(
            OkHttpClient.Builder().addInterceptor { chain ->
                calls += 1
                jsonResponse(chain.request(), "[]", code = 403, message = "rate limited")
            }.build(),
        )
        val repository = "https://github.com/cachetest/failure"
        assertNull(checker.latestVersion(repository))
        assertNull(checker.latestVersion(repository))
        // 一次查询 = API + 跳转兜底两次请求；第二次查询走缓存，不再发请求。
        assertEquals("API and redirect are asked once per lookup", 2, calls)
    }

    @Test
    fun `a rate limited API falls back to the latest release redirect`() = runBlocking {
        val checker = BridgeReleaseChecker(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host == "api.github.com") {
                    jsonResponse(request, "[]", code = 403, message = "rate limited")
                } else {
                    redirectResponse(
                        request,
                        "https://github.com/cachetest/redirect/releases/tag/v1.4.0",
                    )
                }
            }.build(),
        )
        assertEquals(
            "1.4.0",
            checker.latestVersion("https://github.com/cachetest/redirect"),
        )
    }

    @Test
    fun `prereleases are skipped when a stable release exists`() = runBlocking {
        val checker = BridgeReleaseChecker(
            OkHttpClient.Builder().addInterceptor { chain ->
                jsonResponse(
                    chain.request(),
                    releases(
                        release("v1.3.0-rc.1", prerelease = true),
                        release("v1.2.3"),
                    ),
                )
            }.build(),
        )
        assertEquals(
            "1.2.3",
            checker.latestVersion("https://github.com/cachetest/prerelease"),
        )
    }

    @Test
    fun `a non-github source is not queryable`() = runBlocking {
        val checker = BridgeReleaseChecker(
            OkHttpClient.Builder().addInterceptor { chain ->
                error("must not reach the network for a non-GitHub source")
            }.build(),
        )
        assertNull(checker.latestVersion("https://example.com/owner/repo"))
    }

    private fun release(tag: String, prerelease: Boolean = false): String =
        """{"tag_name":"$tag","draft":false,"prerelease":$prerelease}"""

    private fun releases(vararg items: String): String = "[" + items.joinToString(",") + "]"

    private fun jsonResponse(
        request: okhttp3.Request,
        body: String,
        code: Int = 200,
        message: String = "OK",
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun redirectResponse(request: okhttp3.Request, location: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(302)
            .message("Found")
            .header("Location", location)
            .body("".toResponseBody(null))
            .build()
}
