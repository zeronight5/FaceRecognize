# 项目结构说明

```
detFace/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── facerecognition/
│       │           ├── FaceRecognitionApplication.java  # Spring Boot主程序
│       │           ├── config/                          # 配置类
│       │           │   ├── CorsConfig.java             # 跨域配置
│       │           │   ├── FaceConfig.java             # 人脸识别配置
│       │           │   ├── MilvusConfig.java           # Milvus配置
│       │           │   └── OnnxConfig.java             # ONNX配置
│       │           ├── controller/                      # REST控制器
│       │           │   └── FaceController.java         # 人脸识别API
│       │           ├── dto/                            # 数据传输对象
│       │           │   ├── ApiResponse.java            # API响应包装
│       │           │   ├── RecognizeRequest.java       # 识别请求
│       │           │   ├── RecognizeResult.java        # 识别结果
│       │           │   └── RegisterRequest.java        # 注册请求
│       │           ├── exception/                       # 异常处理
│       │           │   └── GlobalExceptionHandler.java # 全局异常处理器
│       │           ├── model/                          # 实体模型
│       │           │   ├── FaceDetectionResult.java    # 人脸检测结果
│       │           │   └── FaceInfo.java               # 人脸信息
│       │           ├── service/                        # 业务服务
│       │           │   ├── FaceAlignmentService.java   # OpenCV人脸对齐
│       │           │   ├── FaceDetectionService.java   # ONNX人脸检测
│       │           │   ├── FaceRecognitionService.java # ONNX特征提取
│       │           │   ├── FaceService.java            # 核心业务服务
│       │           │   └── MilvusService.java          # 向量数据库服务
│       │           └── util/                           # 工具类
│       │               └── ImageUtils.java             # 图片处理工具
│       └── resources/
│           └── application.yml                          # 应用配置文件
├── models/                                              # ONNX模型目录
│   ├── README.md                                        # 模型说明文档
│   ├── det_10g.onnx                                    # SCRFD检测模型（需下载）
│   └── w600k_r50.onnx                                  # ArcFace识别模型（需下载）
├── debug_output/                                        # 调试输出目录
│   ├── {faceId}_detection.jpg                          # 检测标注图
│   └── {faceId}_aligned.jpg                            # 对齐人脸图
├── docker-compose.yml                                   # Milvus Docker配置
├── Dockerfile                                           # 应用Docker镜像
├── pom.xml                                             # Maven项目配置
├── .gitignore                                          # Git忽略文件
├── README.md                                           # 项目说明文档
├── QUICKSTART.md                                       # 快速开始指南
└── PROJECT_STRUCTURE.md                                # 本文件
```

## 核心组件说明

### 1. 图片处理流程

```
原始图片 (Base64/Bytes)
    ↓
ImageUtils.decode() - 解码为OpenCV Mat (BGR格式)
    ↓
ImageUtils.letterboxResize() - 保持宽高比缩放（避免拉伸）
    ↓
ImageUtils.matToOnnxInput() - 转换为ONNX输入格式 (RGB, CHW, 归一化)
    ↓
ONNX模型推理
```

### 2. 人脸注册流程

```
1. FaceController.registerFace()
   ↓
2. ImageUtils.decodeBase64ToMat() - 图片解码
   ↓
3. FaceDetectionService.detectFaces() - 人脸检测
   ↓
4. FaceAlignmentService.alignFace() - 人脸对齐 (仿射变换)
   ↓
5. FaceRecognitionService.extractFeature() - 特征提取 (512维向量)
   ↓
6. MilvusService.insertFace() - 向量存储
   ↓
7. 返回 faceId
```

### 3. 人脸识别流程

```
1. FaceController.recognizeFace()
   ↓
2. ImageUtils.decodeBase64ToMat() - 图片解码
   ↓
3. FaceDetectionService.detectFaces() - 人脸检测
   ↓
4. FaceAlignmentService.alignFace() - 人脸对齐
   ↓
5. FaceRecognitionService.extractFeature() - 特征提取
   ↓
6. MilvusService.searchSimilarFaces() - 向量检索
   ↓
7. 相似度过滤 (threshold)
   ↓
8. 返回 Top-K 结果
```

## 关键技术实现

### 图片格式统一

**问题**: OpenCV使用BGR格式，ONNX模型通常使用RGB格式

**解决方案**: `ImageUtils.matToOnnxInput()` 自动进行BGR→RGB转换

```java
// BGR -> RGB 通道反转
int rgbChannel = 2 - c;  // c=0→2, c=1→1, c=2→0
```

### 画面拉伸处理

**问题**: 直接缩放图片会导致人脸变形，影响识别准确率

**解决方案**: `ImageUtils.letterboxResize()` 使用Letterbox方式保持宽高比

```java
// 计算缩放比例（取较小值）
float scale = Math.min(targetSize / width, targetSize / height);

// 缩放后居中放置，周围填充灰色
```

### 人脸对齐

**问题**: 人脸可能存在旋转、缩放、平移，影响识别

**解决方案**: 使用5点关键点进行仿射变换

```java
// 计算相似变换矩阵
Mat transformMatrix = estimateSimilarityTransform(srcLandmarks, stdLandmarks);

// 应用仿射变换
Imgproc.warpAffine(image, alignedFace, transformMatrix, outputSize);
```

### 向量检索优化

**索引类型选择**:
- 小规模 (<1万): FLAT (暴力搜索)
- 中等规模 (1-10万): IVF_FLAT
- 大规模 (>10万): IVF_PQ

**度量类型选择**:
- COSINE: 余弦相似度（推荐，归一化后等价于IP）
- IP: 内积（速度快）
- L2: 欧氏距离（直观）

## 配置优化建议

### CPU密集型场景
```yaml
onnx:
  thread-pool:
    core-size: 8  # 增加线程数
    max-size: 16
```

### 内存受限场景
```yaml
milvus:
  collection:
    index-type: IVF_SQ8  # 使用标量量化节省内存
```

### 高并发场景
```yaml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 50
```

## 扩展开发

### 添加批量识别API

```java
@PostMapping("/recognize/batch")
public ApiResponse<List<List<RecognizeResult>>> recognizeBatch(
        @RequestBody List<RecognizeRequest> requests) {
    // 实现批量识别逻辑
}
```

### 添加人脸质量评估

```java
public float assessFaceQuality(Mat face) {
    // 评估模糊度、光照、角度等
    return quality;
}
```

### 添加活体检测

需要额外的活体检测模型（如Silent-Face-Anti-Spoofing）

```java
public boolean isLiveFace(Mat face) {
    // 调用活体检测模型
    return isLive;
}
```

## 技术栈版本

- Spring Boot: 3.2.0
- OpenCV: 4.9.0
- ONNX Runtime: 1.16.3
- Milvus SDK: 2.3.4
- Java: 17
- InsightFace: Latest ONNX models

## 性能指标参考

**硬件**: Intel i7-10700K, 16GB RAM

- 人脸检测: ~50ms
- 人脸对齐: ~10ms
- 特征提取: ~30ms
- 向量检索 (1万人脸库): ~5ms
- **总计**: ~100ms/张图片

**注**: 实际性能取决于硬件配置和模型大小
