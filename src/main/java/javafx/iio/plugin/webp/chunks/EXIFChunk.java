package javafx.iio.plugin.webp.chunks;

public class EXIFChunk extends RawDataChunk {

    public static final byte[] EXIF = new byte[]{'E', 'X', 'I', 'F'};

    public EXIFChunk(byte[] data) {
        super(EXIF, data);
    }

}
