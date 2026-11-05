package com.feniqo.mobile.presentation.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MerchantInitialHelperTest {

    @Test
    fun extractInitial_deseretSmallLetterLongI_convertsToUppercaseAndRetainsSurrogatePairLength() {
        // U+10428: DESERET SMALL LETTER LONG I ("\uD801\uDC28")
        // U+10400: DESERET CAPITAL LETTER LONG I ("\uD801\uDC00")
        val deseretSmallI = "\uD801\uDC28"
        val expectedDeseretCapitalI = "\uD801\uDC00"

        val label = "$deseretSmallI market"
        val initial = MerchantInitialHelper.extractInitial(label)

        assertEquals(expectedDeseretCapitalI, initial)
        assertEquals(2, initial?.length, "Surrogate pair must retain length 2 chars")
    }

    @Test
    fun extractInitial_oldItalicLetterA_recognizedAsLetterAndDoesNotSkipToFollowingLatinM() {
        // U+10300: OLD ITALIC LETTER A ("\uD800\uDF00") - Unicode Category: Other_Letter
        val oldItalicA = "\uD800\uDF00"
        val label = "$oldItalicA Market"
        val initial = MerchantInitialHelper.extractInitial(label)

        // Gerçek bir Unicode harfi olduğundan sonraki Latin 'M' harfine atlamamalı
        assertEquals(oldItalicA, initial)
        assertEquals(2, initial?.length)
    }

    @Test
    fun extractInitial_brahmiSignCandrabinduMark_skipsMarkAndResolvesLatinM() {
        // U+11000: BRAHMI SIGN CANDRABINDU ("\uD804\uDC00") - Unicode Category: Nonspacing_Mark
        val brahmiMark = "\uD804\uDC00"
        val label = "${brahmiMark}Market"
        val initial = MerchantInitialHelper.extractInitial(label)

        // U+11000 harf değil bir mark'tır; atlanmalı ve ilk gerçek harf olan 'M' çözülmelidir
        assertEquals("M", initial)
    }

    @Test
    fun extractInitial_combiningGlagoliticLetterAzuMark_skipsMarkAndResolvesLatinS() {
        // U+1E000: COMBINING GLAGOLITIC LETTER AZU ("\uD838\uDC00") - Unicode Category: Nonspacing_Mark
        val combiningMark = "\uD838\uDC00"
        val label = "${combiningMark}Spotify"
        val initial = MerchantInitialHelper.extractInitial(label)

        // U+1E000 harf değil bir combining mark'tır; atlanmalı ve 'S' çözülmelidir
        assertEquals("S", initial)
    }

    @Test
    fun extractInitial_supplementaryEmojiFollowedBySupplementaryLetter_skipsEmojiAndSelectsLetter() {
        // U+1F6D2: SHOPPING CART ("\uD83D\uDED2") - Symbol
        // U+10400: DESERET CAPITAL LETTER LONG I ("\uD801\uDC00") - Letter
        val shoppingCartEmoji = "\uD83D\uDED2"
        val deseretCapitalI = "\uD83D\uDED2\uD801\uDC00 Market"
        val initial = MerchantInitialHelper.extractInitial(deseretCapitalI)

        assertEquals("\uD801\uDC00", initial)
        assertEquals(2, initial?.length)
    }

    @Test
    fun extractInitial_isolatedHighSurrogate_skipsMalformedCharAndResolvesLatinM() {
        // İzole high surrogate ("\uD800" ardında low surrogate yok)
        val malformedLabel = "\uD800Market"
        val initial = MerchantInitialHelper.extractInitial(malformedLabel)

        assertEquals("M", initial)
    }

    @Test
    fun extractInitial_isolatedLowSurrogate_skipsMalformedCharAndResolvesLatinM() {
        // İzole low surrogate ("\uDC00" önünde high surrogate yok)
        val malformedLabel = "\uDC00Market"
        val initial = MerchantInitialHelper.extractInitial(malformedLabel)

        assertEquals("M", initial)
    }

    @Test
    fun extractInitial_leadingNumbersAndSymbols_skipsToFirstUnicodeLetter() {
        assertEquals("M", MerchantInitialHelper.extractInitial("101 Market"))
        assertEquals("M", MerchantInitialHelper.extractInitial("--- 101 Market"))
        assertEquals("S", MerchantInitialHelper.extractInitial("🛒 Süpermarket"))
        assertEquals("A", MerchantInitialHelper.extractInitial("123 A-Market"))
    }

    @Test
    fun extractInitial_numbersOnly_returnsNull() {
        assertNull(MerchantInitialHelper.extractInitial("123"))
        assertNull(MerchantInitialHelper.extractInitial("101"))
        assertNull(MerchantInitialHelper.extractInitial("2026"))
    }

    @Test
    fun extractInitial_turkishDottedI_resolvesCorrectly() {
        assertEquals("İ", MerchantInitialHelper.extractInitial("istanbul"))
        assertEquals("İ", MerchantInitialHelper.extractInitial("İstanbul"))
        assertEquals("İ", MerchantInitialHelper.extractInitial("İZMİR"))
        assertEquals("İ", MerchantInitialHelper.extractInitial("i̇stanbul")) // combining dot
    }

    @Test
    fun extractInitial_turkishDotlessI_resolvesCorrectly() {
        assertEquals("I", MerchantInitialHelper.extractInitial("ışık"))
        assertEquals("I", MerchantInitialHelper.extractInitial("Işık"))
        assertEquals("I", MerchantInitialHelper.extractInitial("ISPARTA"))
    }

    @Test
    fun extractInitial_standardLetters_convertsToUppercase() {
        assertEquals("S", MerchantInitialHelper.extractInitial("spotify"))
        assertEquals("N", MerchantInitialHelper.extractInitial("Netflix"))
        assertEquals("T", MerchantInitialHelper.extractInitial("trendyol"))
    }

    @Test
    fun extractInitial_withLeadingWhitespace_trimsCorrectly() {
        assertEquals("M", MerchantInitialHelper.extractInitial("   migros"))
    }

    @Test
    fun extractInitial_blankNullOrSymbolsOnly_returnsNull() {
        assertNull(MerchantInitialHelper.extractInitial(null))
        assertNull(MerchantInitialHelper.extractInitial(""))
        assertNull(MerchantInitialHelper.extractInitial("   "))
        assertNull(MerchantInitialHelper.extractInitial("---"))
        assertNull(MerchantInitialHelper.extractInitial("..."))
        assertNull(MerchantInitialHelper.extractInitial("!@#$%^&*()"))
        assertNull(MerchantInitialHelper.extractInitial("🚀🔥"))
    }
}
