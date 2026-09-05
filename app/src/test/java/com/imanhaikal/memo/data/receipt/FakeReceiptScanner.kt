package com.imanhaikal.memo.data.receipt

import android.net.Uri

class FakeReceiptScanner(
    override var isAvailable: Boolean = true,
    var outcome: ScanOutcome = ScanOutcome.Failure(ScanFailureReason.UNREADABLE)
) : ReceiptScanner {
    override suspend fun scan(uri: Uri): ScanOutcome = outcome
}
