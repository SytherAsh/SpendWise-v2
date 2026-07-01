package com.spendwise.parser.samples

/**
 * Realistic sample SMS text used across parser and keyword-filter tests.
 * Centralized here per docs/development_guidelines.md "Adding a New SMS Sender".
 */
object SampleSms {

    // -- Non-financial --

    const val OTP = "123456 is your OTP for login. Do not share your OTP with anyone. Valid for 10 minutes."
    const val PROMOTIONAL = "Flat 50% off on all electronics! Limited period offer, sale ends tonight. Shop now and win exciting prizes."

    // -- SBI --

    const val SBI_DEBIT = "Your A/c XXXX2345 is debited for Rs.500 on 28-Jun-2026 UPI Ref no. 123456789012"
    const val SBI_CREDIT = "INR 1,500.00 credited to your a/c XXXX2345 on 27-Jun-2026"
    const val SBI_MALFORMED = "A/c XXXX debited Rs 500 sometime recently, ref unavailable"

    // -- Paytm --

    const val PAYTM_DEBIT = "Rs.200 paid to Swiggy using Paytm UPI. Ref no: PAYTM123456"
    const val PAYTM_PARTIAL = "Rs.150 paid using Paytm UPI."

    // -- GPay --

    const val GPAY_DEBIT = "You have sent Rs.350.00 to restaurant@okhdfc using Google Pay UPI"
    const val GPAY_PARTIAL = "You have sent money using Google Pay UPI"

    // -- Unknown sender --

    const val UNKNOWN_SENDER_FINANCIAL =
        "Alert: Your account XX1234 has been debited with Rs 799.00 on 01-Jul-2026 towards purchase at BigBazaar"
}
