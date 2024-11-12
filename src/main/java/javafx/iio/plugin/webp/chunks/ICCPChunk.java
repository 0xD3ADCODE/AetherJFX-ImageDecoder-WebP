package javafx.iio.plugin.webp.chunks;

public class ICCPChunk extends RawDataChunk {

    public static final byte[] ICCP = new byte[]{'I', 'C', 'C', 'P'};

    public ICCPChunk(byte[] data) {
        super(ICCP, data);
    }

}
