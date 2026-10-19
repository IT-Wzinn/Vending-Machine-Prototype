package com.vendingmachine.payment.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Service to generate NAPAS / VietQR EMVCo dynamic QR code strings.
 * Conforms to the EMVCo QR Code Specification for Payment Systems and NAPAS VietQR standard.
 */
@Service
public class VietQrService {

    private static final String NAPAS_GUID = "A000000727";
    private static final String SERVICE_CODE_TRANSFER = "QRIBFTTA";
    private static final String CURRENCY_VND = "704";
    private static final String COUNTRY_VN = "VN";

    /**
     * Generates a dynamic VietQR EMVCo string containing the beneficiary bank, account number,
     * amount and transfer reference.
     */
    public String generateVietQr(String bankBin, String accountNumber, int amountVnd, String transferReference) {
        if (bankBin == null || bankBin.isBlank() || accountNumber == null || accountNumber.isBlank()) {
            // Fallback for demo without valid bank credentials
            return String.format("DEMO_VIETQR:BIN=%s:ACC=%s:AMOUNT=%d:REF=%s",
                    bankBin, accountNumber, amountVnd, transferReference);
        }

        // Tag 38: Merchant Account Information
        String sub00 = tlv("00", NAPAS_GUID);
        String ben00 = tlv("00", bankBin);
        String ben01 = tlv("01", accountNumber);
        String sub01 = tlv("01", ben00 + ben01);
        String sub02 = tlv("02", SERVICE_CODE_TRANSFER);
        String tag38 = tlv("38", sub00 + sub01 + sub02);

        // Tag 62: Additional Data Field (Transfer Reference)
        String tag62 = "";
        if (transferReference != null && !transferReference.isBlank()) {
            String sub08 = tlv("08", transferReference);
            tag62 = tlv("62", sub08);
        }

        // Build Payload without CRC
        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01")); // Payload format indicator
        sb.append(tlv("01", "12")); // Point of initiation method: 12 = Dynamic (with amount)
        sb.append(tag38);
        sb.append(tlv("53", CURRENCY_VND));
        if (amountVnd > 0) {
            sb.append(tlv("54", String.valueOf(amountVnd)));
        }
        sb.append(tlv("58", COUNTRY_VN));
        sb.append(tag62);
        sb.append("6304"); // Tag 63 with length 04

        // Compute CRC-16 CCITT
        int crc = calculateCrc16Ccitt(sb.toString().getBytes(StandardCharsets.US_ASCII));
        String crcHex = String.format("%04X", crc);

        return sb.toString() + crcHex;
    }

    /**
     * Builds SePay Quick Link URL for alternative browser QR rendering.
     */
    public String generateSepayQrUrl(String bankName, String accountNumber, int amountVnd, String transferReference) {
        return String.format("https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%d&des=%s",
                accountNumber != null ? accountNumber : "",
                bankName != null ? bankName : "",
                amountVnd,
                transferReference != null ? transferReference : "");
    }

    private static String tlv(String tag, String value) {
        if (value == null) {
            value = "";
        }
        return tag + String.format("%02d", value.length()) + value;
    }

    /**
     * Computes CRC-16 CCITT (Polynomial 0x1021, Init 0xFFFF).
     */
    public static int calculateCrc16Ccitt(byte[] bytes) {
        int crc = 0xFFFF;
        for (byte b : bytes) {
            crc ^= ((b & 0xFF) << 8);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        return crc;
    }
}
