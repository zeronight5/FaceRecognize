package com.facerecognition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 人脸识别服务主程序
 */
@Slf4j
@SpringBootApplication
public class FaceRecognitionApplication {
    
    public static void main(String[] args) {
        ConfigurableApplicationContext application = SpringApplication.run(FaceRecognitionApplication.class, args);
        
        try {
            Environment env = application.getEnvironment();
            String protocol = "http";
            String serverPort = env.getProperty("server.port");
            String contextPath = env.getProperty("server.servlet.context-path", "");
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            
            log.info("\n----------------------------------------------------------\n\t" +
                            "Application '{}' is running! Access URLs:\n\t" +
                            "Local: \t\t{}://localhost:{}{}\n\t" +
                            "External: \t{}://{}:{}{}\n\t" +
                            "Swagger UI: \t{}://localhost:{}{}/swagger-ui.html\n\t" +
                            "API Docs: \t{}://localhost:{}{}/v3/api-docs\n" +
                            "----------------------------------------------------------",
                    env.getProperty("spring.application.name"),
                    protocol,
                    serverPort,
                    contextPath,
                    protocol,
                    hostAddress,
                    serverPort,
                    contextPath,
                    protocol,
                    serverPort,
                    contextPath,
                    protocol,
                    serverPort,
                    contextPath
            );
            
        } catch (UnknownHostException e) {
            log.error("获取本机IP地址失败", e);
        }
    }
}
