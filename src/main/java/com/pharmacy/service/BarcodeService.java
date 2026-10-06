package com.pharmacy.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class BarcodeService {

    /** In-store EAN-13 prefix ("200"-"299" are reserved for internal use). */
    private static final String INTERNAL_PREFIX = "200";

    /** Builds a valid EAN-13 from a product id, e.g. id 15 -> 2000000000152. */
    public String generateEan13(long id) {
        String base = INTERNAL_PREFIX + String.format("%09d", id);
        return base + ean13CheckDigit(base);
    }

    public static int ean13CheckDigit(String twelveDigits) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = twelveDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static boolean isValidEan13(String code) {
        return code != null && code.matches("\\d{13}")
                && ean13CheckDigit(code.substring(0, 12)) == code.charAt(12) - '0';
    }

    /** PNG image of the barcode. Uses EAN-13 for valid EAN codes, Code 128 for everything else. */
    public byte[] png(String code, int width, int height) {
        BarcodeFormat format = isValidEan13(code) ? BarcodeFormat.EAN_13 : BarcodeFormat.CODE_128;
        return render(code, format, width, height);
    }

    public byte[] qrPng(String text, int size) {
        return render(text, BarcodeFormat.QR_CODE, size, size);
    }

    private byte[] render(String text, BarcodeFormat format, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.MARGIN, 2, EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new MultiFormatWriter().encode(text, format, width, height, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException | IllegalArgumentException e) {
            throw new BusinessException("error.barcode", text);
        }
    }
}
