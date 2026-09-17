package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.OcrCandidate
import com.feniqo.mobile.domain.model.OcrCandidateConfidence
import com.feniqo.mobile.domain.model.ReceiptOcrDraft
import com.feniqo.mobile.domain.model.ReceiptOcrResult

object ReceiptOcrParser {
    private const val MaxInputLength = 50_000
    private const val MaxMerchantLength = 100

    // İlk dilimde birincil etiketler Codex kararıyla sınırlandırılmıştır
    private val primaryTotalLabels = listOf(
        "GENEL TOPLAM",
        "ÖDENECEK TUTAR",
        "ODENECEK TUTAR",
        "ÖDENECEK",
        "ODENECEK",
    )

    private val secondaryTotalLabels = listOf(
        "TOPLAM",
        "TOTAL",
        "T.TOPLAM",
        "T. TOPLAM",
    )

    private val ignoredMerchantTerms = listOf(
        "FİŞ", "FATURA", "TARİH", "SAAT", "TOPLAM", "TOTAL", "VERGİ", "KDV", "VKN", "TCKN",
        "TEL", "TELEFON", "ADRES", "NO:", "POS", "KASA",
    )

    // Katı sayı format regex'leri (O/I dönüşümü yapılmaz, yalnız standart rakamlar [0-9])
    private val groupedTurkishDecimalPattern = Regex("""^[0-9]{1,3}(\.[0-9]{3})+,[0-9]{1,2}$""")
    private val ungroupedTurkishDecimalPattern = Regex("""^[0-9]+,[0-9]{1,2}$""")
    private val singlePeriodDecimalPattern = Regex("""^[0-9]+\.[0-9]{1,2}$""")
    private val turkishThousandIntegerPattern = Regex("""^[0-9]{1,3}(\.[0-9]{3})+$""")
    private val plainIntegerPattern = Regex("""^[0-9]+$""")
    private val timePattern = Regex("""^[0-2]?[0-9]:[0-5][0-9](:[0-5][0-9])?$""")

    private val dayFirstDate = Regex("(?<![0-9])([0-3]?[0-9])[./-]([01]?[0-9])[./-]([0-9]{4})(?![0-9])")
    private val yearFirstDate = Regex("(?<![0-9])([0-9]{4})-([01]?[0-9])-([0-3]?[0-9])(?![0-9])")

    private data class Candidate(
        val amountMinor: Long,
        val confidence: OcrCandidateConfidence,
        val lineIndex: Int,
    )

    fun parse(
        recognizedText: String,
        currency: Currency,
    ): ReceiptOcrResult {
        if (recognizedText.length > MaxInputLength) return ReceiptOcrResult.InputTooLarge

        // Fiziksel satır sınırlarını koru
        val rawLines = recognizedText.lineSequence()
            .map(String::trim)
            .toList()

        val nonEmptyLines = rawLines.filter(String::isNotEmpty)
        if (nonEmptyLines.isEmpty()) return ReceiptOcrResult.NoCandidates

        // Tarih ve işyeri mevcut davranışla boş olmayan satırlardan aranır
        val merchantCandidate = findMerchant(nonEmptyLines)
        val dateCandidate = findDate(nonEmptyLines)
        val totalCandidate = findTotal(rawLines, currency)

        val draft = ReceiptOcrDraft(
            merchantName = merchantCandidate,
            total = totalCandidate,
            transactionDate = dateCandidate,
        )

        return if (draft.merchantName == null && draft.total == null && draft.transactionDate == null) {
            ReceiptOcrResult.NoCandidates
        } else {
            ReceiptOcrResult.Candidates(draft)
        }
    }

    private fun findTotal(rawLines: List<String>, currency: Currency): OcrCandidate<Money>? {
        val candidates = mutableListOf<Candidate>()

        for (i in rawLines.indices) {
            val line = rawLines[i]
            if (line.isEmpty()) continue

            val normalized = normalize(line)
            if (isDistractorLine(normalized)) continue

            val isPrimary = primaryTotalLabels.any { matchesTotalLabelAtStart(normalized, it) }
            val isSecondary = !isPrimary && secondaryTotalLabels.any { matchesTotalLabelAtStart(normalized, it) }

            if (!isPrimary && !isSecondary) continue

            // 1. Etiket satırındaki sayıları ayrıştır
            val lineInspection = inspectLineForAmounts(line)

            if (lineInspection.hasPoisonedOrInvalidAmount) {
                // Negatif, sıfır, taşan veya biçimi bozuk sayı var: sonraki satıra geçilmez, bu satır reddedilir
                continue
            }

            if (lineInspection.validAmounts.size > 1) {
                // K7: Aynı satırda eşdeğer farklı tutarlar varsa sessiz seçim yapılmaz
                // Bu satırdan aday üretilmez; birden fazla tutar çelişki oluşturur
                return null
            }

            if (lineInspection.validAmounts.size == 1) {
                val amount = lineInspection.validAmounts.first()
                val conf = if (isPrimary) OcrCandidateConfidence.HIGH else OcrCandidateConfidence.MEDIUM
                candidates.add(Candidate(amountMinor = amount, confidence = conf, lineIndex = i))
                continue
            }

            // 2. Etiket satırında hiç sayı yoksa: Daraltılmış çok satırlı arama (orijinal fiziksel satırlar)
            var targetLineIdx = i + 1
            if (targetLineIdx < rawLines.size && isAllowedSeparatorLine(rawLines[targetLineIdx])) {
                // İzin verilen ayırıcı satırı üzerinden tek bir geçiş yapılabilir
                targetLineIdx++
            }

            if (targetLineIdx < rawLines.size) {
                val targetLine = rawLines[targetLineIdx]
                // İkinci satır da boş veya ayırıcı ise uzaktaki sayılara ulaşma
                if (targetLine.isNotEmpty() && !isAllowedSeparatorLine(targetLine)) {
                    // Hedef satırın YALNIZCA tutar ve izin verilen işaretlerden oluştuğunu doğrula
                    if (isStrictAmountOnlyLine(targetLine)) {
                        val nextInspection = inspectLineForAmounts(targetLine)
                        if (!nextInspection.hasPoisonedOrInvalidAmount && nextInspection.validAmounts.size == 1) {
                            val amount = nextInspection.validAmounts.first()
                            // K3: Çok satırlı eşleşme birincil etiket olsa bile MEDIUM
                            candidates.add(
                                Candidate(
                                    amountMinor = amount,
                                    confidence = OcrCandidateConfidence.MEDIUM,
                                    lineIndex = targetLineIdx,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null

        val distinctAmounts = candidates.map { it.amountMinor }.distinct()

        // K5 & K6: Farklı aday tutarları çelişkidir; total=null döner
        if (distinctAmounts.size > 1) {
            return null
        }

        // Tüm adaylar aynı tutarda hemfikir:
        val singleAmount = distinctAmounts.first()
        val hasHighConfidence = candidates.any { it.confidence == OcrCandidateConfidence.HIGH }
        val finalConfidence = if (hasHighConfidence) OcrCandidateConfidence.HIGH else OcrCandidateConfidence.MEDIUM

        return OcrCandidate(
            value = Money(singleAmount, currency),
            confidence = finalConfidence,
        )
    }

    private data class LineInspectionResult(
        val validAmounts: List<Long>,
        val hasPoisonedOrInvalidAmount: Boolean,
    )

    private fun isNegativeSign(c: Char): Boolean =
        c == '-' || c == '\u2212' || c == '\u2013' || c == '\u2014'

    private fun inspectLineForAmounts(line: String): LineInspectionResult {
        // İki noktayı ayır: saat içindeki iki nokta (örn. 14:30) hariç, rakamla başlamayan iki noktaları ayır
        val preprocessedLine = line.replace(Regex("""(?<![0-9]):"""), " ")

        // Boşluklara göre ayrıştırılmış token'lar
        val rawTokens = preprocessedLine.split(Regex("""\s+""")).filter(String::isNotEmpty)
        val validAmounts = mutableListOf<Long>()
        var hasPoison = false

        for (idx in rawTokens.indices) {
            val raw = rawTokens[idx]

            // Saat (örn. 14:30 veya 14:30:00) kontrolü
            val strippedForTime = raw.trim('(', ')', '[', ']', '{', '}', '*', '#')
            if (timePattern.matches(strippedForTime)) continue

            // Tarih kontrolü (örn. 09.09.2026 veya 2026-09-09)
            if (dayFirstDate.find(raw) != null || yearFirstDate.find(raw) != null) continue

            // Yüzde kontrolü (örn. %18 veya 18%)
            if (raw.contains('%')) continue

            // Token kendisi veya sonraki/önceki token çarpan işareti veya adet/birim belirteçleri içeriyor mu?
            val normalizedRaw = normalize(raw).trim('(', ')', '[', ']', '{', '}', ':', '*', '#')
            if (normalizedRaw.startsWith("X") || normalizedRaw.endsWith("X")) continue
            if (normalizedRaw == "ADET" || normalizedRaw == "ADT" || normalizedRaw == "KALEM" ||
                normalizedRaw == "KG" || normalizedRaw == "GR" || normalizedRaw == "LT" || normalizedRaw == "PK"
            ) continue

            // Önceki token X veya SAAT, NO vs. mi?
            if (idx > 0) {
                val prevNormalized = normalize(rawTokens[idx - 1]).trim('(', ')', '[', ']', '{', '}', ':', '*', '#')
                if (prevNormalized == "X" || prevNormalized == "SAAT" || prevNormalized == "NO" ||
                    prevNormalized == "KASA" || prevNormalized == "KASIYER"
                ) continue
            }

            // Sonraki token ADET, KALEM, KG vs. mi?
            if (idx + 1 < rawTokens.size) {
                val nextNormalized = normalize(rawTokens[idx + 1]).trim('(', ')', '[', ']', '{', '}', ':', '*', '#')
                if (nextNormalized == "ADET" || nextNormalized == "ADT" || nextNormalized == "KALEM" ||
                    nextNormalized == "KG" || nextNormalized == "GR" || nextNormalized == "LT" ||
                    nextNormalized == "PK" || nextNormalized == "X"
                ) continue
            }

            // Süs ve parantez işaretlerini temizle
            var clean = raw.trim('(', ')', '[', ']', '{', '}', ':', '*', '#')

            // Eksi işareti kontrolü (baştaki/sondaki bitişik eksi veya önceki/sonraki ayrık eksi)
            val hasDigits = clean.any { it.isDigit() }
            val isExplicitNegative = hasDigits && (isNegativeSign(clean.first()) || isNegativeSign(clean.last()))
            val hasPrecedingMinus = hasDigits && idx > 0 && rawTokens[idx - 1].length == 1 && isNegativeSign(rawTokens[idx - 1][0])
            val hasFollowingMinus = hasDigits && idx + 1 < rawTokens.size && rawTokens[idx + 1].length == 1 && isNegativeSign(rawTokens[idx + 1][0])

            if (isExplicitNegative || hasPrecedingMinus || hasFollowingMinus) {
                // Negatif sayı: kesin zehirli sayı
                hasPoison = true
                continue
            }

            // Para birimi eklerini yalnızca açıkça tanımlanan önek/sonek konumlarında temizle
            clean = stripCurrencyAffixes(clean)

            if (clean.isEmpty() || clean.none { it.isDigit() }) continue

            // Sayı formatını tam token üzerinde doğrula
            val minor = parseStrictMinorUnits(clean)
            if (minor == null) {
                // Rakam içeriyor ama geçerli tutar sınırında/biçiminde değil (sıfır, taşma, bozuk format)
                hasPoison = true
            } else {
                validAmounts.add(minor)
            }
        }

        return LineInspectionResult(
            validAmounts = validAmounts,
            hasPoisonedOrInvalidAmount = hasPoison,
        )
    }

    private fun stripCurrencyAffixes(token: String): String {
        var s = token
        // Önek para birimleri
        if (s.startsWith("₺") || s.startsWith("€") || s.startsWith("$")) {
            s = s.substring(1)
        } else if (s.startsWith("TL", ignoreCase = true)) {
            s = s.substring(2)
        } else if (s.startsWith("TRY", ignoreCase = true) || s.startsWith("EUR", ignoreCase = true) || s.startsWith("USD", ignoreCase = true)) {
            s = s.substring(3)
        }

        // Sonek para birimleri
        if (s.endsWith("₺") || s.endsWith("€") || s.endsWith("$")) {
            s = s.dropLast(1)
        } else if (s.endsWith("TL", ignoreCase = true)) {
            s = s.dropLast(2)
        } else if (s.endsWith("TRY", ignoreCase = true) || s.endsWith("EUR", ignoreCase = true) || s.endsWith("USD", ignoreCase = true)) {
            s = s.dropLast(3)
        }

        return s.trim('*', '#', ':', ' ')
    }

    private fun parseStrictMinorUnits(token: String): Long? {
        if (token.isEmpty() || isNegativeSign(token.first())) return null

        val (wholeStr, fractionStr) = when {
            groupedTurkishDecimalPattern.matches(token) -> {
                val whole = token.substringBeforeLast(',').replace(".", "")
                val fraction = token.substringAfterLast(',')
                whole to fraction
            }
            ungroupedTurkishDecimalPattern.matches(token) -> {
                val whole = token.substringBeforeLast(',')
                val fraction = token.substringAfterLast(',')
                whole to fraction
            }
            singlePeriodDecimalPattern.matches(token) -> {
                val whole = token.substringBeforeLast('.')
                val fraction = token.substringAfterLast('.')
                whole to fraction
            }
            turkishThousandIntegerPattern.matches(token) -> {
                // Türkçe binlik gruplama politikası (örn. 1.234 -> 123400 kuruş)
                val whole = token.replace(".", "")
                whole to ""
            }
            plainIntegerPattern.matches(token) -> {
                token to ""
            }
            else -> return null
        }

        val whole = wholeStr.toLongOrNull() ?: return null
        val fraction = when (fractionStr.length) {
            0 -> 0L
            1 -> fractionStr.toLong() * 10L
            2 -> fractionStr.toLong()
            else -> return null
        }

        if (whole > (Money.MAX_AMOUNT_MINOR - fraction) / 100L) return null
        val totalMinor = whole * 100L + fraction
        if (totalMinor !in 1..Money.MAX_AMOUNT_MINOR) return null

        return totalMinor
    }

    private fun isAllowedSeparatorLine(line: String): Boolean {
        if (line.isEmpty()) return true
        val stripped = line.replace(" ", "")
            .replace("*", "")
            .replace("_", "")
            .replace("=", "")
            .replace("#", "")
            .replace(":", "")
            .replace(".", "")
            .replace("TL", "", ignoreCase = true)
            .replace("TRY", "", ignoreCase = true)
            .replace("EUR", "", ignoreCase = true)
            .replace("USD", "", ignoreCase = true)
            .replace("₺", "")
            .replace("€", "")
            .replace("$", "")
        return stripped.isEmpty()
    }

    private fun isStrictAmountOnlyLine(line: String): Boolean {
        // Çizgi, harf, ürün veya metadata sözcüğü içermemeli
        val stripped = line.replace(" ", "")
            .replace("*", "")
            .replace("#", "")
            .replace(":", "")
            .replace(",", "")
            .replace(".", "")
            .replace("TL", "", ignoreCase = true)
            .replace("TRY", "", ignoreCase = true)
            .replace("EUR", "", ignoreCase = true)
            .replace("USD", "", ignoreCase = true)
            .replace("₺", "")
            .replace("€", "")
            .replace("$", "")
            .replace("(", "")
            .replace(")", "")
        return stripped.isNotEmpty() && stripped.all { it.isDigit() }
    }

    private fun isDistractorLine(normalized: String): Boolean {
        val hasVatIncluded = normalized.contains("KDV DAHIL") ||
            normalized.contains("KDVLI") ||
            normalized.contains("KDV DAHILDIR")

        if (!hasVatIncluded) {
            if (normalized.contains("TOPLAM KDV") ||
                normalized.contains("KDV TOPLAM") ||
                normalized.contains("KDV TOPLAMI") ||
                normalized.contains("VERGI TOPLAM") ||
                normalized.contains("VERGI TOPLAMI") ||
                normalized.contains("KDV %") ||
                normalized.contains("% KDV") ||
                normalized.contains("KDV:") ||
                normalized.contains(" KDV ") ||
                normalized.startsWith("KDV ") ||
                normalized.endsWith(" KDV") ||
                normalized.contains("KDV TUTARI")
            ) {
                return true
            }
        }

        return normalized.contains("ARA TOPLAM") ||
            normalized.contains("ARATOPLAM") ||
            normalized.contains("SUBTOTAL") ||
            normalized.contains("SUB TOTAL") ||
            normalized.contains("TOPLAM URUN") ||
            normalized.contains("TOPLAM ADET") ||
            normalized.contains("TOPLAM KALEM") ||
            normalized.contains("TOPLAM INDIRIM") ||
            normalized.contains("INDIRIM TOPLAMI") ||
            normalized.contains("INDIRIM") ||
            normalized.contains("ISKONTO") ||
            normalized.contains("PARA USTU") ||
            normalized.contains("NAKIT VERILEN")
    }

    private fun matchesTotalLabelAtStart(normalizedLine: String, label: String): Boolean {
        val trimmed = normalizedLine.trimStart('*', '#', '-', '_', '=', ' ')
        val normalizedLabel = normalize(label)
        if (!trimmed.startsWith(normalizedLabel)) return false
        if (trimmed.length == normalizedLabel.length) return true
        val nextChar = trimmed[normalizedLabel.length]
        return nextChar.isWhitespace() || nextChar in ":.-_=#*"
    }

    private fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                'İ', 'ı', 'I', 'i' -> builder.append('I')
                'Ö', 'ö', 'O', 'o' -> builder.append('O')
                'Ü', 'ü', 'U', 'u' -> builder.append('U')
                'Ş', 'ş', 'S', 's' -> builder.append('S')
                'Ç', 'ç', 'C', 'c' -> builder.append('C')
                'Ğ', 'ğ', 'G', 'g' -> builder.append('G')
                else -> builder.append(ch.uppercaseChar())
            }
        }
        return builder.toString()
    }

    private fun findDate(lines: List<String>): OcrCandidate<LocalDate>? {
        lines.forEach { line ->
            yearFirstDate.find(line)?.let { match ->
                safeDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    ?.let { return OcrCandidate(it, OcrCandidateConfidence.HIGH) }
            }
            dayFirstDate.find(line)?.let { match ->
                safeDate(match.groupValues[3], match.groupValues[2], match.groupValues[1])
                    ?.let {
                        val confidence = if (line.uppercase().contains("TARİH")) {
                            OcrCandidateConfidence.HIGH
                        } else {
                            OcrCandidateConfidence.MEDIUM
                        }
                        return OcrCandidate(it, confidence)
                    }
            }
        }
        return null
    }

    private fun findMerchant(lines: List<String>): OcrCandidate<String>? = lines
        .asSequence()
        .take(8)
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .firstOrNull { line ->
            line.length in 2..MaxMerchantLength &&
                line.any(Char::isLetter) &&
                ignoredMerchantTerms.none { term -> line.uppercase().contains(term) } &&
                dayFirstDate.find(line) == null && yearFirstDate.find(line) == null
        }
        ?.let { OcrCandidate(it, OcrCandidateConfidence.LOW) }

    private fun safeDate(year: String, month: String, day: String): LocalDate? = try {
        LocalDate(year.toInt(), month.toInt(), day.toInt())
    } catch (_: IllegalArgumentException) {
        null
    }
}
