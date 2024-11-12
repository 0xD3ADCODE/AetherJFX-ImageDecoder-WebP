package javafx.iio.plugin.webp.chunks;

public class VP8LChunk extends BitstreamChunk {

    public static final byte[] VP8L = new byte[]{'V', 'P', '8', 'L'};

    public VP8LChunk(byte[] data) {
        super(VP8L, data);
    }

}
