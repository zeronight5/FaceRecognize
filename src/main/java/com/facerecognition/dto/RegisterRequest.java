package com.facerecognition.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人脸注册请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    /**
     * 人员姓名
     */
    @NotBlank(message = "姓名不能为空")
    private String name;
    
    /**
     * 人员ID
     */
    @NotBlank(message = "人员ID不能为空")
    private String personId;
    
    /**
     * Base64编码的图片数据
     */
    @NotBlank(message = "图片数据不能为空")
    private String imageBase64;
    
    /**
     * 备注信息
     */
    private String remark;
}
