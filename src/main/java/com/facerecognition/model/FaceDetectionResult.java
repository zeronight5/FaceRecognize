package com.facerecognition.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opencv.core.Mat;
import org.opencv.core.Point;

/**
 * 人脸检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceDetectionResult {
    /**
     * 人脸边界框 [x1, y1, x2, y2]
     */
    private float[] bbox;
    
    /**
     * 检测置信度 (0-1)
     */
    private float confidence;
    
    /**
     * 人脸关键点 (5个点: 左眼、右眼、鼻尖、左嘴角、右嘴角)
     */
    private Point[] landmarks;
    
    /**
     * 对齐后的人脸图片 (OpenCV Mat对象)
     */
    private Mat alignedFace;
    
    /**
     * 人脸特征向量 (512维)
     */
    private float[] feature;
}
