package com.qms.modules.sampling.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 样品条码：Code128（YP 编号可扫）。
 */
@Service
public class BarcodeService {

    /**
     * 生成 Code128 PNG。
     *
     * @param content 条码内容（样品编号）
     * @param width   像素宽
     * @param height  像素高
     */
    public byte[] code128Png(String content, int width, int height) {
        try {
            Code128Writer writer = new Code128Writer();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.CODE_128, width, height,
                    Map.of(EncodeHintType.MARGIN, 2,
                            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "条码生成失败: " + e.getMessage());
        }
    }
}
