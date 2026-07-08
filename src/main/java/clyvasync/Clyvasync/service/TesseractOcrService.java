package clyvasync.Clyvasync.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
@Service
public class TesseractOcrService {

    public String extractTextFromImage(byte[] imageBytes) {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) throw new RuntimeException("Không thể đọc ảnh.");


            ITesseract tesseract = new Tesseract();

            //tesseract.setDatapath("/opt/homebrew/share/tessdata");


            tesseract.setDatapath("/usr/share/tesseract-ocr/5/tessdata");
           // tesseract.setDatapath("/usr/share/tesseract-ocr/5/tessdata");
            tesseract.setLanguage("vie");


            log.info(">>>> [Tesseract] Đang phân tích hình ảnh...");
            String extractedText = tesseract.doOCR(image);
            log.info(">>>> [Tesseract] Quét thành công!");

            return extractedText.toUpperCase();

        } catch (Exception e) {
            log.error(">>>> [Tesseract] Lỗi OCR: {}", e.getMessage());
            return "";
        }
    }
}