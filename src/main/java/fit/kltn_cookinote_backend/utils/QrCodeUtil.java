/*
 * @ (#) QrCodeUtil.java    1.0    28/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.utils;/*
 * @description:
 * @author: Bao Thong
 * @date: 28/10/2025
 * @version: 1.0
 */

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class QrCodeUtil {

    /**
     * Tạo mã QR từ text và trả về dưới dạng Base64 String.
     * @param text Nội dung cần mã hóa (ví dụ: URL chia sẻ)
     * @param width Chiều rộng ảnh QR
     * @param height Chiều cao ảnh QR
     * @return Base64 encoded PNG image string, hoặc null nếu lỗi
     */
    public static String generateQrCodeBase64(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L); // Mức sửa lỗi
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1); // Viền

            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
        } catch (WriterException | IOException e) {
            // Log lỗi ở đây nếu cần
            System.err.println("Không thể tạo mã QR: " + e.getMessage());
            return null;
        }
    }
}