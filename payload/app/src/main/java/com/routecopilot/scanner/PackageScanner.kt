package com.routecopilot.scanner

import android.app.Activity
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

object PackageScanner {
    fun start(
        activity: Activity,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX
            )
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(activity, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue?.trim().orEmpty()
                if (value.isBlank()) {
                    onError("Código não identificado.")
                } else {
                    onResult(value.uppercase())
                }
            }
            .addOnCanceledListener {
                onError("Leitura cancelada.")
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Não foi possível abrir o leitor.")
            }
    }
}
