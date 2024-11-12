package javafx.iio.plugin.webp.chunks;

import java.io.IOException;
import java.io.OutputStream;

import static javafx.iio.plugin.webp.utils.WebPUtils.writeFourCC;

public class RIFFChunk extends LISTChunk {

    public static final byte[] RIFF = new byte[]{'R', 'I', 'F', 'F'};

    private final byte[] fourCC;

    public RIFFChunk(byte[] fourCC, Chunk... subchunks) {
        super(RIFF, 4, subchunks);
        this.fourCC = fourCC;
    }

    public byte[] getTag() {
        return fourCC.clone();
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        writeFourCC(out, fourCC);
        writeSubchunks(out);
    }

}
