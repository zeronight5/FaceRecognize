package com.facerecognition.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人脸信息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceInfo {
    /**
     * 人脸唯一标识ID
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
     * 人脸特征向量 (512维)
     */
    private float[] feature;
    
    /**
     * 备注信息
     */
    private String remark;
    
    /**
     * 注册时间
     */
    private Long registerTime;
}
