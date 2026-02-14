# 人脸识别服务

基于Spring Boot、InsightFace、OpenCV、ONNX Runtime和Milvus的人脸识别服务系统。

> **📌 项目说明**: 本项目完全由 AI 辅助生成，包括架构设计、代码实现、文档编写等全部内容。

## 技术栈

- **Spring Boot 3.2.0** - Java Web框架
- **ONNX Runtime 1.16.3** - 模型推理引擎 https://github.com/microsoft/onnxruntime
- **OpenCV 4.9.0** - 计算机视觉库 https://github.com/opencv/opencv
- **InsightFace** - 人脸检测和识别模型 https://github.com/deepinsight/insightface
- **Milvus 2.3.4** - 向量数据库
- **Java 17**、**Python 3.11** - 编程语言
- **Minio** - 对象存储

## 功能特性

✅ 人脸注册 - 支持Base64和文件上传两种方式  
✅ 人脸识别 - 1:N人脸搜索,返回相似度最高的Top-K结果  
✅ **OpenCV人脸对齐** - 使用OpenCV进行高精度人脸对齐，提升识别准确率  
✅ 人脸库管理 - 支持删除单个人脸，删除某人员的所有人脸，列表查看所有人员人脸  
✅ 高性能向量检索 - 基于Milvus实现毫秒级人脸搜索  
✅ ONNX模型推理 - 支持跨平台部署  
✅ 调试可视化 - 自动保存检测打点图和对齐结果图


## 环境要求

- Java 17+
- Maven 3.6+
- Docker & Docker Compose (用于运行Milvus)

## 快速开始

### 1. 下载InsightFace模型

从InsightFace官方或模型仓库下载ONNX格式的模型文件:

- **人脸检测模型**: `det_10g.onnx` (SCRFD检测器)  
  下载地址: https://github.com/deepinsight/insightface/tree/master/detection/scrfd

- **人脸识别模型**: `w600k_r50.onnx` (ArcFace特征提取)  
  下载地址: https://github.com/deepinsight/insightface/tree/master/recognition/arcface_torch

将模型文件放到项目根目录的 `models/` 文件夹下。

### 2. 启动Milvus向量数据库

使用Docker Compose启动Milvus:

```bash
docker-compose up -d
```

等待Milvus启动完成(约30秒)，可通过以下命令查看状态:

```bash
docker-compose ps
```

**Milvus认证**: 数据库已启用认证功能（默认用户名: `root`，密码: `Milvus`）。详见 [MILVUS_AUTH.md](MILVUS_AUTH.md)

### 3. 编译项目

```bash
mvn clean package -DskipTests
```

### 4. 运行服务

```bash
java -jar target/face-recognition-service-1.0.0.jar
```

服务将在 `http://localhost:8080/api` 启动。

## API接口

### 1. 健康检查

```http
GET /api/face/health
```

**响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": "服务正常"
}
```

### 2. 注册人脸 (Base64方式)

```http
POST /api/face/register
Content-Type: application/json

{
  "name": "张三",
  "personId": "P001",
  "imageBase64": "base64编码的图片数据...",
  "remark": "备注信息"
}
```

**响应示例:**
```json
{
  "code": 200,
  "message": "人脸注册成功",
  "data": "a1b2c3d4e5f6..."
}
```

### 3. 注册人脸 (文件上传方式)

```http
POST /api/face/register/upload
Content-Type: multipart/form-data

file: [图片文件]
name: 张三
personId: P001
remark: 备注信息
```

### 4. 识别人脸 (Base64方式)

```http
POST /api/face/recognize
Content-Type: application/json

{
  "imageBase64": "base64编码的图片数据...",
  "threshold": 0.6,
  "topK": 5
}
```

**响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "faceId": "a1b2c3d4e5f6...",
      "name": "张三",
      "personId": "P001",
      "similarity": 0.95,
      "remark": "备注信息"
    }
  ]
}
```

### 5. 识别人脸 (文件上传方式)

```http
POST /api/face/recognize/upload
Content-Type: multipart/form-data

file: [图片文件]
threshold: 0.6
topK: 5
```

### 6. 删除人脸

```http
DELETE /api/face/{faceId}
```

### 7. 删除人员所有人脸

```http
DELETE /api/face/person/{personId}
```

### 8. 列表查询人员

```http
GET /api/face/list?personId=P001
```

**响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "faceId": "a1b2c3d4e5f6...",
      "name": "张三",
      "personId": "P001",
      "remark": "备注信息",
      "registerTime": 1707800000000
    }
  ]
}
```

### 9. 重置人脸库

```http
POST /api/face/reset
```

**响应示例:**
```json
{
  "code": 200,
  "message": "人脸库重置成功",
  "data": true
}
```

## 配置说明

在 `src/main/resources/application.yml` 中可以配置:

- **服务端口**: `server.port`
- **ONNX模型路径**: `onnx.model.*`
- **线程池大小**: `onnx.thread-pool.*`
- **Milvus连接**: `milvus.host`, `milvus.port`
- **Milvus认证**: `milvus.username`, `milvus.password` (默认: root/Milvus)
- **人脸识别阈值**: `face.recognition.threshold` (0-1)
- **检测置信度**: `face.detection.confidence` (0-1)
- **返回结果数**: `face.recognition.top-k`

## 性能优化建议

1. **调整线程池大小**: 根据CPU核心数调整 `onnx.thread-pool.core-size`
2. **Milvus索引优化**: 大规模人脸库建议使用IVF_PQ索引
3. **图片预处理**: 上传前可压缩图片以减少网络传输时间
4. **批量识别**: 对于批量任务,可考虑异步处理

## 注意事项

⚠️ **模型文件**: 请确保下载正确的InsightFace ONNX模型  
⚠️ **内存要求**: 模型加载需要至少2GB内存  
⚠️ **Milvus版本**: 确保Milvus版本与SDK兼容  
⚠️ **Milvus认证**: 生产环境请务必修改默认密码
⚠️ **图片格式**: 支持JPG、PNG等常见格式  
⚠️ **人脸质量**: 建议使用清晰、正面的人脸图片以获得最佳识别效果

## Docker部署

构建Docker镜像:

```bash
mvn clean package -DskipTests
docker build -t face-recognition-service .
```

运行容器:

```bash
docker run -d \
  -p 8080:8080 \
  -v $(pwd)/models:/app/models \
  --name face-service \
  face-recognition-service
```

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源许可证。

### 使用的第三方组件

本项目使用了以下开源组件：

- **Spring Boot** - Apache License 2.0
- **OpenCV** - Apache License 2.0
- **ONNX Runtime** - MIT License
- **Milvus SDK** - Apache License 2.0
- **Apache Commons** - Apache License 2.0
- **Hutool** - Apache License 2.0 / Mulan PSL v2
- **Project Lombok** - MIT License
- **InsightFace Models** - MIT License（具体取决于模型）

详细的第三方组件版权信息请参阅 [NOTICE](NOTICE) 文件。

---

**© 2026 Face Recognition Service - AI Generated Project**

