package com.facerecognition.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusConfig {
    
    private String host = "localhost";
    private int port = 19530;
    private String username = "root";
    private String password = "Milvus";
    
    private Collection collection = new Collection();
    
    @Data
    public static class Collection {
        /**
         * 集合名称
         */
        private String name = "face_vectors";
        
        /**
         * 向量维度
         */
        private int dimension = 512;
        
        /**
         * 索引类型
         */
        private String indexType = "IVF_FLAT";
        
        /**
         * 度量类型
         */
        private String metricType = "COSINE";
        
        /**
         * nlist参数
         */
        private int nlist = 1024;
        
        /**
         * nprobe参数
         */
        private int nprobe = 10;
    }
}
