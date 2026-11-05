package com.feniqo.mobile.presentation.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteMerchantLogoRequestTest {
    @Test
    fun toString_masksClientId_andHidesHeaderValues() {
        val secretClientId = "secret_client_id_987"
        val request =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/domain/migros.com.tr/w/128/h/128/fallback/404/type/icon?c=$secretClientId",
                headers =
                    mapOf(
                        "Referer" to "https://app.feniqo.com",
                        "User-Agent" to "FeniqoMobil/1.0 (Android 34)",
                    ),
            )

        val stringRepresentation = request.toString()

        // 1. Secret Client ID kesinlikle sızmamalı
        assertFalse(
            stringRepresentation.contains(secretClientId),
            "Client ID must not leak into toString()",
        )

        // 2. c query parametresi maskelenmeli
        assertTrue(
            stringRepresentation.contains("c=***"),
            "Client ID query param must be masked with 'c=***'",
        )

        // 3. Header değerleri (Referer ve User-Agent) sızmamalı, yalnız adlar ve sayı görünmeli
        assertFalse(
            stringRepresentation.contains("https://app.feniqo.com"),
            "Referer header value must not leak into toString()",
        )
        assertFalse(
            stringRepresentation.contains("FeniqoMobil/1.0"),
            "User-Agent header value must not leak into toString()",
        )
        assertTrue(
            stringRepresentation.contains("headers=[Referer, User-Agent]"),
            "Sorted header names must be displayed",
        )
        assertTrue(
            stringRepresentation.contains("headersCount=2"),
            "Header count must be displayed",
        )
    }

    @Test
    fun toString_multipleQueryParams_masksOnlyClientId() {
        val secretClientId = "secret_client_id_987"
        val request =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/domain/bim.com.tr/icon?w=64&c=$secretClientId&h=64",
                headers = mapOf("Referer" to "https://app.feniqo.com"),
            )

        val stringRepresentation = request.toString()

        assertFalse(stringRepresentation.contains(secretClientId))
        assertTrue(stringRepresentation.contains("w=64&c=***&h=64"))
    }

    @Test
    fun toString_blankOrPercentEncodedClientId_doesNotCrash() {
        val emptyClientParamRequest =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/domain/a101.com.tr/icon?c=",
            )
        assertTrue(emptyClientParamRequest.toString().contains("c=***"))

        val percentEncodedRequest =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/domain/a101.com.tr/icon?c=secret%20123&test=1",
            )
        val encodedString = percentEncodedRequest.toString()
        assertFalse(encodedString.contains("secret%20123"))
        assertTrue(encodedString.contains("c=***&test=1"))

        val noClientParamRequest =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/domain/a101.com.tr/icon",
            )
        assertEquals(
            "RemoteMerchantLogoRequest(" +
                "url=https://cdn.brandfetch.io/domain/a101.com.tr/icon, " +
                "headers=[], " +
                "headersCount=0, " +
                "cachePolicy=STRICT_NO_STORE)",
            noClientParamRequest.toString(),
        )
    }

    @Test
    fun toString_masksLogoDevToken_andPreservesSafeUrlParts() {
        val secretToken = "pk_synthetic_secret_key_12345"
        val request =
            RemoteMerchantLogoRequest(
                url = "https://img.logo.dev/migros.com.tr?token=$secretToken&size=128&format=png&fallback=404",
                headers =
                    mapOf(
                        "Authorization" to "Bearer sensitive_auth_token",
                        "User-Agent" to "FeniqoMobil/1.0.0",
                    ),
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )

        val str = request.toString()

        assertFalse(str.contains(secretToken), "Logo.dev token değeri toString()'e sızamaz: $str")
        assertFalse(str.contains("secret_key_12345"), "Token içeriği toString()'e sızamaz: $str")
        assertFalse(str.contains("sensitive_auth_token"), "Header değeri toString()'e sızamaz: $str")
        assertTrue(str.contains("token=***"), "token parametresi maskelenmelidir: $str")
        assertTrue(str.contains("size=128&format=png&fallback=404"), "URL'nin güvenli kısımları korunmalıdır: $str")
        assertTrue(str.contains("headers=[Authorization, User-Agent]"), "Header adları sıralı gösterilmelidir: $str")
        assertTrue(str.contains("cachePolicy=PROVIDER_HEADERS_NO_LOCAL_STORAGE"), "Cache policy gösterilmelidir: $str")
    }

    @Test
    fun toString_masksBothBrandfetchAndLogoDevTokens_ifBothPresent() {
        val request =
            RemoteMerchantLogoRequest(
                url = "https://example.com/icon?c=secret_client_val&token=secret_token_val&safe=1",
            )
        val str = request.toString()

        assertFalse(str.contains("secret_client_val"), "c değeri sızamaz: $str")
        assertFalse(str.contains("secret_token_val"), "token değeri sızamaz: $str")
        assertTrue(str.contains("c=***&token=***&safe=1"), "Hem c hem token maskelenmeli ve diğerleri korunmalı: $str")
    }

    @Test
    fun equalityAndHashCode_matchContract() {
        val req1 =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/icon",
                headers = mapOf("Referer" to "https://app.feniqo.com"),
                cachePolicy = RemoteMerchantLogoCachePolicy.STRICT_NO_STORE,
            )
        val req2 =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/icon",
                headers = mapOf("Referer" to "https://app.feniqo.com"),
                cachePolicy = RemoteMerchantLogoCachePolicy.STRICT_NO_STORE,
            )
        val req3 =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/other",
                headers = mapOf("Referer" to "https://app.feniqo.com"),
                cachePolicy = RemoteMerchantLogoCachePolicy.STRICT_NO_STORE,
            )
        val reqPolicyDiff =
            RemoteMerchantLogoRequest(
                url = "https://cdn.brandfetch.io/icon",
                headers = mapOf("Referer" to "https://app.feniqo.com"),
                cachePolicy = RemoteMerchantLogoCachePolicy.PROVIDER_HEADERS_NO_LOCAL_STORAGE,
            )

        assertEquals(req1, req2)
        assertEquals(req1.hashCode(), req2.hashCode())
        assertFalse(req1 == req3)
        assertFalse(req1 == reqPolicyDiff)
    }
}
