package javafx.iio.plugin.webp.chunks;

public class WebPChunk extends RIFFChunk {

    public static final byte[] WEBP = new byte[]{'W', 'E', 'B', 'P'};

    public WebPChunk(Chunk... subchunks) {
        super(WEBP, subchunks);
    }

}
