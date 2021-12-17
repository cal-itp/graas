package qrgen;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;

public class Main {
    private static void log(String s) {
        System.out.println(s);
    }

    private static void usage() {
        System.err.println("usage: Main <path-to-qr-text> <caption>");
        System.exit(0);
    }

    private static String getFileContents(String s) throws Exception {
        Path path = FileSystems.getDefault().getPath(s);
        List<String> list = Files.readAllLines(path);

        String c = "";

        for (String l : list) {
            c += l;
        }

        return c;
    }

    private static int grayScale(int n) {
        n &= 0xffffff;

        int r = (n & 0xff0000) >> 16;
        int g = (n &   0xff00) >>  8;
        int b = (n &     0xff) >>  0;

        return (r + g + b) / 3;
    }

    private static BufferedImage tint(BufferedImage img) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics g = out.getGraphics();

        g.drawImage(img, 0, 0, null);

        int[] buf = out.getRGB(
            0,
            0,
            img.getWidth(),
            img.getHeight(),
            null,
            0,
            img.getWidth()
        );

        for (int i=0; i<buf.length; i++) {
            int v = grayScale(buf[i]);
            //log("- buf[i]: " + Integer.toHexString(buf[i]));

            if (v < 0x40) {
                buf[i] = 0xff006900;
            }
        }

        out.setRGB(
            0,
            0,
            img.getWidth(),
            img.getHeight(),
            buf,
            0,
            img.getWidth()
        );

        return out;
    }

    public static void main(String[] arg) throws Exception {
        if (arg.length == 0) {
            usage();
        }

        Map<EncodeHintType, Object> map = new HashMap<EncodeHintType, Object>();
        map.put(EncodeHintType.MARGIN, 0);

        String content = getFileContents(arg[0]);
        log("- content: " + content);
        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix =
          barcodeWriter.encode(content, BarcodeFormat.QR_CODE, 300, 300, map);

        BufferedImage cimg = tint(MatrixToImageWriter.toBufferedImage(bitMatrix));

        BufferedImage img = new BufferedImage(356, 400, BufferedImage.TYPE_INT_ARGB);
        int inset = (img.getWidth() - cimg.getWidth()) / 2;
        log("- inset: " + inset);

        Graphics2D g = (Graphics2D)img.getGraphics();
        Color c = new Color(0, 105, 0);

        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        int csize = 20;
        int footerHeight = 44;

        g.setColor(Color.white);
        g.fillRoundRect(0, 0, img.getWidth(), img.getHeight(), csize, csize);

        g.setColor(c);
        g.fillRect(0, 356, img.getWidth(), 20);
        g.fillRoundRect(0, 356, img.getWidth(), footerHeight, csize, csize);

        g.drawImage(cimg, inset, inset, null);

        int fontSize = 20;
        Font f = new Font("", Font.BOLD, fontSize);
        g.setFont(f);
        g.setColor(Color.white);

        String s  = arg[1];
        FontMetrics fm = g.getFontMetrics();
        Rectangle2D r =fm.getStringBounds(s, g);


        int x = (int)((img.getWidth() - r.getWidth()) / 2);
        log("- x: " + x);
        int y = (int)(356 + fontSize + (footerHeight - r.getHeight()) / 2);
        log("- y: " + y);

        g.drawString(s, x, y);

        ImageIO.write(img, "png", new File("qr.png"));
    }
}
