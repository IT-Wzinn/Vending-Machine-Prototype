package com.vendingmachine.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietQrServiceTest {

    private VietQrService vietQrService;

    @BeforeEach
    void setUp() {
        vietQrService = new VietQrService();
    }

    @Test
    @DisplayName("Tạo chuỗi VietQR EMVCo hợp lệ với đầy đủ Tag 00, 01, 38, 53, 54, 58, 62 và CRC 63")
    void generateVietQr_Success() {
        String qr = vietQrService.generateVietQr("970422", "0345678999", 15000, "VM001AB3F");

        assertThat(qr).isNotNull();
        // Tag 00: 01 (EMVCo specification version)
        assertThat(qr).startsWith("000201010212");
        // Tag 38 contains NAPAS GUID A000000727 and MBBank BIN 970422
        assertThat(qr).contains("A000000727");
        assertThat(qr).contains("970422");
        assertThat(qr).contains("0345678999");
        assertThat(qr).contains("QRIBFTTA");
        // Tag 53: VND (704)
        assertThat(qr).contains("5303704");
        // Tag 54: Amount 15000
        assertThat(qr).contains("540515000");
        // Tag 58: Country VN
        assertThat(qr).contains("5802VN");
        // Tag 62: Transfer Reference VM001AB3F
        assertThat(qr).contains("VM001AB3F");
        // Tag 63: CRC16 4 hex chars at the end
        assertThat(qr).contains("6304");
        assertThat(qr.length()).isGreaterThan(70);
    }

    @Test
    @DisplayName("Fallback khi thiếu bank BIN hoặc số tài khoản")
    void generateVietQr_FallbackWhenMissingCredentials() {
        String qr = vietQrService.generateVietQr("", "", 10000, "VM001REF");
        assertThat(qr).startsWith("DEMO_VIETQR:");
    }

    @Test
    @DisplayName("Tạo SePay Quick Link URL chính xác")
    void generateSepayQrUrl_Success() {
        String url = vietQrService.generateSepayQrUrl("MBBank", "0345678999", 15000, "VM001AB3F");
        assertThat(url).isEqualTo("https://qr.sepay.vn/img?acc=0345678999&bank=MBBank&amount=15000&des=VM001AB3F");
    }

    @Test
    @DisplayName("Tính toán CRC16-CCITT chính xác")
    void calculateCrc16Ccitt_Correctness() {
        String data = "123456789";
        int crc = VietQrService.calculateCrc16Ccitt(data.getBytes());
        // CRC16-CCITT for "123456789" with polynomial 0x1021 and init 0xFFFF is 0x29B1
        assertThat(String.format("%04X", crc)).isEqualTo("29B1");
    }
}
