package javafx.iio.plugin.webp.chunks;

public class VP8Chunk extends BitstreamChunk {

    public static final byte[] VP8 = new byte[]{'V', 'P', '8', ' '};

    public VP8Chunk(byte[] data) {
        super(VP8, data);
    }

}
