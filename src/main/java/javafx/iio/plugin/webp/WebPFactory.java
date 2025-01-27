package javafx.iio.plugin.webp;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import javafx.iio.plugin.webp.chunks.*;
import javafx.iio.plugin.webp.jna.WebP;
import javafx.iio.plugin.webp.utils.WebPUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static javafx.iio.plugin.webp.chunks.ALPHChunk.ALPH;
import static javafx.iio.plugin.webp.chunks.ANIMChunk.ANIM;
import static javafx.iio.plugin.webp.chunks.ANMFChunk.ANMF;
import static javafx.iio.plugin.webp.chunks.EXIFChunk.EXIF;
import static javafx.iio.plugin.webp.chunks.ICCPChunk.ICCP;
import static javafx.iio.plugin.webp.chunks.VP8Chunk.VP8;
import static javafx.iio.plugin.webp.chunks.VP8LChunk.VP8L;
import static javafx.iio.plugin.webp.chunks.VP8XChunk.VP8X;
import static javafx.iio.plugin.webp.chunks.WebPChunk.RIFF;
import static javafx.iio.plugin.webp.chunks.WebPChunk.WEBP;
import static javafx.iio.plugin.webp.chunks.XMPChunk.XMP;
import static javafx.iio.plugin.webp.utils.WebPUtils.*;

final class WebPFactory {

    private WebPFactory() {
        throw new UnsupportedOperationException();
    }

    public static WebPChunk demux(InputStream in) throws IOException {
        Objects.requireNonNull(in);
        byte[] riffChunkHeader = readFourCC(in);
        if (!arrayEquals(riffChunkHeader, RIFF)) {
            throw new IOException("Illegal magic number: " + new String(riffChunkHeader));
        }
        long fileSize = readUInt32(in);
        byte[] webpChunkHeader = readFourCC(in);
        if (!arrayEquals(webpChunkHeader, WEBP)) {
            throw new IOException("Illegal magic number: " + new String(riffChunkHeader) + new String(webpChunkHeader));
        }
        WebPChunk webPChunk;
        boolean filePad = false;
        if (isOdd(fileSize)) {
            fileSize -= 1;
            filePad = true;
        }
        fileSize -= 4;
        byte[] vp8ChunkHeader = readFourCC(in);
        long vp8ChunkSize = readUInt32(in);
        fileSize -= 8;
        fileSize -= vp8ChunkSize;
        boolean vp8ChunkPad = false;
        if (isOdd(vp8ChunkSize)) {
            fileSize -= 1;
            vp8ChunkPad = true;
        }
        if (arrayEquals(vp8ChunkHeader, VP8X)) {
            List<Chunk> chunks = new ArrayList<>();
            chunks.add(new VP8XChunk(readInt32(in), read1Based(in), read1Based(in)));
            if (vp8ChunkPad) skip1Byte(in);
            byte[] chunkHeader;
            long chunkSize;
            boolean chunkPad = false;
            while (fileSize > 0) {
                chunkHeader = readFourCC(in);
                chunkSize = readUInt32(in);
                fileSize -= 8;
                fileSize -= chunkSize;
                if (isOdd(chunkSize)) {
                    fileSize -= 1;
                    chunkPad = true;
                }
                if (chunkSize > Integer.MAX_VALUE) throw new IOException("chunk too large to read");
                if (arrayEquals(chunkHeader, ICCP)) {
                    chunks.add(new ICCPChunk(readNBytes(in, (int) chunkSize)));
                } else if (arrayEquals(chunkHeader, EXIF)) {
                    chunks.add(new EXIFChunk(readNBytes(in, (int) chunkSize)));
                } else if (arrayEquals(chunkHeader, XMP)) {
                    chunks.add(new XMPChunk(readNBytes(in, (int) chunkSize)));
                } else if (arrayEquals(chunkHeader, VP8)) {
                    chunks.add(new VP8Chunk(readNBytes(in, (int) chunkSize)));
                } else if (arrayEquals(chunkHeader, VP8L)) {
                    chunks.add(new VP8LChunk(readNBytes(in, (int) chunkSize)));
                } else if (arrayEquals(chunkHeader, ALPH)) {
                    chunks.add(new ALPHChunk(readNBytes(in, (int) chunkSize)));
                } else if (arrayEquals(chunkHeader, ANIM)) {
                    chunks.add(new ANIMChunk(readInt32(in), readUInt16(in)));
                } else if (arrayEquals(chunkHeader, ANMF)) {
                    int x = readUInt24(in);
                    int y = readUInt24(in);
                    int width = read1Based(in);
                    int height = read1Based(in);
                    int duration = readUInt24(in);
                    int reservedBD = readInt8(in);
                    chunkSize -= 16;
                    List<Chunk> framesubchunks = new ArrayList<>();
                    byte[] framesubchunkHeader;
                    long framesubchunkSize;
                    boolean framesubchunkPad = false;
                    while (chunkSize > 0) {
                        framesubchunkHeader = readFourCC(in);
                        framesubchunkSize = readUInt32(in);
                        if (isOdd(framesubchunkSize)) {
                            chunkSize -= 1;
                            framesubchunkPad = true;
                        }
                        chunkSize -= 8;
                        chunkSize -= framesubchunkSize;
                        if (framesubchunkSize > Integer.MAX_VALUE) throw new IOException("chunk too large to read");
                        if (arrayEquals(framesubchunkHeader, VP8)) {
                            framesubchunks.add(new VP8Chunk(readNBytes(in, (int) framesubchunkSize)));
                        } else if (arrayEquals(framesubchunkHeader, VP8L)) {
                            framesubchunks.add(new VP8LChunk(readNBytes(in, (int) framesubchunkSize)));
                        } else if (arrayEquals(framesubchunkHeader, ALPH)) {
                            framesubchunks.add(new ALPHChunk(readNBytes(in, (int) framesubchunkSize)));
                        } else {
                            try {
                                framesubchunks.add(new UnknownChunk(framesubchunkHeader, readNBytes(in, (int) framesubchunkSize)));
                            } catch (IOException ignored) {
                                // Some VP8X images with ALPH + VP8 and unknown chunks may produce EOFException on read
                            }
                        }
                    }
                    chunks.add(new ANMFChunk(x, y, width, height, duration, reservedBD, framesubchunks.toArray(new Chunk[0])));
                    if (framesubchunkPad) skip1Byte(in);
                } else {
                    try {
                        chunks.add(new UnknownChunk(chunkHeader, readNBytes(in, (int) chunkSize)));
                    } catch (IOException ignored) {
                        // Some VP8X images with unknown chunks may produce EOFException on read
                    }
                }
                if (chunkPad) skip1ByteSilently(in);
            }
            webPChunk = new WebPChunk(chunks.toArray(new Chunk[0]));
        } else if (arrayEquals(vp8ChunkHeader, VP8)) {
            if (vp8ChunkSize > Integer.MAX_VALUE) throw new IOException("chunk too large to read");
            webPChunk = new WebPChunk(new VP8Chunk(readNBytes(in, (int) vp8ChunkSize)));
        } else if (arrayEquals(vp8ChunkHeader, VP8L)) {
            if (vp8ChunkSize > Integer.MAX_VALUE) throw new IOException("chunk too large to read");
            webPChunk = new WebPChunk(new VP8LChunk(readNBytes(in, (int) vp8ChunkSize)));
        } else {
            throw new IOException("No VP8 data found");
        }
        if (filePad) skip1Byte(in);
        in.close();
        return webPChunk;
    }

    public static byte[] decodeRGBA(BitstreamChunk chunk, int[] size) {
        Objects.requireNonNull(chunk);
        WebP webP = WebP.INSTANCE;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        Pointer webPData = webP.WebPDecodeRGBA(chunk.getRawData(), chunk.getSize(), width, height);
        size[0] = width.getValue();
        size[1] = height.getValue();
        byte[] buf = webPData.getByteArray(0, size[0] * size[1] * 4);
        webP.WebPFree(webPData);
        return buf;
    }

    public static byte[] decodeARGB(BitstreamChunk chunk, int[] size) {
        Objects.requireNonNull(chunk);
        WebP webP = WebP.INSTANCE;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        Pointer webPData = webP.WebPDecodeARGB(chunk.getRawData(), chunk.getSize(), width, height);
        size[0] = width.getValue();
        size[1] = height.getValue();
        byte[] buf = webPData.getByteArray(0, size[0] * size[1] * 4);
        webP.WebPFree(webPData);
        return buf;
    }

    public static int[] decodeBGRA(BitstreamChunk chunk, int[] size) {
        Objects.requireNonNull(chunk);
        if (size == null || size.length != 2) throw new IllegalArgumentException("size length must be 2");
        WebP webP = WebP.INSTANCE;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        Pointer webPData = webP.WebPDecodeBGRA(chunk.getRawData(), chunk.getSize(), width, height);
        size[0] = width.getValue();
        size[1] = height.getValue();
        int[] buf = webPData.getIntArray(0, size[0] * size[1]);
        webP.WebPFree(webPData);
        return buf;
    }

    public static byte[] decodeRGBA(ALPHChunk alphChunk, VP8Chunk vp8Chunk, int[] size) {
        Objects.requireNonNull(alphChunk);
        Objects.requireNonNull(vp8Chunk);
        if (size == null || size.length != 2) throw new IllegalArgumentException("size length must be 2");
        long chunkFullSize = alphChunk.getFullSize() + vp8Chunk.getFullSize();
        if (chunkFullSize > Integer.MAX_VALUE) throw new IllegalArgumentException("chunk too large to read");
        WebP webP = WebP.INSTANCE;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        byte[] chunkData = new byte[(int) chunkFullSize];
        System.arraycopy(alphChunk.getRawData(), 0, chunkData, 0, (int) alphChunk.getFullSize());
        System.arraycopy(vp8Chunk.getRawData(), 0, chunkData, (int) alphChunk.getFullSize(), (int) vp8Chunk.getFullSize());
        Pointer webPData = webP.WebPDecodeRGBA(chunkData, chunkFullSize, width, height);
        size[0] = width.getValue();
        size[1] = height.getValue();
        byte[] buf = webPData.getByteArray(0, size[0] * size[1] * 4);
        webP.WebPFree(webPData);
        return buf;
    }

    public static byte[] decodeARGB(ALPHChunk alphChunk, VP8Chunk vp8Chunk, int[] size) {
        Objects.requireNonNull(alphChunk);
        Objects.requireNonNull(vp8Chunk);
        if (size == null || size.length != 2) throw new IllegalArgumentException("size length must be 2");
        long chunkFullSize = alphChunk.getFullSize() + vp8Chunk.getFullSize();
        if (chunkFullSize > Integer.MAX_VALUE) throw new IllegalArgumentException("chunk too large to read");
        WebP webP = WebP.INSTANCE;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        byte[] chunkData = new byte[(int) chunkFullSize];
        System.arraycopy(alphChunk.getRawData(), 0, chunkData, 0, (int) alphChunk.getFullSize());
        System.arraycopy(vp8Chunk.getRawData(), 0, chunkData, (int) alphChunk.getFullSize(), (int) vp8Chunk.getFullSize());
        Pointer webPData = webP.WebPDecodeBGRA(chunkData, chunkFullSize, width, height);
        size[0] = width.getValue();
        size[1] = height.getValue();
        byte[] buf = webPData.getByteArray(0, size[0] * size[1] * 4);
        webP.WebPFree(webPData);
        return buf;
    }

    public static int[] decodeBGRA(ALPHChunk alphChunk, VP8Chunk vp8Chunk, int[] size) {
        Objects.requireNonNull(alphChunk);
        Objects.requireNonNull(vp8Chunk);
        if (size == null || size.length != 2) throw new IllegalArgumentException("size length must be 2");
        long chunkFullSize = alphChunk.getFullSize() + vp8Chunk.getFullSize();
        if (chunkFullSize > Integer.MAX_VALUE) throw new IllegalArgumentException("chunk too large to read");
        WebP webP = WebP.INSTANCE;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        byte[] chunkData = new byte[(int) chunkFullSize];
        System.arraycopy(alphChunk.getRawData(), 0, chunkData, 0, (int) alphChunk.getFullSize());
        System.arraycopy(vp8Chunk.getRawData(), 0, chunkData, (int) alphChunk.getFullSize(), (int) vp8Chunk.getFullSize());
        Pointer webPData = webP.WebPDecodeBGRA(chunkData, chunkFullSize, width, height);
        size[0] = width.getValue();
        size[1] = height.getValue();
        int[] buf = webPData.getIntArray(0, size[0] * size[1]);
        webP.WebPFree(webPData);
        return buf;
    }

    public static byte[] decodeRGB(BitstreamChunk chunk, int[] size) {
        Objects.requireNonNull(chunk);
        if (size == null || size.length != 2) throw new IllegalArgumentException("size length must be 2");
        WebP webP = WebP.INSTANCE;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        Pointer webPData = webP.WebPDecodeRGB(chunk.getRawData(), chunk.getSize(), width, height);
        size[0] = width.getValue();
        size[1] = height.getValue();
        byte[] buf = webPData.getByteArray(0, width.getValue() * height.getValue() * 3);
        webP.WebPFree(webPData);
        return buf;
    }

    public static byte[] decodeBGR(BitstreamChunk chunk, int[] size) {
        Objects.requireNonNull(chunk);
        if (size == null || size.length != 2) throw new IllegalArgumentException("size length must be 2");
        WebP webP = WebP.INSTANCE;
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        Pointer webPData = webP.WebPDecodeBGR(chunk.getRawData(), chunk.getSize(), width, height);
        size[0] = width.getValue();
        size[1] = height.getValue();
        byte[] buf = webPData.getByteArray(0, width.getValue() * height.getValue() * 3);
        webP.WebPFree(webPData);
        return buf;
    }

    public static VP8LChunk encodeLosslessBGRA(int[] bgra, int width, int height, int stride) {
        Objects.requireNonNull(bgra);
        PointerByReference webPDataRef = new PointerByReference();
        WebP webP = WebP.INSTANCE;
        webP.WebPEncodeLosslessBGRA(bgra, width, height, stride * 4, webPDataRef);
        return getVP8LChunk(webPDataRef);
    }

    private static VP8LChunk getVP8LChunk(PointerByReference webPDataRef) {
        WebP webP = WebP.INSTANCE;
        Pointer webPData = webPDataRef.getValue();
        byte[] lengthBuf = webPData.getByteArray(16, 4);
        long length = WebPUtils.toUInt32(lengthBuf);
        byte[] buf = webPData.getByteArray(20, (int) length);
        webP.WebPFree(webPData);
        return new VP8LChunk(buf);
    }

    public static VP8LChunk encodeLosslessRGBA(byte[] rgba, int width, int height, int stride) {
        Objects.requireNonNull(rgba);
        PointerByReference webPDataRef = new PointerByReference();
        WebP webP = WebP.INSTANCE;
        webP.WebPEncodeLosslessRGBA(rgba, width, height, stride * 4, webPDataRef);
        return getVP8LChunk(webPDataRef);
    }

    public static VP8LChunk encodeLosslessRGB(byte[] rgb, int width, int height, int stride) {
        Objects.requireNonNull(rgb);
        PointerByReference webPDataRef = new PointerByReference();
        WebP webP = WebP.INSTANCE;
        webP.WebPEncodeLosslessRGB(rgb, width, height, stride * 3, webPDataRef);
        return getVP8LChunk(webPDataRef);
    }

    public static VP8LChunk encodeLosslessBGR(byte[] bgr, int width, int height, int stride) {
        Objects.requireNonNull(bgr);
        PointerByReference webPDataRef = new PointerByReference();
        WebP webP = WebP.INSTANCE;
        webP.WebPEncodeLosslessBGR(bgr, width, height, stride * 3, webPDataRef);
        return getVP8LChunk(webPDataRef);
    }

    public static Chunk[] encodeBGRA(int[] bgra, int width, int height, int stride, float quality) {
        Objects.requireNonNull(bgra);
        PointerByReference webPDataRef = new PointerByReference();
        WebP webP = WebP.INSTANCE;
        webP.WebPEncodeBGRA(bgra, width, height, stride * 4, quality, webPDataRef);
        return getLossyChunks(webPDataRef);
    }

    public static Chunk[] encodeRGBA(byte[] rgba, int width, int height, int stride, float quality) {
        Objects.requireNonNull(rgba);
        PointerByReference webPDataRef = new PointerByReference();
        WebP webP = WebP.INSTANCE;
        webP.WebPEncodeRGBA(rgba, width, height, stride * 4, quality, webPDataRef);
        return getLossyChunks(webPDataRef);
    }

    private static Chunk[] getLossyChunks(PointerByReference webPDataRef) {
        WebP webP = WebP.INSTANCE;
        Pointer webPData = webPDataRef.getValue();
        byte[] chunkHeaderBuf = webPData.getByteArray(12, 4);
        if (arrayEquals(chunkHeaderBuf, VP8)) {
            return new Chunk[]{getVP8Chunk(webPDataRef)};
        } else {
            byte[] lengthBuf = webPData.getByteArray(34, 4);
            long lengthALPH = WebPUtils.toUInt32(lengthBuf);
            int pad = isOdd(lengthALPH) ? 1 : 0;
            byte[] bufALPH = webPData.getByteArray(38, (int) lengthALPH);
            byte[] lengthBuf2 = webPData.getByteArray(42 + bufALPH.length + pad, 4);
            long lengthVP8 = WebPUtils.toUInt32(lengthBuf2);
            byte[] bufVP8 = webPData.getByteArray(46 + bufALPH.length + pad, (int) lengthVP8);
            webP.WebPFree(webPData);
            return new Chunk[]{new ALPHChunk(bufALPH), new VP8Chunk(bufVP8)};
        }
    }

    public static VP8Chunk encodeBGR(byte[] bgr, int width, int height, int stride, float quality) {
        Objects.requireNonNull(bgr);
        PointerByReference webPDataRef = new PointerByReference();
        WebP webP = WebP.INSTANCE;
        webP.WebPEncodeBGR(bgr, width, height, stride * 3, quality, webPDataRef);
        return getVP8Chunk(webPDataRef);
    }

    public static VP8Chunk encodeRGB(byte[] rgb, int width, int height, int stride, float quality) {
        Objects.requireNonNull(rgb);
        PointerByReference webPDataRef = new PointerByReference();
        WebP webP = WebP.INSTANCE;
        webP.WebPEncodeRGB(rgb, width, height, stride * 3, quality, webPDataRef);
        return getVP8Chunk(webPDataRef);
    }

    private static VP8Chunk getVP8Chunk(PointerByReference webPDataRef) {
        WebP webP = WebP.INSTANCE;
        Pointer webPData = webPDataRef.getValue();
        byte[] lengthBuf = webPData.getByteArray(16, 4);
        long length = WebPUtils.toUInt32(lengthBuf);
        byte[] buf = webPData.getByteArray(20, (int) length);
        webP.WebPFree(webPData);
        return new VP8Chunk(buf);
    }

}
