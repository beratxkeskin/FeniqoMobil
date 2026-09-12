package com.feniqo.mobile.ocr

import android.net.Uri
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.OcrCandidate
import com.feniqo.mobile.domain.model.OcrCandidateConfidence
import com.feniqo.mobile.domain.model.ReceiptOcrDraft
import com.feniqo.mobile.domain.model.ReceiptOcrResult
import com.feniqo.mobile.presentation.sync.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReceiptOcrViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun recognizedCandidates_waitForExplicitConsumption() = runTest {
        val expected = ReceiptOcrDraft(
            merchantName = OcrCandidate("Market", OcrCandidateConfidence.LOW),
            total = OcrCandidate(Money(1_250, Currency.TRY), OcrCandidateConfidence.HIGH),
            transactionDate = null,
        )
        val viewModel = ReceiptOcrViewModel(FakeService(ReceiptOcrResult.Candidates(expected)))

        viewModel.recognize(Uri.parse("content://receipt/1"), Currency.TRY)
        testScheduler.advanceUntilIdle()

        assertEquals(expected, viewModel.state.value.draft)
        assertFalse(viewModel.state.value.isProcessing)
        viewModel.consumeDraft()
        assertNull(viewModel.state.value.draft)
    }

    @Test
    fun noCandidates_exposesRetryableMessage() = runTest {
        val viewModel = ReceiptOcrViewModel(FakeService(ReceiptOcrResult.NoCandidates))

        viewModel.recognize(Uri.parse("content://receipt/2"), Currency.TRY)
        testScheduler.advanceUntilIdle()

        assertEquals("Makbuzdan kullanılabilir bilgi okunamadı.", viewModel.state.value.errorMessage)
        viewModel.dismissError()
        assertNull(viewModel.state.value.errorMessage)
    }

    private class FakeService(private val result: ReceiptOcrResult) : ReceiptOcrService {
        override suspend fun recognize(imageUri: Uri, currency: Currency): ReceiptOcrResult = result
    }
}
