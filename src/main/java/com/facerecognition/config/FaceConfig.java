package com.facerecognition.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 人脸识别配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "face")
public class FaceConfig {
    
    private Detection detection = new Detection();
    private Recognition recognition = new Recognition();
    private Alignment alignment = new Alignment();
    private Debug debug = new Debug();
    
    @Data
    public static class Detection {
        /**
         * 检测置信度阈值
         */
        private float confidence = 0.5f;
        
        /**
         * 输入图片尺寸
         */
        private int inputSize = 640;
        
        /**
         * 最小人脸尺寸
         */
        private int minFaceSize = 20;
    }
    
    @Data
    public static class Recognition {
        /**
         * 识别相似度阈值
         */
        private float threshold = 0.6f;
        
        /**
         * Top-K结果数
         */
        private int topK = 5;
    }
    
    @Data
    public static class Alignment {
        /**
         * 对齐后人脸尺寸
         */
        private int outputSize = 112;
    }
    
    @Data
    public static class Debug {
        /**
         * 是否启用调试模式
         */
        private boolean enabled = true;
        
        /**
         * 调试输出目录
         */
        private String outputDir = "debug_output";
    }
}
