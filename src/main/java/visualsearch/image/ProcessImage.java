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
import org.apache.commons.codec.binary.Base64;

import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ProcessImage {
    public static ProcessedImage getProcessingResult(ByteBuffer b, ProcessedImage.Builder builder) throws IOException {
        assert Base64.isBase64(b.array()) == false;
        JPEGImageReaderSpi spi = new JPEGImageReaderSpi();
        JPEGImageReader imageReader = new JPEGImageReader(spi);
        MemoryCacheImageInputStream memoryCacheImageInputStream = new MemoryCacheImageInputStream(new ByteArrayInputStream(b.array()));
        imageReader.setInput(memoryCacheImageInputStream);
        BufferedImage img = imageReader.read(0);
        builder.capacity(b.capacity()).build();
        builder.numPixels(img.getWidth() * img.getHeight());
        return builder.build();
    }

}
