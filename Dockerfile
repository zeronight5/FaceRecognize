FROM openjdk:17-jdk-slim

LABEL maintainer="face-recognition-service"

# 安装依赖
RUN apt-get update && apt-get install -y \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

# 复制JAR文件
COPY target/face-recognition-service-1.0.0.jar app.jar

# 创建目录
RUN mkdir -p /app/models /app/debug_output

# 暴露端口
EXPOSE 8080

# 运行应用
ENTRYPOINT ["java", "-jar", "app.jar"]
