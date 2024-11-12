package javafx.iio.plugin.webp.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public interface WebP extends Library {
    WebP INSTANCE = WebPLibrary.create();

    void WebPFree(Pointer ptr);

    long WebPEncodeRGB(byte[] rgb, int width, int height, int stride,
                       float quality_factor, PointerByReference output);

    long WebPEncodeBGR(byte[] bgr, int width, int height, int stride,
                       float quality_factor, PointerByReference output);

    long WebPEncodeRGBA(byte[] rgba, int width, int height, int stride,
                        float quality_factor, PointerByReference output);

    long WebPEncodeBGRA(int[] bgra, int width, int height, int stride,
                        float quality_factor, PointerByReference output);

    long WebPEncodeLosslessRGB(byte[] rgb, int width, int height,
                               int stride, PointerByReference output);

    long WebPEncodeLosslessBGR(byte[] bgr, int width, int height,
                               int stride, PointerByReference output);

    long WebPEncodeLosslessRGBA(byte[] rgba, int width, int height,
                                int stride, PointerByReference output);

    long WebPEncodeLosslessBGRA(int[] bgra, int width, int height,
                                int stride, PointerByReference output);

    Pointer WebPDecodeRGBA(byte[] data, long data_size,
                           IntByReference width, IntByReference height);

    Pointer WebPDecodeARGB(byte[] data, long data_size,
                           IntByReference width, IntByReference height);

    Pointer WebPDecodeBGRA(byte[] data, long data_size,
                           IntByReference width, IntByReference height);

    Pointer WebPDecodeRGB(byte[] data, long data_size,
                          IntByReference width, IntByReference height);

    Pointer WebPDecodeBGR(byte[] data, long data_size,
                          IntByReference width, IntByReference height);
}

class WebPLibrary {

    static WebP create() {
        try {
            loadJarDll("/win32-x86-64/libsharpyuv.dll");
            loadJarDll("/win32-x86-64/libwebpdecoder.dll");
        } catch (Exception ignored) {
            System.loadLibrary("libsharpyuv");
            System.loadLibrary("libwebpdecoder");
        }

        NativeLibrary JNA_NATIVE_LIB = NativeLibrary.getInstance("libwebp");
        return Native.load("libwebp", WebP.class);
    }

    static void loadJarDll(String name) throws IOException {
        InputStream in = WebPLibrary.class.getResourceAsStream(name);
        byte[] buffer = new byte[1024];
        int read = -1;

        File temp = new File(new File(System.getProperty("java.io.tmpdir"), "AetherJFXWebP"), name);
        if (!temp.exists()) {
            temp.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(temp);

            while ((read = in.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.close();
        }
        in.close();

        System.load(temp.getAbsolutePath());
    }
}