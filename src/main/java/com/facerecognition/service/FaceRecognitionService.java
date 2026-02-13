package com.facerecognition.service;

import ai.onnxruntime.*;
import com.facerecognition.config.OnnxConfig;
import com.facerecognition.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * ONNX人脸识别服务 - ArcFace模型
 * 提取512维人脸特征向量
 */
@Slf4j
@Service
public class FaceRecognitionService {
    
    @Autowired
    private OnnxConfig onnxConfig;
    
    private OrtEnvironment environment;
    private OrtSession session;
    private String inputName;
    
    @PostConstruct
    public void init() throws Exception {
        log.info("初始化ONNX人脸识别服务...");
        
        // 创建ONNX Runtime环境
        environment = OrtEnvironment.getEnvironment();
        
        // 配置Session选项
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        sessionOptions.setInterOpNumThreads(onnxConfig.getThreadPool().getCoreSize());
        sessionOptions.setIntraOpNumThreads(onnxConfig.getThreadPool().getCoreSize());
        
        // 加载模型
        String modelPath = onnxConfig.getModel().getRecognition();
        session = environment.createSession(modelPath, sessionOptions);
        
        // 获取输入名称
        inputName = session.getInputNames().iterator().next();
        
        log.info("ONNX人脸识别模型加载成功: {}", modelPath);
        log.info("模型输入: {}", inputName);
        log.info("模型输出: {}", session.getOutputNames());
    }
    
    /**
     * 提取人脸特征向量
     * 
     * @param alignedFace 对齐后的人脸图片 (112x112, BGR格式)
     * @return 512维特征向量
     */
    public float[] extractFeature(Mat alignedFace) {
        try {
            // 1. 转换为ONNX输入格式
            // ArcFace模型通常使用均值[127.5, 127.5, 127.5]，标准差[128.0, 128.0, 128.0]
            float[] inputData = ImageUtils.matToOnnxInput(alignedFace, 
                    new float[]{127.5f, 127.5f, 127.5f}, 
                    new float[]{128.0f, 128.0f, 128.0f}, 
                    false);
            
            // 2. 创建ONNX输入Tensor
            int inputSize = alignedFace.height();  // 通常是112
            long[] inputShape = new long[]{1, 3, inputSize, inputSize};
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, 
                    FloatBuffer.wrap(inputData), inputShape);
            
            // 3. 执行推理
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, inputTensor);
            
            OrtSession.Result result = session.run(inputs);
            
            // 4. 获取特征向量
            String outputName = session.getOutputNames().iterator().next();
            OnnxTensor outputTensor = (OnnxTensor) result.get(outputName).get();
            
            float[][] output = (float[][]) outputTensor.getValue();
            float[] feature = output[0];  // 512维特征
            
            // 5. L2归一化
            feature = normalizeFeature(feature);
            
            // 清理资源
            inputTensor.close();
            result.close();
            
            log.debug("特征提取成功，维度: {}", feature.length);
            
            return feature;
            
        } catch (Exception e) {
            log.error("人脸特征提取失败", e);
            throw new RuntimeException("人脸特征提取失败: " + e.getMessage());
        }
    }
    
    /**
     * L2归一化特征向量
     * 
     * @param feature 原始特征
     * @return 归一化后的特征
     */
    private float[] normalizeFeature(float[] feature) {
        double norm = 0.0;
        
        // 计算L2范数
        for (float v : feature) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        // 归一化
        float[] normalized = new float[feature.length];
        for (int i = 0; i < feature.length; i++) {
            normalized[i] = (float) (feature[i] / norm);
        }
        
        return normalized;
    }
    
    /**
     * 计算两个特征向量的余弦相似度
     * 
     * @param feature1 特征向量1
     * @param feature2 特征向量2
     * @return 余弦相似度 (0-1)
     */
    public float cosineSimilarity(float[] feature1, float[] feature2) {
        if (feature1.length != feature2.length) {
            throw new IllegalArgumentException("特征维度不匹配");
        }
        
        float dotProduct = 0.0f;
        for (int i = 0; i < feature1.length; i++) {
            dotProduct += feature1[i] * feature2[i];
        }
        
        // 如果特征已经归一化，余弦相似度就是点积
        // 转换到[0, 1]区间
        return (dotProduct + 1.0f) / 2.0f;
    }
    
    @PreDestroy
    public void cleanup() throws Exception {
        if (session != null) {
            session.close();
        }
        log.info("ONNX人脸识别服务已关闭");
    }
}
