package com.example.myapplication.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.appcompat.widget.AppCompatImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * ImageView tự sinh mã QR (cần thư viện com.google.zxing:core).
 * layout activity_ticket_detail dùng ImageView thường nên Activity gọi QRCodeView.bind(...).
 */
public class QRCodeView extends AppCompatImageView {
    public QRCodeView(Context context) { super(context); }
    public QRCodeView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setContent(String content) { bind(this, content); }

    public static void bind(ImageView target, String content) {
        if (content == null || content.isEmpty()) {
            target.setImageDrawable(null);
            return;
        }
        int size = 720; // px, ImageView sẽ tự scale về 240dp
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    px[y * size + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, size, 0, 0, size, size);
            target.setImageBitmap(bmp);
        } catch (WriterException e) {
            target.setImageDrawable(null);
        }
    }
}
