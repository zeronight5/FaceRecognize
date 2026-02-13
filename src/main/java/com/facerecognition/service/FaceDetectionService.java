package com.facerecognition.service;

import ai.onnxruntime.*;
import com.facerecognition.config.FaceConfig;
import com.facerecognition.config.OnnxConfig;
import com.facerecognition.model.FaceDetectionResult;
import com.facerecognition.util.ImageUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * ONNX人脸检测服务 - SCRFD模型（完整NMS实现）
 */
@Slf4j
@Service
public class FaceDetectionService {
    
    @Autowired
    private OnnxConfig onnxConfig;
    
    @Autowired
    private FaceConfig faceConfig;
    
    private OrtEnvironment environment;
    private OrtSession session;
    private String inputName;
    
    // SCRFD模型配置
    private static final int[] FEATURE_STRIDE_FPN = {8, 16, 32};  // 特征图步长
    private static final int NUM_ANCHORS = 2;  // 每个位置的anchor数量
    private static final float NMS_THRESHOLD = 0.4f;  // NMS阈值
    
    // 预生成的anchor中心点
    private Map<Integer, float[][]> anchorCenters;
    
    @PostConstruct
    public void init() throws Exception {
        log.info("初始化ONNX人脸检测服务...");
        
        // 创建ONNX Runtime环境
        environment = OrtEnvironment.getEnvironment();
        
        // 配置Session选项
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        sessionOptions.setInterOpNumThreads(onnxConfig.getThreadPool().getCoreSize());
        sessionOptions.setIntraOpNumThreads(onnxConfig.getThreadPool().getCoreSize());
        
        // 加载模型
        String modelPath = onnxConfig.getModel().getDetection();
        session = environment.createSession(modelPath, sessionOptions);
        
        // 获取输入名称
        inputName = session.getInputNames().iterator().next();
        
        // 预生成anchor中心点
        anchorCenters = new HashMap<>();
        int inputSize = faceConfig.getDetection().getInputSize();
        for (int stride : FEATURE_STRIDE_FPN) {
            anchorCenters.put(stride, generateAnchorCenters(inputSize, stride));
        }
        
        log.info("ONNX人脸检测模型加载成功: {}", modelPath);
        log.info("模型输入: {}", inputName);
        log.info("模型输出: {}", session.getOutputNames());
        log.info("Anchor生成完成，步长: {}", Arrays.toString(FEATURE_STRIDE_FPN));
    }
    
    /**
     * 检测人脸
     * 
     * @param image 输入图片 (BGR格式)
     * @return 人脸检测结果列表
     */
    public List<FaceDetectionResult> detectFaces(Mat image) {
        try {
            int inputSize = faceConfig.getDetection().getInputSize();
            
            // 1. 图片预处理（保持宽高比缩放）
            ImageUtils.ResizeResult resizeResult = ImageUtils.letterboxResize(image, inputSize);
            Mat resizedImage = resizeResult.resizedMat;
            
            // 2. 转换为ONNX输入格式
            float[] inputData = ImageUtils.matToOnnxInput(resizedImage, 
                    new float[]{127.5f, 127.5f, 127.5f}, 
                    new float[]{128.0f, 128.0f, 128.0f}, 
                    false);
            
            // 3. 创建ONNX输入Tensor
            long[] inputShape = new long[]{1, 3, inputSize, inputSize};
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, 
                    FloatBuffer.wrap(inputData), inputShape);
            
            // 4. 执行推理
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, inputTensor);
            
            OrtSession.Result result = session.run(inputs);
            
            // 5. 解析输出（完整NMS后处理）
            List<FaceDetectionResult> faces = parseDetectionOutput(result, resizeResult);
            
            // 清理资源
            inputTensor.close();
            result.close();
            
            log.debug("检测到 {} 张人脸", faces.size());
            
            return faces;
            
        } catch (Exception e) {
            log.error("人脸检测失败", e);
            throw new RuntimeException("人脸检测失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析SCRFD模型输出（完整NMS后处理）
     * 
     * @param result ONNX推理结果
     * @param resizeResult 缩放信息
     * @return 人脸检测结果列表
     */
    private List<FaceDetectionResult> parseDetectionOutput(OrtSession.Result result, 
                                                           ImageUtils.ResizeResult resizeResult) 
            throws OrtException {
        
        // 打印所有输出的详细信息（用于调试）
        log.info("========== ONNX模型输出信息 ==========");
        log.info("输出数量: {}", result.size());
        for (int i = 0; i < result.size(); i++) {
            try {
                OnnxValue value = result.get(i);
                if (value instanceof OnnxTensor) {
                    OnnxTensor tensor = (OnnxTensor) value;
                    log.info("输出[{}]: shape={}, type={}", 
                            i, Arrays.toString(tensor.getInfo().getShape()), 
                            tensor.getInfo().type);
                }
            } catch (Exception e) {
                log.warn("无法获取输出[{}]信息: {}", i, e.getMessage());
            }
        }
        
        // 打印输出名称（如果有）
        try {
            Set<String> outputNames = session.getOutputNames();
            log.info("输出名称: {}", outputNames);
        } catch (Exception e) {
            log.debug("无法获取输出名称");
        }
        log.info("=====================================");
        
        // 收集所有尺度的检测框
        List<DetectionBox> allBoxes = new ArrayList<>();
        
        // SCRFD输出格式：score_8, score_16, score_32, bbox_8, bbox_16, bbox_32, kps_8, kps_16, kps_32
        // 遍历所有尺度
        for (int strideIdx = 0; strideIdx < FEATURE_STRIDE_FPN.length; strideIdx++) {
            int stride = FEATURE_STRIDE_FPN[strideIdx];
            
            try {
                // 获取对应尺度的输出
                String scoreKey = "score_" + stride;
                String bboxKey = "bbox_" + stride;
                String kpsKey = "kps_" + stride;
                
                OnnxValue scoreValue = result.get(scoreKey).orElse(null);
                OnnxValue bboxValue = result.get(bboxKey).orElse(null);
                OnnxValue kpsValue = result.get(kpsKey).orElse(null);
                
                if (scoreValue == null || bboxValue == null) {
                    // 尝试使用索引访问（SCRFD模型输出顺序：score[0,1,2], bbox[3,4,5], kps[6,7,8]）
                    try {
                        scoreValue = result.get(strideIdx);        // score: 0,1,2
                        bboxValue = result.get(strideIdx + 3);     // bbox: 3,4,5
                        if (result.size() > strideIdx + 6) {
                            kpsValue = result.get(strideIdx + 6);  // kps: 6,7,8
                        }
                    } catch (Exception indexEx) {
                        log.debug("使用索引{}访问输出失败: {}", strideIdx, indexEx.getMessage());
                    }
                }
                
                if (scoreValue != null && bboxValue != null) {
                    OnnxTensor scoreTensor = (OnnxTensor) scoreValue;
                    OnnxTensor bboxTensor = (OnnxTensor) bboxValue;
                    OnnxTensor kpsTensor = kpsValue != null ? (OnnxTensor) kpsValue : null;
                    
                    // 打印tensor shape以便调试
                    long[] scoreShape = scoreTensor.getInfo().getShape();
                    long[] bboxShape = bboxTensor.getInfo().getShape();
                    log.debug("stride={}, score shape={}, bbox shape={}", 
                            stride, Arrays.toString(scoreShape), Arrays.toString(bboxShape));
                    
                    // 获取实际数据
                    Object scoreData = scoreTensor.getValue();
                    Object bboxData = bboxTensor.getValue();
                    Object kpsData = kpsTensor != null ? kpsTensor.getValue() : null;
                    
                    // 解析当前尺度的检测结果
                    parseScaleOutput(scoreData, bboxData, kpsData, scoreShape, bboxShape,
                            stride, allBoxes, resizeResult);
                }
            } catch (Exception e) {
                log.warn("解析stride={}的输出失败: {}", stride, e.getMessage());
            }
        }
        
        if (allBoxes.isEmpty()) {
            log.debug("未检测到人脸");
            return Collections.emptyList();
        }
        
        log.info("NMS前检测框总数: {}", allBoxes.size());
        
        // NMS去重
        List<DetectionBox> nmsBoxes = nms(allBoxes, NMS_THRESHOLD);
        
        // 转换为FaceDetectionResult
        List<FaceDetectionResult> faces = new ArrayList<>();
        for (DetectionBox box : nmsBoxes) {
            faces.add(box.toFaceDetectionResult());
        }
        
        // 按置信度排序
        faces.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        
        log.info("NMS后检测到 {} 张人脸", faces.size());
        
        return faces;
    }
    
    /**
     * 解析单个尺度的输出
     */
    private void parseScaleOutput(Object scoreData, Object bboxData, Object kpsData,
                                  long[] scoreShape, long[] bboxShape,
                                  int stride, List<DetectionBox> allBoxes,
                                  ImageUtils.ResizeResult resizeResult) {
        
        float[][] centers = anchorCenters.get(stride);
        if (centers == null) {
            log.warn("未找到stride={}的anchor中心点", stride);
            return;
        }
        
        float confThreshold = faceConfig.getDetection().getConfidence();
        int inputSize = faceConfig.getDetection().getInputSize();
        
        try {
            // 打印数据类型信息
            log.info("stride={}, scoreData类型={}, bboxData类型={}", 
                    stride, scoreData.getClass().getName(), bboxData.getClass().getName());
            
            // 根据shape判断数据格式并解析
            // SCRFD通常输出格式: [batch, height, width, num_anchors] 或 [batch, num_anchors*height*width]
            
            float[] scoreFlat = flattenToFloatArray(scoreData, scoreShape);
            float[] bboxFlat = flattenToFloatArray(bboxData, bboxShape);
            float[] kpsFlat = kpsData != null ? flattenToFloatArray(kpsData, null) : null;
            
            // 打印前几个值用于诊断
            log.info("stride={}, scoreFlat前10个值: {}", stride, 
                    Arrays.toString(Arrays.copyOfRange(scoreFlat, 0, Math.min(10, scoreFlat.length))));
            log.info("stride={}, bboxFlat前10个值: {}", stride,
                    Arrays.toString(Arrays.copyOfRange(bboxFlat, 0, Math.min(10, bboxFlat.length))));
            
            // 计算特征图尺寸
            int featureSize = inputSize / stride;
            int numAnchors = featureSize * featureSize * NUM_ANCHORS;
            
            // 确保不超过anchor数量
            int maxIdx = Math.min(scoreFlat.length, numAnchors);
            maxIdx = Math.min(maxIdx, centers.length);
            
            log.info("stride={}, featureSize={}, numAnchors={}, scoreFlat.length={}, bboxFlat.length={}, processing count={}", 
                    stride, featureSize, numAnchors, scoreFlat.length, bboxFlat.length, maxIdx);
            
            // 检查score值范围，判断是否已经sigmoid
            float minScore = Float.MAX_VALUE;
            float maxScore = Float.MIN_VALUE;
            for (int i = 0; i < Math.min(100, scoreFlat.length); i++) {
                minScore = Math.min(minScore, scoreFlat[i]);
                maxScore = Math.max(maxScore, scoreFlat[i]);
            }
            log.info("stride={}, score值范围（前100个采样）: min={}, max={}", stride, minScore, maxScore);
            
            // 如果score值已经在[0,1]范围，说明模型已经做了sigmoid，不需要再做
            boolean needSigmoid = (minScore < 0 || maxScore > 1.0);
            log.info("stride={}, 是否需要sigmoid: {}", stride, needSigmoid);
            
            int validCount = 0;
            int totalAboveThreshold = 0;
            
            // 遍历所有anchor
            for (int i = 0; i < maxIdx; i++) {
                // 获取分数
                float score = scoreFlat[i];
                
                // sigmoid激活（仅当需要时）
                if (needSigmoid) {
                    score = 1.0f / (1.0f + (float) Math.exp(-score));
                }
                
                if (score >= confThreshold) {
                    totalAboveThreshold++;
                    
                    // 打印前几个高置信度检测的详细信息
                    if (totalAboveThreshold <= 5) {
                        log.info("高置信度检测[{}]: i={}, 原始score={}, {}score={}, center=[{}, {}]", 
                                totalAboveThreshold, i, scoreFlat[i], 
                                needSigmoid ? "sigmoid后" : "直接使用", score, 
                                centers[i][0], centers[i][1]);
                    }
                }
                
                if (score < confThreshold) {
                    continue;
                }
                
                // 检查bbox数据是否足够
                if (i * 4 + 3 >= bboxFlat.length) {
                    log.warn("bbox数据不足: i={}, bboxFlat.length={}", i, bboxFlat.length);
                    break;
                }
                
                // 解码bbox
                float cx = centers[i][0];
                float cy = centers[i][1];
                
                // 获取bbox预测值 [left, top, right, bottom]
                float[] bboxPred = new float[]{
                    bboxFlat[i * 4],
                    bboxFlat[i * 4 + 1],
                    bboxFlat[i * 4 + 2],
                    bboxFlat[i * 4 + 3]
                };
                
                // 距离解码（SCRFD使用DFL - Distribution Focal Loss）
                float[] bbox = decodeBbox(bboxPred, cx, cy, stride);
                
                // 检查bbox有效性
                float width = bbox[2] - bbox[0];
                float height = bbox[3] - bbox[1];
                if (width <= 0 || height <= 0 || width > inputSize * 2 || height > inputSize * 2) {
                    if (totalAboveThreshold <= 5) {
                        log.debug("跳过无效bbox: width={}, height={}, bbox={}", width, height, Arrays.toString(bbox));
                    }
                    continue;  // 跳过无效的bbox
                }
                
                // 裁剪到图片范围内
                bbox[0] = Math.max(0, Math.min(bbox[0], inputSize));
                bbox[1] = Math.max(0, Math.min(bbox[1], inputSize));
                bbox[2] = Math.max(0, Math.min(bbox[2], inputSize));
                bbox[3] = Math.max(0, Math.min(bbox[3], inputSize));
                
                // 映射回原图
                float[] topLeft = ImageUtils.mapToOriginal(bbox[0], bbox[1], resizeResult);
                float[] bottomRight = ImageUtils.mapToOriginal(bbox[2], bbox[3], resizeResult);
                
                float[] originalBbox = new float[]{
                    topLeft[0], topLeft[1], bottomRight[0], bottomRight[1]
                };
                
                // 解码关键点
                Point[] landmarks = null;
                if (kpsFlat != null && i * 10 + 9 < kpsFlat.length) {
                    float[] kpsPred = new float[10];
                    System.arraycopy(kpsFlat, i * 10, kpsPred, 0, 10);
                    landmarks = decodeKeypoints(kpsPred, cx, cy, stride, resizeResult);
                }
                
                DetectionBox detBox = new DetectionBox();
                detBox.bbox = originalBbox;
                detBox.score = score;
                detBox.landmarks = landmarks;
                allBoxes.add(detBox);
                validCount++;
            }
            
            log.info("stride={}, 通过置信度阈值的数量: {}, 有效bbox数量: {}", 
                    stride, totalAboveThreshold, validCount);
            
        } catch (Exception e) {
            log.error("解析stride={}的输出异常", stride, e);
        }
    }
    
    /**
     * 将tensor数据展平为一维float数组
     */
    private float[] flattenToFloatArray(Object data, long[] shape) {
        if (data instanceof float[]) {
            return (float[]) data;
        } else if (data instanceof float[][]) {
            float[][] data2d = (float[][]) data;
            int totalSize = 0;
            for (float[] row : data2d) {
                totalSize += row.length;
            }
            float[] result = new float[totalSize];
            int idx = 0;
            for (float[] row : data2d) {
                System.arraycopy(row, 0, result, idx, row.length);
                idx += row.length;
            }
            return result;
        } else if (data instanceof float[][][]) {
            float[][][] data3d = (float[][][]) data;
            // 计算总大小
            int totalSize = 0;
            for (float[][] d2 : data3d) {
                for (float[] d1 : d2) {
                    totalSize += d1.length;
                }
            }
            float[] result = new float[totalSize];
            int idx = 0;
            for (float[][] d2 : data3d) {
                for (float[] d1 : d2) {
                    System.arraycopy(d1, 0, result, idx, d1.length);
                    idx += d1.length;
                }
            }
            return result;
        } else if (data instanceof float[][][][]) {
            float[][][][] data4d = (float[][][][]) data;
            // 计算总大小
            int totalSize = 0;
            for (float[][][] d3 : data4d) {
                for (float[][] d2 : d3) {
                    for (float[] d1 : d2) {
                        totalSize += d1.length;
                    }
                }
            }
            float[] result = new float[totalSize];
            int idx = 0;
            for (float[][][] d3 : data4d) {
                for (float[][] d2 : d3) {
                    for (float[] d1 : d2) {
                        System.arraycopy(d1, 0, result, idx, d1.length);
                        idx += d1.length;
                    }
                }
            }
            return result;
        }
        
        throw new RuntimeException("不支持的数据类型: " + data.getClass().getName());
    }
    
    /**
     * 解码边界框
     */
    private float[] decodeBbox(float[] pred, float cx, float cy, int stride) {
        // SCRFD使用distance格式: [left, top, right, bottom]
        float distance0 = pred[0] * stride;
        float distance1 = pred[1] * stride;
        float distance2 = pred[2] * stride;
        float distance3 = pred[3] * stride;
        
        float x1 = cx - distance0;
        float y1 = cy - distance1;
        float x2 = cx + distance2;
        float y2 = cy + distance3;
        
        return new float[]{x1, y1, x2, y2};
    }
    
    /**
     * 解码关键点
     */
    private Point[] decodeKeypoints(float[] pred, float cx, float cy, int stride,
                                   ImageUtils.ResizeResult resizeResult) {
        Point[] landmarks = new Point[5];
        
        for (int i = 0; i < 5; i++) {
            float x = cx + pred[i * 2] * stride;
            float y = cy + pred[i * 2 + 1] * stride;
            
            // 映射回原图
            float[] mapped = ImageUtils.mapToOriginal(x, y, resizeResult);
            landmarks[i] = new Point(mapped[0], mapped[1]);
        }
        
        return landmarks;
    }
    
    /**
     * 生成anchor中心点
     */
    private float[][] generateAnchorCenters(int inputSize, int stride) {
        int featureSize = inputSize / stride;
        List<float[]> centers = new ArrayList<>();
        
        for (int i = 0; i < featureSize; i++) {
            for (int j = 0; j < featureSize; j++) {
                for (int k = 0; k < NUM_ANCHORS; k++) {
                    float cx = (j + 0.5f) * stride;
                    float cy = (i + 0.5f) * stride;
                    centers.add(new float[]{cx, cy});
                }
            }
        }
        
        return centers.toArray(new float[0][]);
    }
    
    /**
     * NMS (Non-Maximum Suppression) 非极大值抑制
     */
    private List<DetectionBox> nms(List<DetectionBox> boxes, float iouThreshold) {
        if (boxes.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 按分数降序排序
        boxes.sort((a, b) -> Float.compare(b.score, a.score));
        
        List<DetectionBox> keep = new ArrayList<>();
        boolean[] suppressed = new boolean[boxes.size()];
        
        for (int i = 0; i < boxes.size(); i++) {
            if (suppressed[i]) {
                continue;
            }
            
            DetectionBox boxA = boxes.get(i);
            keep.add(boxA);
            
            // 抑制与当前框重叠度高的其他框
            for (int j = i + 1; j < boxes.size(); j++) {
                if (suppressed[j]) {
                    continue;
                }
                
                DetectionBox boxB = boxes.get(j);
                float iou = calculateIOU(boxA.bbox, boxB.bbox);
                
                if (iou > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        
        return keep;
    }
    
    /**
     * 计算IOU (Intersection over Union)
     */
    private float calculateIOU(float[] boxA, float[] boxB) {
        // 计算交集
        float x1 = Math.max(boxA[0], boxB[0]);
        float y1 = Math.max(boxA[1], boxB[1]);
        float x2 = Math.min(boxA[2], boxB[2]);
        float y2 = Math.min(boxA[3], boxB[3]);
        
        float intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        
        // 计算并集
        float boxAArea = (boxA[2] - boxA[0]) * (boxA[3] - boxA[1]);
        float boxBArea = (boxB[2] - boxB[0]) * (boxB[3] - boxB[1]);
        float unionArea = boxAArea + boxBArea - intersectionArea;
        
        return unionArea > 0 ? intersectionArea / unionArea : 0;
    }
    
    /**
     * 检测框数据结构
     */
    @Data
    @AllArgsConstructor
    private static class DetectionBox {
        float[] bbox;      // [x1, y1, x2, y2]
        float score;       // 置信度
        Point[] landmarks; // 5个关键点
        
        public DetectionBox() {
        }
        
        FaceDetectionResult toFaceDetectionResult() {
            return FaceDetectionResult.builder()
                    .bbox(bbox)
                    .confidence(score)
                    .landmarks(landmarks)
                    .build();
        }
    }
    
    @PreDestroy
    public void cleanup() throws Exception {
        if (session != null) {
            session.close();
        }
        log.info("ONNX人脸检测服务已关闭");
    }
}
