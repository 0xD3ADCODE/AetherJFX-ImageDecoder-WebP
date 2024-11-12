package javafx.iio.plugin.webp.chunks;

import java.io.IOException;
import java.io.OutputStream;

import static javafx.iio.plugin.webp.utils.WebPUtils.*;

public class ANIMChunk extends Chunk {

    public static final byte[] ANIM = new byte[]{'A', 'N', 'I', 'M'};

    private final int backgroundColor;
    private final int loopCount;

    public ANIMChunk(int backgroundColor, int loopCount) {
        super(ANIM, 4 + 2);
        this.backgroundColor = backgroundColor;
        this.loopCount = checkUInt16(loopCount);
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getLoopCount() {
        return loopCount;
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        writeInt32(out, backgroundColor);
        writeUInt16(out, loopCount);
    }

}
