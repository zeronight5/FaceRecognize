package com.facerecognition.util;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Base64;

/**
 * 图片格式统一处理工具类
 * 解决不同框架之间的图片格式差异和画面拉伸问题
 */
@Slf4j
public class ImageUtils {
    
    /**
     * 从Base64字符串解码图片为OpenCV Mat (BGR格式)
     * 
     * @param base64Str Base64编码的图片字符串
     * @return OpenCV Mat对象 (BGR格式)
     */
    public static Mat decodeBase64ToMat(String base64Str) {
        try {
            // 移除Base64前缀（如果存在）
            String imageData = base64Str;
            if (imageData.contains(",")) {
                imageData = imageData.split(",")[1];
            }
            
            // Base64解码
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            
            // 使用OpenCV解码图片（自动处理格式，输出BGR）
            Mat mat = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
            
            if (mat.empty()) {
                throw new RuntimeException("图片解码失败");
            }
            
            log.debug("图片解码成功: {}x{}, channels={}", mat.width(), mat.height(), mat.channels());
            return mat;
        } catch (Exception e) {
            log.error("Base64图片解码失败", e);
            throw new RuntimeException("图片解码失败: " + e.getMessage());
        }
    }
    
    /**
     * 从字节数组解码图片为OpenCV Mat (BGR格式)
     * 
     * @param imageBytes 图片字节数组
     * @return OpenCV Mat对象 (BGR格式)
     */
    public static Mat decodeBytesToMat(byte[] imageBytes) {
        try {
            Mat mat = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
            
            if (mat.empty()) {
                throw new RuntimeException("图片解码失败");
            }
            
            log.debug("图片解码成功: {}x{}, channels={}", mat.width(), mat.height(), mat.channels());
            return mat;
        } catch (Exception e) {
            log.error("字节数组图片解码失败", e);
            throw new RuntimeException("图片解码失败: " + e.getMessage());
        }
    }
    
    /**
     * BGR转RGB格式
     * 
     * @param bgrMat BGR格式的Mat
     * @return RGB格式的Mat
     */
    public static Mat bgrToRgb(Mat bgrMat) {
        Mat rgbMat = new Mat();
        Imgproc.cvtColor(bgrMat, rgbMat, Imgproc.COLOR_BGR2RGB);
        return rgbMat;
    }
    
    /**
     * RGB转BGR格式
     * 
     * @param rgbMat RGB格式的Mat
     * @return BGR格式的Mat
     */
    public static Mat rgbToBgr(Mat rgbMat) {
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(rgbMat, bgrMat, Imgproc.COLOR_RGB2BGR);
        return bgrMat;
    }
    
    /**
     * 保持宽高比的图片缩放（letterbox方式）
     * 用于人脸检测模型输入，避免画面拉伸变形
     * 
     * @param srcMat 原始图片
     * @param targetSize 目标尺寸（正方形）
     * @return 缩放后的图片和缩放信息
     */
    public static ResizeResult letterboxResize(Mat srcMat, int targetSize) {
        int srcWidth = srcMat.width();
        int srcHeight = srcMat.height();
        
        // 计算缩放比例（保持宽高比）
        float scale = Math.min((float) targetSize / srcWidth, (float) targetSize / srcHeight);
        
        // 缩放后的尺寸
        int newWidth = Math.round(srcWidth * scale);
        int newHeight = Math.round(srcHeight * scale);
        
        // 缩放图片
        Mat resizedMat = new Mat();
        Imgproc.resize(srcMat, resizedMat, new Size(newWidth, newHeight));
        
        // 创建目标画布（填充灰色）
        Mat targetMat = new Mat(targetSize, targetSize, srcMat.type(), new Scalar(114, 114, 114));
        
        // 计算偏移量（居中放置）
        int offsetX = (targetSize - newWidth) / 2;
        int offsetY = (targetSize - newHeight) / 2;
        
        // 将缩放后的图片放到画布上
        Mat roi = targetMat.submat(offsetY, offsetY + newHeight, offsetX, offsetX + newWidth);
        resizedMat.copyTo(roi);
        
        log.debug("Letterbox缩放: {}x{} -> {}x{}, scale={}, offset=({}, {})", 
                srcWidth, srcHeight, newWidth, newHeight, scale, offsetX, offsetY);
        
        ResizeResult result = new ResizeResult();
        result.resizedMat = targetMat;
        result.scale = scale;
        result.offsetX = offsetX;
        result.offsetY = offsetY;
        result.newWidth = newWidth;
        result.newHeight = newHeight;
        
        return result;
    }
    
    /**
     * 直接缩放（会拉伸）
     * 
     * @param srcMat 原始图片
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @return 缩放后的图片
     */
    public static Mat directResize(Mat srcMat, int targetWidth, int targetHeight) {
        Mat resizedMat = new Mat();
        Imgproc.resize(srcMat, resizedMat, new Size(targetWidth, targetHeight));
        return resizedMat;
    }
    
    /**
     * 将OpenCV Mat转换为ONNX模型输入格式
     * 归一化到[0, 1]，通道顺序从HWC转为CHW
     * 
     * @param mat OpenCV Mat (BGR格式，HWC顺序)
     * @param mean 均值 (RGB顺序)
     * @param std 标准差 (RGB顺序)
     * @param normalize 是否归一化到[0,1]
     * @return ONNX输入数组 (1, C, H, W)
     */
    public static float[] matToOnnxInput(Mat mat, float[] mean, float[] std, boolean normalize) {
        int height = mat.height();
        int width = mat.width();
        int channels = mat.channels();
        
        float[] input = new float[1 * channels * height * width];
        
        // BGR -> RGB 并转换为CHW格式
        byte[] data = new byte[height * width * channels];
        mat.get(0, 0, data);
        
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                for (int c = 0; c < channels; c++) {
                    int srcIdx = (h * width + w) * channels + c;
                    
                    // BGR -> RGB: OpenCV使用BGR，需要反转到RGB
                    int rgbChannel = 2 - c;
                    int dstIdx = rgbChannel * height * width + h * width + w;
                    
                    float pixelValue = (data[srcIdx] & 0xFF);
                    
                    // 归一化
                    if (normalize) {
                        pixelValue = pixelValue / 255.0f;
                    }
                    
                    // 减均值除标准差
                    if (mean != null && std != null) {
                        pixelValue = (pixelValue - mean[rgbChannel]) / std[rgbChannel];
                    }
                    
                    input[dstIdx] = pixelValue;
                }
            }
        }
        
        return input;
    }
    
    /**
     * Mat转为ONNX输入（默认归一化）
     */
    public static float[] matToOnnxInput(Mat mat) {
        return matToOnnxInput(mat, new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f}, true);
    }
    
    /**
     * 将坐标从缩放后的图片映射回原图
     * 
     * @param x 缩放图中的x坐标
     * @param y 缩放图中的y坐标
     * @param result 缩放信息
     * @return 原图坐标 [x, y]
     */
    public static float[] mapToOriginal(float x, float y, ResizeResult result) {
        float originalX = (x - result.offsetX) / result.scale;
        float originalY = (y - result.offsetY) / result.scale;
        return new float[]{originalX, originalY};
    }
    
    /**
     * 保存Mat为图片文件（用于调试）
     * 
     * @param mat OpenCV Mat
     * @param filepath 文件路径
     */
    public static void saveMat(Mat mat, String filepath) {
        Imgcodecs.imwrite(filepath, mat);
        log.debug("图片已保存: {}", filepath);
    }
    
    /**
     * 缩放结果信息
     */
    public static class ResizeResult {
        public Mat resizedMat;
        public float scale;
        public int offsetX;
        public int offsetY;
        public int newWidth;
        public int newHeight;
    }
}
