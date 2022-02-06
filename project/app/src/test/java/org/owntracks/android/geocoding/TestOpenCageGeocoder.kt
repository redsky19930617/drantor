package org.owntracks.android.geocoding

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit

class TestOpenCageGeocoder {

    @Test
    fun `Given an incomplete OpenCage Response, when deserialized then no error is thrown`() {
        var openCageJSON = """
            {"documentation":"https://opencagedata.com/api","rate":{"reset":123}}
        """.trimIndent()

        val deserialized = OpenCageGeocoder("").deserializeOpenCageResponse(openCageJSON)
        assertNull(deserialized.formatted)
        assertNull(deserialized.results)
        assertNotNull(deserialized.rate)
        assertEquals(0, deserialized.rate?.limit)
        assertEquals(0, deserialized.rate?.remaining)
        assertEquals(Instant.ofEpochSecond(123), deserialized.rate?.reset)
    }

    @Test
    fun `Given a successful response from OpenCage, the correct formatted address is returned`() {
        val openCageJSON = this.javaClass.getResource("/openCage/opencageResult.json")!!.readText()
        assertNotNull(openCageJSON)

        val httpResponse = Response.Builder()
            .body(openCageJSON.toResponseBody("application/json".toMediaTypeOrNull()))
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("Ok")
            .build()

        val httpCall: Call = mock { on { execute() } doReturn httpResponse }
        val httpClient: OkHttpClient = mock { on { newCall(any()) } doReturn httpCall }

        val geocoder = OpenCageGeocoder("", httpClient)

        val response = geocoder.reverse(0.0, 0.0)
        assert(response is GeocodeResult.Formatted)
        assertEquals(
            "Friedrich-Ebert-Straße 7, 48153 Münster, Germany",
            (response as GeocodeResult.Formatted).text
        )
    }

    @Test
    fun `Given a disabled response from OpenCage, a disabled message is returned`() {
        val openCageJSON =
            this.javaClass.getResource("/openCage/opencageDisabledResult.json")!!.readText()
        assertNotNull(openCageJSON)

        val httpResponse = Response.Builder()
            .body(openCageJSON.toResponseBody("application/json".toMediaTypeOrNull()))
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Ok")
            .build()

        val httpCall: Call = mock { on { execute() } doReturn httpResponse }
        val httpClient: OkHttpClient = mock { on { newCall(any()) } doReturn httpCall }

        val geocoder = OpenCageGeocoder("", httpClient)

        val response = geocoder.reverse(0.0, 0.0)
        assert(response is GeocodeResult.Fault.Disabled)
    }

    @Test
    fun `Given an IP Address Rejected response from OpenCage, a rejected message is returned`() {
        val openCageJSON =
            this.javaClass.getResource("/openCage/opencageIPAddressRejectedResult.json")!!
                .readText()
        assertNotNull(openCageJSON)

        val httpResponse = Response.Builder()
            .body(openCageJSON.toResponseBody("application/json".toMediaTypeOrNull()))
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Ok")
            .build()

        val httpCall: Call = mock { on { execute() } doReturn httpResponse }
        val httpClient: OkHttpClient = mock { on { newCall(any()) } doReturn httpCall }

        val geocoder = OpenCageGeocoder("", httpClient)

        val response = geocoder.reverse(0.0, 0.0)
        assert(response is GeocodeResult.Fault.IPAddressRejected)
    }

    @Test
    fun `Given a rate limited response from OpenCage, a rate limited message is returned with an appropriate expiry`() {
        val openCageJSON =
            this.javaClass.getResource("/openCage/opencageRateLimitedResult.json")!!.readText()
        assertNotNull(openCageJSON)

        val httpResponse = Response.Builder()
            .body(openCageJSON.toResponseBody("application/json".toMediaTypeOrNull()))
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Ok")
            .build()

        val httpCall: Call = mock { on { execute() } doReturn httpResponse }
        val httpClient: OkHttpClient = mock { on { newCall(any()) } doReturn httpCall }

        val geocoder = OpenCageGeocoder("", httpClient)

        val response = geocoder.reverse(0.0, 0.0)
        assert(response is GeocodeResult.Fault.RateLimited)
        assert((response as GeocodeResult.Fault.RateLimited).until > Instant.now())
        assert(response.until < Instant.now().plus(5, ChronoUnit.MINUTES))
    }

    @Test
    fun `Given a quota response from OpenCage, a quota limited message is returned with an appropriate expiry`() {
        val openCageJSON =
            this.javaClass.getResource("/openCage/opencageOutOfQuotaResult.json")!!.readText()
        assertNotNull(openCageJSON)

        val httpResponse = Response.Builder()
            .body(openCageJSON.toResponseBody("application/json".toMediaTypeOrNull()))
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(402)
            .message("Ok")
            .build()

        val httpCall: Call = mock { on { execute() } doReturn httpResponse }
        val httpClient: OkHttpClient = mock { on { newCall(any()) } doReturn httpCall }

        val geocoder = OpenCageGeocoder("", httpClient)

        val response = geocoder.reverse(0.0, 0.0)
        assert(response is GeocodeResult.Fault.RateLimited)
        assertEquals(
            Instant.parse("2021-03-05T00:00:00Z"),
            (response as GeocodeResult.Fault.RateLimited).until
        )
    }
}