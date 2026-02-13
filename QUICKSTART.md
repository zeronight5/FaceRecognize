# 快速开始指南

本指南将帮助您快速部署和使用人脸识别服务。

## 前置要求

- Java 17+
- Maven 3.6+
- Docker & Docker Compose
- InsightFace ONNX模型文件

## 步骤1：准备模型文件

1. 创建 `models` 目录（如果不存在）
2. 下载以下模型文件并放入 `models` 目录：

### 人脸检测模型
- **文件名**: `det_10g.onnx`
- **下载**: https://github.com/deepinsight/insightface/tree/master/detection/scrfd

### 人脸识别模型
- **文件名**: `w600k_r50.onnx`
- **下载**: https://github.com/deepinsight/insightface/tree/master/recognition/arcface_torch

详细模型说明请参考 `models/README.md`

## 步骤2：启动Milvus向量数据库

```bash
# Windows PowerShell
docker-compose up -d

# 等待30秒让Milvus完全启动

# 检查状态
docker-compose ps
```

所有容器应该显示为 `Up` 状态。

**重要提示**: Milvus 已启用认证功能
- 默认用户名: `root`
- 默认密码: `Milvus`
- 详细配置说明请参考 [MILVUS_AUTH.md](MILVUS_AUTH.md)
- **生产环境请务必修改默认密码！**

## 步骤3：编译项目

```bash
mvn clean package -DskipTests
```

编译成功后会在 `target` 目录生成 `face-recognition-service-1.0.0.jar`

## 步骤4：运行服务

```bash
java -jar target/face-recognition-service-1.0.0.jar
```

服务启动后会显示以下信息：
```
Application 'face-recognition-service' is running! Access URLs:
Local:      http://localhost:8080/api
External:   http://192.168.x.x:8080/api
```

## 步骤5：测试API

### 健康检查

```bash
curl http://localhost:8080/api/face/health
```

### 注册人脸（Base64方式）

```bash
curl -X POST http://localhost:8080/api/face/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "张三",
    "personId": "P001",
    "imageBase64": "base64编码的图片...",
    "remark": "测试用户"
  }'
```

### 注册人脸（文件上传方式）

```bash
# Windows PowerShell
$response = Invoke-WebRequest -Uri "http://localhost:8080/api/face/register/upload" `
  -Method POST `
  -Form @{
    file = Get-Item "path\to\image.jpg"
    name = "张三"
    personId = "P001"
    remark = "测试用户"
  }
$response.Content
```

### 识别人脸（Base64方式）

```bash
curl -X POST http://localhost:8080/api/face/recognize \
  -H "Content-Type: application/json" \
  -d '{
    "imageBase64": "base64编码的图片...",
    "threshold": 0.6,
    "topK": 5
  }'
```

### 识别人脸（文件上传方式）

```bash
# Windows PowerShell
$response = Invoke-WebRequest -Uri "http://localhost:8080/api/face/recognize/upload" `
  -Method POST `
  -Form @{
    file = Get-Item "path\to\image.jpg"
    threshold = "0.6"
    topK = "5"
  }
$response.Content
```

### 删除人脸

```bash
curl -X DELETE http://localhost:8080/api/face/{faceId}
```

### 删除人员所有人脸

```bash
curl -X DELETE http://localhost:8080/api/face/person/{personId}
```

### 重置人脸库

```bash
curl -X POST http://localhost:8080/api/face/reset
```

## 调试功能

服务启动后会在项目根目录创建 `debug_output` 文件夹，保存以下调试图片：

- `{faceId}_detection.jpg` - 人脸检测标注图（显示边界框和关键点）
- `{faceId}_aligned.jpg` - 对齐后的人脸图片（112x112）

这些图片可以帮助您验证人脸检测和对齐是否正确。

## 配置说明

主要配置在 `src/main/resources/application.yml`：

### 人脸检测配置
```yaml
face:
  detection:
    confidence: 0.5      # 检测置信度阈值
    input-size: 640      # 输入图片尺寸
    min-face-size: 20    # 最小人脸尺寸
```

### 人脸识别配置
```yaml
face:
  recognition:
    threshold: 0.6       # 识别相似度阈值
    top-k: 5            # 返回Top-K结果
```

### Milvus配置
```yaml
milvus:
  host: localhost
  port: 19530
  username: root
  password: Milvus
  collection:
    name: face_vectors
    dimension: 512
    index-type: IVF_FLAT
    metric-type: COSINE
```

## 常见问题

### 1. OpenCV库加载失败

确保已正确安装OpenCV依赖。项目使用 `org.openpnp:opencv` 会自动下载本地库。

### 2. ONNX模型加载失败

- 检查模型文件路径是否正确
- 确保模型文件完整未损坏
- 验证ONNX Runtime版本兼容性

### 3. Milvus连接失败

- 检查Docker容器是否正常运行
- 验证端口19530是否被占用
- 查看Milvus日志: `docker logs milvus-standalone`

### 4. 未检测到人脸

- 确保图片中有清晰的正面人脸
- 降低检测置信度阈值（face.detection.confidence）
- 检查debug_output中的检测标注图

### 5. 识别准确率低

- 使用高质量的人脸图片注册
- 调整识别阈值（face.recognition.threshold）
- 确保光照良好、人脸清晰

## 性能优化

### 调整线程池大小
```yaml
onnx:
  thread-pool:
    core-size: 4    # 根据CPU核心数调整
    max-size: 8
```

### Milvus索引优化

对于大规模人脸库（>10万），建议使用IVF_PQ索引：
```yaml
milvus:
  collection:
    index-type: IVF_PQ
    nlist: 2048
    nprobe: 32
```

## Docker部署

### 构建镜像
```bash
mvn clean package -DskipTests
docker build -t face-recognition-service .
```

### 运行容器
```bash
docker run -d \
  -p 8080:8080 \
  -v ${PWD}/models:/app/models \
  -v ${PWD}/debug_output:/app/debug_output \
  --name face-service \
  face-recognition-service
```

## 下一步

- 查看 [API文档](README.md#api接口) 了解完整的接口说明
- 阅读 [models/README.md](models/README.md) 了解模型详情
- 根据实际需求调整配置参数
- 实现完整的SCRFD后处理逻辑以提高检测准确率

## 获取帮助

如有问题，请检查日志输出或提交Issue。
