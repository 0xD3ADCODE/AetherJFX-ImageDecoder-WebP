package javafx.iio.plugin.webp;

import javafx.iio.*;
import javafx.iio.plugin.webp.chunks.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class WebPLoader extends IIOLoader {
    private static final String FORMAT_NAME = "WebP";
    private static final List<String> EXTENSIONS = List.of("webp");
    private static final List<IIOSignature> SIGNATURES = List.of(new IIOSignature((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F'));

    private WebPChunk webpChunk;

    public static void register() {
        IIO.registerImageLoader(FORMAT_NAME, EXTENSIONS, SIGNATURES, EXTENSIONS, WebPLoader::new);
    }

    private WebPLoader(InputStream stream) {
        super(stream);
    }

    @Override
    public IIOImageFrame decode(int imageIndex, int rWidth, int rHeight, boolean preserveAspectRatio, boolean smooth) throws IOException {
        if (webpChunk == null) {
            webpChunk = WebPFactory.demux(super.stream);
        }

        PixelData pixelData = decodePixels(imageIndex);
        IIOImageFrame imageFrame = new IIOImageFrame(
                IIOImageType.RGBA,
                ByteBuffer.wrap(pixelData.pixels),
                pixelData.width, pixelData.height,
                pixelData.width * 4, null,
                new IIOImageMetadata(
                        null, null, null, null, null, pixelData.frameDuration, pixelData.loopCount,
                        pixelData.width, pixelData.height,
                        null, null, null
                )
        );

        int[] outWH = IIOImageTools.computeDimensions(imageFrame.getWidth(), imageFrame.getHeight(), rWidth, rHeight, preserveAspectRatio);
        rWidth = outWH[0];
        rHeight = outWH[1];

        return imageFrame.getWidth() != rWidth || imageFrame.getHeight() != rHeight
                ? IIOImageTools.scaleImageFrame(imageFrame, rWidth, rHeight, smooth)
                : imageFrame;
    }

    private PixelData decodePixels(int imageIndex) {
        int loopCount = webpChunk.subchunks()
                .stream()
                .filter(chunk -> chunk instanceof ANIMChunk)
                .map(ANIMChunk.class::cast)
                .mapToInt(ANIMChunk::getLoopCount)
                .findFirst()
                .orElse(0);

        List<Chunk> frameChunks = webpChunk.subchunks()
                .stream()
                .filter(chunk -> chunk instanceof ANMFChunk)
                .toList();

        if (!frameChunks.isEmpty()) {
            for (int i = 0; i < frameChunks.size(); i++) {
                if (i == imageIndex) {
                    ANMFChunk anmfChunk = (ANMFChunk) frameChunks.get(i);

                    ALPHChunk alphChunk = null;
                    VP8Chunk vp8Chunk = null;
                    int[] size = new int[2];

                    for (Chunk framesubchunk : anmfChunk.subchunks()) {
                        if (framesubchunk instanceof VP8LChunk bitstreamChunk) {
                            byte[] pixels = WebPFactory.decodeRGBA(bitstreamChunk, size);
                            return new PixelData(pixels, size[0], size[1], anmfChunk.getFrameDuration(), loopCount);
                        } else if (framesubchunk instanceof ALPHChunk chunk) {
                            alphChunk = chunk;
                        } else if (framesubchunk instanceof VP8Chunk chunk) {
                            vp8Chunk = chunk;
                        }
                    }

                    // Decoding of Alpha chunk with VP8 chunk
                    if (alphChunk != null && vp8Chunk != null) {
                        byte[] pixels = WebPFactory.decodeRGBA(alphChunk, vp8Chunk, size);
                        return new PixelData(pixels, size[0], size[1], anmfChunk.getFrameDuration(), loopCount);
                    }
                    // Decoding of VP8 chunk without Alpha chunk
                    else if (vp8Chunk != null) {
                        byte[] pixels = WebPFactory.decodeRGBA(vp8Chunk, size);
                        return new PixelData(pixels, size[0], size[1], anmfChunk.getFrameDuration(), loopCount);
                    }
                }
            }
        } else if (imageIndex == 0) {
//            ALPHChunk alphChunk = null;
            for (Chunk webpChunk : webpChunk.subchunks()) {
//                if (webpChunk instanceof ALPHChunk chunk) {
//                    alphChunk = chunk;
//                }

                // FIXME: decoding of VP8(L) + ALPH is not working as expected
                // Decoding VP8/VP8L with ALPH chunks
//                if (alphChunk != null && webpChunk instanceof VP8Chunk vp8Chunk) {
//                    int[] size = new int[2];
//                    byte[] pixels = WebPFactory.decodeRGBA(alphChunk, vp8Chunk, size);
//                    return new PixelData(pixels, size[0], size[1]);
//                }

                // Decoding VP8/VP8L chunk
                if (webpChunk instanceof BitstreamChunk bitstreamChunk) {
                    int[] size = new int[2];
                    byte[] pixels = WebPFactory.decodeRGBA(bitstreamChunk, size);
                    return new PixelData(pixels, size[0], size[1]);
                }
            }
        }

        // TODO: !!!
        return null;
    }

    private static class PixelData {
        byte[] pixels;
        int width;
        int height;
        Integer frameDuration;
        Integer loopCount;

        PixelData(byte[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }

        PixelData(byte[] pixels, int width, int height, int frameDuration, int loopCount) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.frameDuration = frameDuration;
            this.loopCount = loopCount;
        }
    }
}
