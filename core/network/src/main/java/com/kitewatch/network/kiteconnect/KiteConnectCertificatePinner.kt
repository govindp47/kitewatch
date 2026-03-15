package com.kitewatch.network.kiteconnect

import okhttp3.CertificatePinner

/**
 * Builds the OkHttp [CertificatePinner] for the Kite Connect API hostname.
 *
 * Three SPKI SHA-256 pins are included for `api.kite.trade`:
 *
 * | Cert | Subject | Pin |
 * |---|---|---|
 * | Leaf | *.kite.trade | `HUdH3qmn8f23Xy4zTVMLvw3pkmjc1i1pUlnLQuIaOUY=` |
 * | Intermediate | Sectigo RSA Domain Validation Secure Server CA | `4a6cPehI7OG6cuDZka5NDZ7FR8a60d3auda+sKfg4Ng=` |
 * | Root | USERTrust RSA Certification Authority | `x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td4=` |
 *
 * Pins extracted on 2026-03-15 via:
 * ```
 * openssl s_client -connect api.kite.trade:443 -showcerts 2>/dev/null \
 *   | awk '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/' \
 *   | openssl x509 -pubkey -noout \
 *   | openssl pkey -pubin -outform DER \
 *   | openssl dgst -sha256 -binary | base64
 * ```
 *
 * **PIN ROTATION:** When Zerodha rotates the server certificate, the new leaf pin MUST
 * be added to this file and shipped in a new app release BEFORE the old certificate
 * expires. The intermediate and root pins are long-lived and rarely change.
 * See `10_DEPLOYMENT_WORKFLOW.md §11` for the full rotation procedure.
 *
 * A [javax.net.ssl.SSLPeerUnverifiedException] from OkHttp indicates a pin mismatch.
 * [com.kitewatch.network.kiteconnect.adapter.ApiResultAdapterFactory] maps this to
 * [com.kitewatch.domain.error.AppError.NetworkError.CertificateMismatch].
 */
object KiteConnectCertificatePinner {
    private const val HOST = "api.kite.trade"

    // Leaf certificate: CN=*.kite.trade (issued by Sectigo)
    private const val PIN_LEAF = "sha256/HUdH3qmn8f23Xy4zTVMLvw3pkmjc1i1pUlnLQuIaOUY="

    // Intermediate CA: Sectigo RSA Domain Validation Secure Server CA
    private const val PIN_INTERMEDIATE = "sha256/4a6cPehI7OG6cuDZka5NDZ7FR8a60d3auda+sKfg4Ng="

    // Root CA: USERTrust RSA Certification Authority
    private const val PIN_ROOT = "sha256/x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td4="

    /**
     * Returns a [CertificatePinner] pinned to all three levels of the Kite Connect
     * certificate chain. OkHttp requires the server certificate's SPKI hash to match
     * at least one of the configured pins; including the intermediate and root provides
     * backup coverage during leaf certificate rotation.
     */
    fun build(): CertificatePinner =
        CertificatePinner
            .Builder()
            .add(HOST, PIN_LEAF, PIN_INTERMEDIATE, PIN_ROOT)
            .build()
}
