package javafx.iio.plugin.webp.chunks;

public class XMPChunk extends RawDataChunk {

    public static final byte[] XMP = new byte[]{'X', 'M', 'P', ' '};

    public XMPChunk(byte[] data) {
        super(XMP, data);
    }

}
