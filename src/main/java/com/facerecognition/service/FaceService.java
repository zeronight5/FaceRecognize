package com.facerecognition.service;

import com.facerecognition.config.FaceConfig;
import com.facerecognition.dto.RecognizeResult;
import com.facerecognition.model.FaceDetectionResult;
import com.facerecognition.model.FaceInfo;
import com.facerecognition.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 人脸识别核心服务
 * 整合人脸检测、对齐、特征提取和向量检索
 */
@Slf4j
@Service
public class FaceService {
    
    @Autowired
    private FaceDetectionService faceDetectionService;
    
    @Autowired
    private FaceAlignmentService faceAlignmentService;
    
    @Autowired
    private FaceRecognitionService faceRecognitionService;
    
    @Autowired
    private MilvusService milvusService;
    
    @Autowired
    private FaceConfig faceConfig;
    
    /**
     * 注册人脸
     * 
     * @param name 人员姓名
     * @param personId 人员ID
     * @param imageBase64 Base64编码的图片
     * @param remark 备注
     * @return 人脸ID
     */
    public String registerFace(String name, String personId, String imageBase64, String remark) {
        try {
            // 1. 解码图片
            Mat image = ImageUtils.decodeBase64ToMat(imageBase64);
            
            // 2. 检测人脸
            List<FaceDetectionResult> detectionResults = faceDetectionService.detectFaces(image);
            
            if (detectionResults.isEmpty()) {
                throw new RuntimeException("未检测到人脸");
            }
            
            if (detectionResults.size() > 1) {
                log.warn("检测到多张人脸，仅注册第一张");
            }
            
            FaceDetectionResult detection = detectionResults.get(0);
            
            // 3. 人脸对齐
            Mat alignedFace = faceAlignmentService.alignFace(image, detection.getLandmarks());
            
            // 4. 提取特征
            float[] feature = faceRecognitionService.extractFeature(alignedFace);
            
            // 5. 生成人脸ID
            String faceId = UUID.randomUUID().toString().replace("-", "");
            
            // 6. 保存到Milvus
            FaceInfo faceInfo = FaceInfo.builder()
                    .faceId(faceId)
                    .name(name)
                    .personId(personId)
                    .feature(feature)
                    .remark(remark)
                    .registerTime(System.currentTimeMillis())
                    .build();
            
            boolean success = milvusService.insertFace(faceInfo);
            
            if (!success) {
                throw new RuntimeException("人脸向量保存失败");
            }
            
            // 7. 调试：保存检测和对齐结果图
            if (faceConfig.getDebug().isEnabled()) {
                saveDebugImages(image, detectionResults, alignedFace, faceId);
            }
            
            log.info("人脸注册成功: faceId={}, name={}, personId={}", faceId, name, personId);
            
            return faceId;
            
        } catch (Exception e) {
            log.error("人脸注册失败", e);
            throw new RuntimeException("人脸注册失败: " + e.getMessage());
        }
    }
    
    /**
     * 注册人脸（从字节数组）
     * 
     * @param name 人员姓名
     * @param personId 人员ID
     * @param imageBytes 图片字节数组
     * @param remark 备注
     * @return 人脸ID
     */
    public String registerFaceFromBytes(String name, String personId, byte[] imageBytes, String remark) {
        try {
            // 1. 解码图片
            Mat image = ImageUtils.decodeBytesToMat(imageBytes);
            
            // 2. 检测人脸
            List<FaceDetectionResult> detectionResults = faceDetectionService.detectFaces(image);
            
            if (detectionResults.isEmpty()) {
                throw new RuntimeException("未检测到人脸");
            }
            
            if (detectionResults.size() > 1) {
                log.warn("检测到多张人脸，仅注册第一张");
            }
            
            FaceDetectionResult detection = detectionResults.get(0);
            
            // 3. 人脸对齐
            Mat alignedFace = faceAlignmentService.alignFace(image, detection.getLandmarks());
            
            // 4. 提取特征
            float[] feature = faceRecognitionService.extractFeature(alignedFace);
            
            // 5. 生成人脸ID
            String faceId = UUID.randomUUID().toString().replace("-", "");
            
            // 6. 保存到Milvus
            FaceInfo faceInfo = FaceInfo.builder()
                    .faceId(faceId)
                    .name(name)
                    .personId(personId)
                    .feature(feature)
                    .remark(remark)
                    .registerTime(System.currentTimeMillis())
                    .build();
            
            boolean success = milvusService.insertFace(faceInfo);
            
            if (!success) {
                throw new RuntimeException("人脸向量保存失败");
            }
            
            // 7. 调试：保存检测和对齐结果图
            if (faceConfig.getDebug().isEnabled()) {
                saveDebugImages(image, detectionResults, alignedFace, faceId);
            }
            
            log.info("人脸注册成功: faceId={}, name={}, personId={}", faceId, name, personId);
            
            return faceId;
            
        } catch (Exception e) {
            log.error("人脸注册失败", e);
            throw new RuntimeException("人脸注册失败: " + e.getMessage());
        }
    }
    
    /**
     * 识别人脸
     * 
     * @param imageBase64 Base64编码的图片
     * @param threshold 相似度阈值
     * @param topK 返回Top-K个结果
     * @return 识别结果列表
     */
    public List<RecognizeResult> recognizeFace(String imageBase64, float threshold, int topK) {
        try {
            // 1. 解码图片
            Mat image = ImageUtils.decodeBase64ToMat(imageBase64);
            
            // 2. 检测人脸
            List<FaceDetectionResult> detectionResults = faceDetectionService.detectFaces(image);
            
            if (detectionResults.isEmpty()) {
                throw new RuntimeException("未检测到人脸");
            }
            
            if (detectionResults.size() > 1) {
                log.warn("检测到多张人脸，仅识别第一张");
            }
            
            FaceDetectionResult detection = detectionResults.get(0);
            
            // 3. 人脸对齐
            Mat alignedFace = faceAlignmentService.alignFace(image, detection.getLandmarks());
            
            // 4. 提取特征
            float[] feature = faceRecognitionService.extractFeature(alignedFace);
            
            // 5. 向量检索
            List<MilvusService.SearchResult> searchResults = milvusService.searchSimilarFaces(feature, topK);
            
            // 6. 过滤并转换结果
            List<RecognizeResult> results = new ArrayList<>();
            
            for (MilvusService.SearchResult searchResult : searchResults) {
                float similarity = searchResult.similarity;
                
                if (similarity >= threshold) {
                    FaceInfo faceInfo = searchResult.faceInfo;
                    
                    RecognizeResult result = RecognizeResult.builder()
                            .faceId(faceInfo.getFaceId())
                            .name(faceInfo.getName())
                            .personId(faceInfo.getPersonId())
                            .similarity(similarity)
                            .remark(faceInfo.getRemark())
                            .build();
                    
                    results.add(result);
                }
            }
            
            // 7. 调试：保存检测和对齐结果图
            if (faceConfig.getDebug().isEnabled()) {
                String debugId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                saveDebugImages(image, detectionResults, alignedFace, "recognize_" + debugId);
            }
            
            log.info("人脸识别完成，找到 {} 个匹配结果", results.size());
            
            return results;
            
        } catch (Exception e) {
            log.error("人脸识别失败", e);
            throw new RuntimeException("人脸识别失败: " + e.getMessage());
        }
    }
    
    /**
     * 识别人脸（从字节数组）
     * 
     * @param imageBytes 图片字节数组
     * @param threshold 相似度阈值
     * @param topK 返回Top-K个结果
     * @return 识别结果列表
     */
    public List<RecognizeResult> recognizeFaceFromBytes(byte[] imageBytes, float threshold, int topK) {
        try {
            // 1. 解码图片
            Mat image = ImageUtils.decodeBytesToMat(imageBytes);
            
            // 2. 检测人脸
            List<FaceDetectionResult> detectionResults = faceDetectionService.detectFaces(image);
            
            if (detectionResults.isEmpty()) {
                throw new RuntimeException("未检测到人脸");
            }
            
            if (detectionResults.size() > 1) {
                log.warn("检测到多张人脸，仅识别第一张");
            }
            
            FaceDetectionResult detection = detectionResults.get(0);
            
            // 3. 人脸对齐
            Mat alignedFace = faceAlignmentService.alignFace(image, detection.getLandmarks());
            
            // 4. 提取特征
            float[] feature = faceRecognitionService.extractFeature(alignedFace);
            
            // 5. 向量检索
            List<MilvusService.SearchResult> searchResults = milvusService.searchSimilarFaces(feature, topK);
            
            // 6. 过滤并转换结果
            List<RecognizeResult> results = new ArrayList<>();
            
            for (MilvusService.SearchResult searchResult : searchResults) {
                float similarity = searchResult.similarity;
                
                if (similarity >= threshold) {
                    FaceInfo faceInfo = searchResult.faceInfo;
                    
                    RecognizeResult result = RecognizeResult.builder()
                            .faceId(faceInfo.getFaceId())
                            .name(faceInfo.getName())
                            .personId(faceInfo.getPersonId())
                            .similarity(similarity)
                            .remark(faceInfo.getRemark())
                            .build();
                    
                    results.add(result);
                }
            }
            
            // 7. 调试：保存检测和对齐结果图
            if (faceConfig.getDebug().isEnabled()) {
                String debugId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                saveDebugImages(image, detectionResults, alignedFace, "recognize_" + debugId);
            }
            
            log.info("人脸识别完成，找到 {} 个匹配结果", results.size());
            
            return results;
            
        } catch (Exception e) {
            log.error("人脸识别失败", e);
            throw new RuntimeException("人脸识别失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除人脸
     * 
     * @param faceId 人脸ID
     * @return 是否成功
     */
    public boolean deleteFace(String faceId) {
        return milvusService.deleteFace(faceId);
    }
    
    /**
     * 删除某人员的所有人脸
     * 
     * @param personId 人员ID
     * @return 是否成功
     */
    public boolean deletePersonFaces(String personId) {
        return milvusService.deletePersonFaces(personId);
    }
    
    /**
     * 重置人脸库
     * 
     * @return 是否成功
     */
    public boolean resetDatabase() {
        return milvusService.resetDatabase();
    }
    
    /**
     * 查询所有人脸
     * 
     * @param limit 最大返回数量
     * @return 人脸信息列表
     */
    public List<FaceInfo> listAllFaces(int limit) {
        return milvusService.queryAllFaces(limit);
    }
    
    /**
     * 根据person_id查询人脸
     * 
     * @param personId 人员ID
     * @return 人脸信息列表
     */
    public List<FaceInfo> queryByPersonId(String personId) {
        return milvusService.queryFacesByPersonId(personId);
    }
    
    /**
     * 根据姓名查询人脸
     * 
     * @param name 姓名
     * @return 人脸信息列表
     */
    public List<FaceInfo> queryByName(String name) {
        return milvusService.queryFacesByName(name);
    }
    
    /**
     * 保存调试图片
     */
    private void saveDebugImages(Mat originalImage, List<FaceDetectionResult> detectionResults, 
                                 Mat alignedFace, String prefix) {
        try {
            String debugDir = faceConfig.getDebug().getOutputDir();
            File dir = new File(debugDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // 保存检测标注图
            String detectionPath = debugDir + "/" + prefix + "_detection.jpg";
            faceAlignmentService.drawLandmarks(originalImage, detectionResults, detectionPath);
            
            // 保存对齐人脸图
            String alignedPath = debugDir + "/" + prefix + "_aligned.jpg";
            faceAlignmentService.saveAlignedFace(alignedFace, alignedPath);
            
        } catch (Exception e) {
            log.error("保存调试图片失败", e);
        }
    }
}
