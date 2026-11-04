package com.example.myapplication.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.example.myapplication.data.model.Booking;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

public class QRCodeHelper {

    private QRCodeHelper() {
    }

    /**
     * Sinh Bitmap mã QR từ chuỗi nội dung.
     *
     * @param content nội dung cần mã hóa
     * @param sizePx  cạnh ảnh (pixel)
     * @return Bitmap hoặc null nếu lỗi
     */
    public static Bitmap generate(String content, int sizePx) {
        if (content == null || content.isEmpty() || sizePx <= 0) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }

    /** Sinh QR cho vé: ưu tiên qrCode từ server, nếu không có thì dùng mã đặt sân. */
    public static Bitmap generateForBooking(Booking booking, int sizePx) {
        if (booking == null) return null;
        String content = booking.getQrCode();
        if (content == null || content.isEmpty()) {
            content = booking.getBookingCode();
        }
        return generate(content, sizePx);
    }
}
