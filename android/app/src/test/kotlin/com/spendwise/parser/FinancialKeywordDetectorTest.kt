package com.spendwise.parser

import com.spendwise.parser.samples.SampleSms
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialKeywordDetectorTest {

    @Test
    fun `random OTP SMS is not financial`() {
        assertFalse(FinancialKeywordDetector.isFinancial(SampleSms.OTP, sender = "VM-HDFCBK"))
    }

    @Test
    fun `promotional SMS is not financial`() {
        assertFalse(FinancialKeywordDetector.isFinancial(SampleSms.PROMOTIONAL, sender = "AD-SHOPZY"))
    }

    @Test
    fun `known financial SMS from an unrecognized sender is financial (keyword-based, not sender-based)`() {
        assertTrue(
            FinancialKeywordDetector.isFinancial(SampleSms.UNKNOWN_SENDER_FINANCIAL, sender = "XY-UNKNWN")
        )
    }

    @Test
    fun `SBI debit SMS is financial`() {
        assertTrue(FinancialKeywordDetector.isFinancial(SampleSms.SBI_DEBIT, sender = "VM-SBIINB"))
    }

    @Test
    fun `Paytm debit SMS is financial`() {
        assertTrue(FinancialKeywordDetector.isFinancial(SampleSms.PAYTM_DEBIT, sender = "VM-PAYTM"))
    }

    @Test
    fun `GPay debit SMS is financial`() {
        assertTrue(FinancialKeywordDetector.isFinancial(SampleSms.GPAY_DEBIT, sender = "GOOGLPAY"))
    }
}
