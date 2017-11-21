package visualsearch.image;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class ImageProcessTest {

    @Test
    public void testImageOther() throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(new File("src/test/resources/nginx/data/test.jpg"))) {
            byte[] imageBytes = IOUtils.toByteArray(fileInputStream);
            ByteBuffer byteBuffer = ByteBuffer.wrap(imageBytes);
            ProcessedImage processedImage = ProcessImage.getProcessingResult(byteBuffer, ProcessedImage.builder().imageUrl(""));
            assertThat(processedImage.receivedBytes, equalTo(11389));
        }
    }
}
