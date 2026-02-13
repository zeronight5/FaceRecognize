# 模型文件目录

请将以下ONNX模型文件放置在此目录下：

## 1. 人脸检测模型

**文件名**: `det_10g.onnx`

**模型**: SCRFD-10G

**下载地址**: 
- https://github.com/deepinsight/insightface/tree/master/detection/scrfd
- 或从Google Drive/百度网盘下载预训练模型

**说明**: 
- SCRFD是高效的人脸检测模型，支持多尺度人脸检测
- 输出包含人脸边界框、置信度和5个关键点

## 2. 人脸识别模型

**文件名**: `w600k_r50.onnx`

**模型**: ArcFace ResNet-50

**下载地址**: 
- https://github.com/deepinsight/insightface/tree/master/recognition/arcface_torch
- 或从官方模型库下载

**说明**: 
- ArcFace是先进的人脸识别模型，输出512维特征向量
- 训练集: WebFace600K
- 骨干网络: ResNet-50

## 模型转换

如果只有PyTorch模型（.pth），需要先转换为ONNX格式：

```python
import torch
import onnx

# 加载PyTorch模型
model = torch.load('model.pth')
model.eval()

# 准备输入样例
dummy_input = torch.randn(1, 3, 112, 112)

# 导出为ONNX
torch.onnx.export(
    model,
    dummy_input,
    "model.onnx",
    input_names=['input'],
    output_names=['output'],
    dynamic_axes={'input': {0: 'batch_size'}, 'output': {0: 'batch_size'}}
)
```

## 验证模型

使用ONNX Runtime验证模型是否正常：

```python
import onnxruntime as ort
import numpy as np

session = ort.InferenceSession("det_10g.onnx")

# 打印输入输出信息
print("输入:")
for input in session.get_inputs():
    print(f"  {input.name}: {input.shape} ({input.type})")

print("输出:")
for output in session.get_outputs():
    print(f"  {output.name}: {output.shape} ({output.type})")
```

## 注意事项

⚠️ **模型文件较大**: 单个模型文件可能达到几百MB，请确保有足够的磁盘空间

⚠️ **版本兼容性**: 确保ONNX模型与ONNX Runtime版本兼容（本项目使用1.16.3）

⚠️ **授权协议**: 使用InsightFace模型需遵守相应的开源协议
