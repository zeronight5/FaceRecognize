package com.facerecognition.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ONNX Runtime配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "onnx")
public class OnnxConfig {
    
    private Model model = new Model();
    private ThreadPool threadPool = new ThreadPool();
    
    @Data
    public static class Model {
        /**
         * 检测模型路径
         */
        private String detection = "models/det_10g.onnx";
        
        /**
         * 识别模型路径
         */
        private String recognition = "models/w600k_r50.onnx";
    }
    
    @Data
    public static class ThreadPool {
        /**
         * 核心线程数
         */
        private int coreSize = 4;
        
        /**
         * 最大线程数
         */
        private int maxSize = 8;
    }
}
