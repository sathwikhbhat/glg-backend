package com.golinkgone.glgbackend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class QRCodeService {

    private static final Color QR_COLOR = Color.BLACK;
    private static final Color BACKGROUND = Color.WHITE;

    public byte[] generateQrImage(String text, int width, int height) throws WriterException, IOException {
        QRCodeWriter writer = new QRCodeWriter();

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        BufferedImage qrImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = qrImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, width, height);
        g.setColor(QR_COLOR);

        int logoSize = width / 4;

        int logoX = (width - logoSize) / 2;
        int logoY = (height - logoSize) / 2;

        int cornerRadius = logoSize / 5;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {

                if (!matrix.get(x, y)) {
                    continue;
                }

                if (insideLogoArea(x, y, logoX, logoY, logoSize)) {
                    continue;
                }

                qrImage.setRGB(x, y, QR_COLOR.getRGB());
            }
        }

        g.setColor(BACKGROUND);
        g.fill(new RoundRectangle2D.Double(logoX, logoY, logoSize, logoSize, cornerRadius, cornerRadius));

        BufferedImage logo = ImageIO.read(new ClassPathResource("static/glg_qr_logo.png").getInputStream());

        g.drawImage(logo, logoX, logoY, logoSize, logoSize, null);
        g.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "PNG", outputStream);

        return outputStream.toByteArray();
    }

    private boolean insideLogoArea(int x, int y, int logoX, int logoY, int logoSize) {
        return x >= logoX && x < logoX + logoSize && y >= logoY && y < logoY + logoSize;
    }
}
