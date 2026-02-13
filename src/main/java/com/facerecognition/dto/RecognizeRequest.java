package com.facerecognition.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人脸识别请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognizeRequest {
    /**
     * Base64编码的图片数据
     */
    @NotBlank(message = "图片数据不能为空")
    private String imageBase64;
    
    /**
     * 相似度阈值 (0-1)
     */
    @Builder.Default
    private Float threshold = 0.6f;
    
    /**
     * 返回Top-K个结果
     */
    @Builder.Default
    private Integer topK = 5;
}
