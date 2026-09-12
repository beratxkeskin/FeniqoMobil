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

    private val totalLabels = listOf("GENEL TOPLAM", "ÖDENECEK", "TOPLAM", "TOTAL")
    private val ignoredMerchantTerms = listOf(
        "FİŞ", "FATURA", "TARİH", "SAAT", "TOPLAM", "TOTAL", "VERGİ", "KDV", "VKN", "TCKN",
        "TEL", "TELEFON", "ADRES", "NO:", "POS", "KASA",
    )
    private val amountToken = Regex("(?<![0-9])([0-9]{1,3}(?:[ .][0-9]{3})*(?:,[0-9]{1,2})|[0-9]+,[0-9]{1,2}|[0-9]+\\.[0-9]{1,2}|[0-9]+)(?![0-9])")
    private val dayFirstDate = Regex("(?<![0-9])([0-3]?[0-9])[./-]([01]?[0-9])[./-]([0-9]{4})(?![0-9])")
    private val yearFirstDate = Regex("(?<![0-9])([0-9]{4})-([01]?[0-9])-([0-3]?[0-9])(?![0-9])")

    fun parse(
        recognizedText: String,
        currency: Currency,
    ): ReceiptOcrResult {
        if (recognizedText.length > MaxInputLength) return ReceiptOcrResult.InputTooLarge
        val lines = recognizedText.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (lines.isEmpty()) return ReceiptOcrResult.NoCandidates

        val draft = ReceiptOcrDraft(
            merchantName = findMerchant(lines),
            total = findTotal(lines, currency),
            transactionDate = findDate(lines),
        )
        return if (draft.merchantName == null && draft.total == null && draft.transactionDate == null) {
            ReceiptOcrResult.NoCandidates
        } else {
            ReceiptOcrResult.Candidates(draft)
        }
    }

    private fun findTotal(lines: List<String>, currency: Currency): OcrCandidate<Money>? {
        totalLabels.forEachIndexed { index, label ->
            lines.asReversed().firstOrNull { it.uppercase().contains(label) }
                ?.let { line ->
                    amountToken.findAll(line).lastOrNull()?.groupValues?.get(1)
                        ?.toMinorUnits()
                        ?.takeIf { it in 1..Money.MAX_AMOUNT_MINOR }
                        ?.let { amount ->
                            return OcrCandidate(
                                value = Money(amount, currency),
                                confidence = if (index <= 1) {
                                    OcrCandidateConfidence.HIGH
                                } else {
                                    OcrCandidateConfidence.MEDIUM
                                },
                            )
                        }
                }
        }
        return null
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

    private fun String.toMinorUnits(): Long? {
        val compact = replace(" ", "")
        val decimalSeparator = when {
            ',' in compact -> ','
            compact.count { it == '.' } == 1 && compact.substringAfterLast('.').length in 1..2 -> '.'
            else -> null
        }
        val integerDigits = if (decimalSeparator == null) {
            compact.replace(".", "")
        } else {
            compact.substringBeforeLast(decimalSeparator).replace(".", "").replace(",", "")
        }
        val fractionDigits = decimalSeparator?.let { compact.substringAfterLast(it) }.orEmpty()
        if (integerDigits.isEmpty() || integerDigits.any { !it.isDigit() } ||
            fractionDigits.any { !it.isDigit() } || fractionDigits.length > 2
        ) return null
        val whole = integerDigits.toLongOrNull() ?: return null
        val fraction = when (fractionDigits.length) {
            0 -> 0L
            1 -> fractionDigits.toLong() * 10L
            else -> fractionDigits.toLong()
        }
        if (whole > (Money.MAX_AMOUNT_MINOR - fraction) / 100L) return null
        return whole * 100L + fraction
    }

    private fun safeDate(year: String, month: String, day: String): LocalDate? = try {
        LocalDate(year.toInt(), month.toInt(), day.toInt())
    } catch (_: IllegalArgumentException) {
        null
    }
}
