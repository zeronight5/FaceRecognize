package com.facerecognition.service;

import com.facerecognition.config.FaceConfig;
import com.facerecognition.model.FaceDetectionResult;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenCV人脸对齐服务
 * 使用仿射变换进行高精度人脸对齐
 */
@Slf4j
@Service
public class FaceAlignmentService {
    
    @Autowired
    private FaceConfig faceConfig;
    
    // 标准人脸关键点位置 (112x112图片中的标准位置)
    private static final Point[] STANDARD_LANDMARKS = new Point[]{
        new Point(38.2946, 51.6963),  // 左眼
        new Point(73.5318, 51.5014),  // 右眼
        new Point(56.0252, 71.7366),  // 鼻尖
        new Point(41.5493, 92.3655),  // 左嘴角
        new Point(70.7299, 92.2041)   // 右嘴角
    };
    
    static {
        // 加载OpenCV本地库
        OpenCV.loadLocally();
        log.info("OpenCV本地库加载成功，版本: {}", Core.VERSION);
    }
    
    @PostConstruct
    public void init() {
        // 创建调试输出目录
        if (faceConfig.getDebug().isEnabled()) {
            File debugDir = new File(faceConfig.getDebug().getOutputDir());
            if (!debugDir.exists()) {
                debugDir.mkdirs();
                log.info("创建调试输出目录: {}", debugDir.getAbsolutePath());
            }
        }
    }
    
    /**
     * 人脸对齐
     * 
     * @param srcImage 原始图片
     * @param landmarks 人脸关键点 (5个点)
     * @return 对齐后的人脸图片
     */
    public Mat alignFace(Mat srcImage, Point[] landmarks) {
        if (landmarks == null || landmarks.length != 5) {
            throw new IllegalArgumentException("人脸关键点数量必须为5");
        }
        
        try {
            // 将关键点转换为MatOfPoint2f
            List<Point> srcPoints = new ArrayList<>();
            for (Point landmark : landmarks) {
                srcPoints.add(landmark);
            }
            MatOfPoint2f srcMat = new MatOfPoint2f();
            srcMat.fromList(srcPoints);
            
            // 标准关键点
            List<Point> dstPoints = new ArrayList<>();
            for (Point point : STANDARD_LANDMARKS) {
                dstPoints.add(point);
            }
            MatOfPoint2f dstMat = new MatOfPoint2f();
            dstMat.fromList(dstPoints);
            
            // 计算仿射变换矩阵（相似变换）
            Mat transformMatrix = estimateSimilarityTransform(srcMat, dstMat);
            
            // 应用仿射变换
            Mat alignedFace = new Mat();
            int outputSize = faceConfig.getAlignment().getOutputSize();
            Imgproc.warpAffine(srcImage, alignedFace, transformMatrix, 
                    new Size(outputSize, outputSize));
            
            log.debug("人脸对齐完成: {}x{}", alignedFace.width(), alignedFace.height());
            
            return alignedFace;
        } catch (Exception e) {
            log.error("人脸对齐失败", e);
            throw new RuntimeException("人脸对齐失败: " + e.getMessage());
        }
    }
    
    /**
     * 估计相似变换矩阵 (允许旋转、缩放、平移，但不允许倾斜)
     * 
     * @param src 源关键点
     * @param dst 目标关键点
     * @return 2x3仿射变换矩阵
     */
    private Mat estimateSimilarityTransform(MatOfPoint2f src, MatOfPoint2f dst) {
        // 使用最小二乘法求解相似变换
        // 相似变换: [x', y'] = s * R * [x, y] + [tx, ty]
        // 其中 s 是缩放因子, R 是旋转矩阵, [tx, ty] 是平移
        
        Point[] srcPoints = src.toArray();
        Point[] dstPoints = dst.toArray();
        
        int n = srcPoints.length;
        
        // 计算质心
        double srcCenterX = 0, srcCenterY = 0;
        double dstCenterX = 0, dstCenterY = 0;
        
        for (int i = 0; i < n; i++) {
            srcCenterX += srcPoints[i].x;
            srcCenterY += srcPoints[i].y;
            dstCenterX += dstPoints[i].x;
            dstCenterY += dstPoints[i].y;
        }
        
        srcCenterX /= n;
        srcCenterY /= n;
        dstCenterX /= n;
        dstCenterY /= n;
        
        // 去质心
        double[][] srcCentered = new double[n][2];
        double[][] dstCentered = new double[n][2];
        
        for (int i = 0; i < n; i++) {
            srcCentered[i][0] = srcPoints[i].x - srcCenterX;
            srcCentered[i][1] = srcPoints[i].y - srcCenterY;
            dstCentered[i][0] = dstPoints[i].x - dstCenterX;
            dstCentered[i][1] = dstPoints[i].y - dstCenterY;
        }
        
        // 计算方差
        double srcVar = 0;
        for (int i = 0; i < n; i++) {
            srcVar += srcCentered[i][0] * srcCentered[i][0] 
                    + srcCentered[i][1] * srcCentered[i][1];
        }
        srcVar /= n;
        
        // 构建协方差矩阵
        double sumA = 0, sumB = 0;
        for (int i = 0; i < n; i++) {
            sumA += dstCentered[i][0] * srcCentered[i][0] 
                  + dstCentered[i][1] * srcCentered[i][1];
            sumB += dstCentered[i][1] * srcCentered[i][0] 
                  - dstCentered[i][0] * srcCentered[i][1];
        }
        
        double scale = Math.sqrt(sumA * sumA + sumB * sumB) / srcVar / n;
        double theta = Math.atan2(sumB, sumA);
        
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        
        // 构建变换矩阵
        // [m00, m01, m02]
        // [m10, m11, m12]
        double m00 = scale * cosTheta;
        double m01 = -scale * sinTheta;
        double m10 = scale * sinTheta;
        double m11 = scale * cosTheta;
        double m02 = dstCenterX - m00 * srcCenterX - m01 * srcCenterY;
        double m12 = dstCenterY - m10 * srcCenterX - m11 * srcCenterY;
        
        Mat transformMatrix = new Mat(2, 3, CvType.CV_64F);
        transformMatrix.put(0, 0, m00, m01, m02);
        transformMatrix.put(1, 0, m10, m11, m12);
        
        return transformMatrix;
    }
    
    /**
     * 在图片上绘制人脸关键点（用于调试）
     * 
     * @param image 原始图片
     * @param detectionResults 检测结果列表
     * @param savePath 保存路径
     */
    public void drawLandmarks(Mat image, List<FaceDetectionResult> detectionResults, String savePath) {
        if (!faceConfig.getDebug().isEnabled()) {
            return;
        }
        
        // 调试：检查输入图片信息
        log.debug("drawLandmarks - 输入图片: {}x{}, channels={}, type={}", 
                image.width(), image.height(), image.channels(), image.type());
        
        // 调试：检查输入图片的像素采样
        int h = image.height();
        int w = image.width();
        double[] inputCenterPixel = image.get(h/2, w/2);
        double[] inputTopLeftPixel = image.get(Math.min(10, h-1), Math.min(10, w-1));
        log.debug("输入图片像素采样 - 中心点({},{}): {}, 左上角(10,10): {}", 
                w/2, h/2, java.util.Arrays.toString(inputCenterPixel), 
                java.util.Arrays.toString(inputTopLeftPixel));
        
        Mat outputImage = image.clone();
        
        // 调试：检查clone后的图片
        log.debug("drawLandmarks - clone后: {}x{}, channels={}, type={}", 
                outputImage.width(), outputImage.height(), outputImage.channels(), outputImage.type());
        
        for (FaceDetectionResult result : detectionResults) {
            // 绘制边界框
            float[] bbox = result.getBbox();
            Point topLeft = new Point(bbox[0], bbox[1]);
            Point bottomRight = new Point(bbox[2], bbox[3]);
            Imgproc.rectangle(outputImage, topLeft, bottomRight, new Scalar(0, 255, 0), 2);
            
            // 绘制关键点
            if (result.getLandmarks() != null) {
                for (Point landmark : result.getLandmarks()) {
                    Imgproc.circle(outputImage, landmark, 2, new Scalar(0, 0, 255), -1);
                }
            }
            
            // 显示置信度
            String confidenceText = String.format("%.2f", result.getConfidence());
            Imgproc.putText(outputImage, confidenceText, 
                    new Point(bbox[0], bbox[1] - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, 
                    new Scalar(0, 255, 0), 1);
        }
        
        // 调试：检查图片中心点和几个角落的像素值
        double[] centerPixel = outputImage.get(h/2, w/2);
        double[] topLeftPixel = outputImage.get(10, 10);
        log.debug("保存前像素采样 - 中心点({},{}): {}, 左上角(10,10): {}", 
                w/2, h/2, java.util.Arrays.toString(centerPixel), java.util.Arrays.toString(topLeftPixel));
        
        Imgcodecs.imwrite(savePath, outputImage);
        log.debug("关键点标注图已保存: {}", savePath);
    }
    
    /**
     * 保存对齐后的人脸图片（用于调试）
     * 
     * @param alignedFace 对齐后的人脸
     * @param savePath 保存路径
     */
    public void saveAlignedFace(Mat alignedFace, String savePath) {
        if (!faceConfig.getDebug().isEnabled()) {
            return;
        }
        
        Imgcodecs.imwrite(savePath, alignedFace);
        log.debug("对齐人脸图已保存: {}", savePath);
    }
}
