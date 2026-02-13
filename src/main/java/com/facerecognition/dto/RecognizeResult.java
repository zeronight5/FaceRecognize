package com.facerecognition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人脸识别结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognizeResult {
    /**
     * 人脸ID
     */
    private String faceId;
    
    /**
     * 人员姓名
     */
    private String name;
    
    /**
     * 人员ID
     */
    private String personId;
    
    /**
     * 相似度分数 (0-1)
     */
    private Float similarity;
    
    /**
     * 备注信息
     */
    private String remark;
}
