package visualsearch.image;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import reactor.util.Logger;
import reactor.util.Loggers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class ImageProcessTest {
    Logger logger = Loggers.getLogger(this.getClass());

    @Test
    public void testActualImage() throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(new File("src/test/resources/nginx/data/test.jpg"))) {
            byte[] imageBytes = IOUtils.toByteArray(fileInputStream);
            ByteBuffer byteBuffer = ByteBuffer.wrap(imageBytes);
            ProcessedImage processedImage = ProcessImage.getProcessingResult(byteBuffer, ProcessedImage.builder().imageUrl(""));
            assertThat(processedImage.receivedBytes, equalTo(11389));
        }
    }

    @Test
    public void testShrinkImage8x9() throws IOException {

        BufferedImage testImage = new BufferedImage(8, 9, TYPE_INT_RGB);
        Graphics2D testGraphics2d = testImage.createGraphics();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 9; j++) {
                testGraphics2d.setColor(RandomUtils.nextBoolean() ? Color.BLACK : Color.WHITE);
                testGraphics2d.fill(new Rectangle(i, j, 1, 1));
            }
        }
        testGraphics2d.setColor(Color.BLACK);
        testGraphics2d.dispose();
        BufferedImage shrunkenImage = ProcessImage.shrinkImage(testImage);

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 9; j++) {
                int shrunkenVal = shrunkenImage.getRaster().getSample(i, j, 0);
                int testVal = testImage.getRaster().getSample(i, j, 0);
                assertThat(shrunkenVal, equalTo(testVal));
            }
        }
    }

    @Test
    public void testDHashImageWithStripes() throws IOException {

        BufferedImage testImage = new BufferedImage(8, 9, TYPE_BYTE_GRAY);
        Graphics2D testGraphics2d = testImage.createGraphics();
        for (int j = 0; j < 9; j++) {
            testGraphics2d.setColor(j % 2 == 0 ? Color.BLACK : Color.WHITE);
            testGraphics2d.fill(new Rectangle(0, j, 8, 1));
        }

        testGraphics2d.dispose();
        boolean[] result = ProcessImage.dHash(testImage);

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                assertThat(result[i * 8 + j], equalTo(j % 2 == 0));
            }
        }
    }

    @Test
    public void testDHashImage16x18WithStripes() throws IOException {

        BufferedImage testImage = new BufferedImage(8 * 2, 9 * 2, TYPE_BYTE_GRAY);
        Graphics2D testGraphics2d = testImage.createGraphics();
        for (int j = 0; j < 9; j++) {
            testGraphics2d.setColor(j % 2 == 0 ? Color.BLACK : Color.WHITE);
            testGraphics2d.fill(new Rectangle(0, j * 2, 8 * 2, 2));
        }

        testGraphics2d.dispose();
        boolean[] result = ProcessImage.dHash(testImage);

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                assertThat(result[i * 8 + j], equalTo(j % 2 == 0));
            }
        }
    }

    @Test
    public void testProcessingResultImageWithStripes() throws IOException {

        BufferedImage testImage = new BufferedImage(8, 9, TYPE_BYTE_GRAY);
        Graphics2D testGraphics2d = testImage.createGraphics();
        for (int j = 0; j < 9; j++) {
            testGraphics2d.setColor(j % 2 == 0 ? Color.BLACK : Color.WHITE);
            testGraphics2d.fill(new Rectangle(0, j, 8, 1));
        }

        testGraphics2d.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "jpg", baos);
        baos.flush();
        byte[] imageInByte = baos.toByteArray();
        baos.close();

        ProcessedImage processedImage = ProcessImage.getProcessingResult(ByteBuffer.wrap(imageInByte), ProcessedImage.builder().imageUrl(""));
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String label = "dh_" + (i * 8 + j);
                assertThat(processedImage.dHash.get(label), equalTo(j % 2 == 0));
            }
        }
    }

    @Test
    public void testShrinkImage16x18() throws IOException {

        BufferedImage testImage = new BufferedImage(8 * 2, 9 * 2, TYPE_INT_RGB);
        Graphics2D testGraphics2d = testImage.createGraphics();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 9; j++) {
                testGraphics2d.setColor(RandomUtils.nextBoolean() ? Color.BLACK : Color.WHITE);
                testGraphics2d.fill(new Rectangle(i * 2, j * 2, 2, 2));
            }
        }
        testGraphics2d.setColor(Color.BLACK);
        testGraphics2d.dispose();
        BufferedImage shrunkenImage = ProcessImage.shrinkImage(testImage);

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 9; j++) {
                int shrunkenVal = shrunkenImage.getRaster().getSample(i, j, 0);
                int testVal = testImage.getRaster().getSample(i * 2, j * 2, 0);
                assertThat(shrunkenVal, equalTo(testVal));
            }
        }
    }
}
