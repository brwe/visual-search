/*
 * Copyright 2017 a2tirb
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package visualsearch.image;


import com.sun.imageio.plugins.jpeg.JPEGImageReader;
import com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi;

import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ProcessImage {
    public static ProcessedImage getProcessingResult(ByteBuffer b, ProcessedImage.Builder builder) throws IOException {
        BufferedImage img = byteBufferToBufferedImage(b);
        builder.capacity(b.capacity()).build();
        builder.numPixels(img.getWidth() * img.getHeight());
        builder.dHash(dHash(img));
        return builder.build();
    }

    public static boolean[] dHash(BufferedImage img) {
        return pixelDiffsLeftToRight(shrinkImage(img));
    }

    public static BufferedImage byteBufferToBufferedImage(ByteBuffer b) throws IOException {
        JPEGImageReaderSpi spi = new JPEGImageReaderSpi();
        JPEGImageReader imageReader = new JPEGImageReader(spi);
        MemoryCacheImageInputStream memoryCacheImageInputStream = new MemoryCacheImageInputStream(new ByteArrayInputStream(b.array()));
        imageReader.setInput(memoryCacheImageInputStream);
        return imageReader.read(0);
    }


    public static boolean[] pixelDiffsLeftToRight(BufferedImage outputImage) {
        boolean[] result = new boolean[64];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int current = outputImage.getRaster().getSample(i, j, 0);
                int next = outputImage.getRaster().getSample(i, j + 1, 0);
                result[i * 8 + j] = current < next;
            }
        }
        return result;
    }

    public static BufferedImage shrinkImage(BufferedImage inputImage) {
        BufferedImage outputImage = new BufferedImage(8,
                9, BufferedImage.TYPE_BYTE_GRAY);

        // scales the input image to the output image
        Graphics2D g2d = outputImage.createGraphics();
        g2d.drawImage(inputImage, 0, 0, 8, 9, null);
        g2d.dispose();
        return outputImage;
    }
}
