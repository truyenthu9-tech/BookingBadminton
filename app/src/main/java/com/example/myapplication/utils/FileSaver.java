package com.example.myapplication.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Lưu file vào thư mục Downloads (không cần xin quyền). */
public final class FileSaver {
    private FileSaver() {}

    /** @return đường dẫn để hiển thị cho người dùng. */
    public static String saveToDownloads(Context c, String name, String mime, byte[] data) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Không tạo được file");
            try (OutputStream os = c.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("Không mở được file");
                os.write(data);
            }
            return "Downloads/" + name;
        }
        File dir = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) throw new IOException("Không có bộ nhớ ngoài");
        File f = new File(dir, name);
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(data);
        }
        return f.getAbsolutePath();
    }
}
