package com.facerecognition.controller;

import com.facerecognition.dto.ApiResponse;
import com.facerecognition.dto.RecognizeRequest;
import com.facerecognition.dto.RecognizeResult;
import com.facerecognition.dto.RegisterRequest;
import com.facerecognition.model.FaceInfo;
import com.facerecognition.service.FaceService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 人脸识别REST API控制器
 */
@Slf4j
@RestController
@RequestMapping("/face")
public class FaceController {
    
    @Autowired
    private FaceService faceService;
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("服务正常");
    }
    
    /**
     * 注册人脸 (Base64方式)
     */
    @PostMapping("/register")
    public ApiResponse<String> registerFace(@Valid @RequestBody RegisterRequest request) {
        try {
            log.info("注册人脸请求: name={}, personId={}", request.getName(), request.getPersonId());
            
            String faceId = faceService.registerFace(
                    request.getName(),
                    request.getPersonId(),
                    request.getImageBase64(),
                    request.getRemark()
            );
            
            return ApiResponse.success("人脸注册成功", faceId);
            
        } catch (Exception e) {
            log.error("注册人脸失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 注册人脸 (文件上传方式)
     */
    @PostMapping("/register/upload")
    public ApiResponse<String> registerFaceUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("personId") String personId,
            @RequestParam(value = "remark", required = false) String remark) {
        try {
            log.info("注册人脸请求(上传): name={}, personId={}, fileName={}", 
                    name, personId, file.getOriginalFilename());
            
            // 检查文件是否为空
            if (file.isEmpty()) {
                return ApiResponse.error("文件不能为空");
            }
            
            // 检查文件大小
            if (file.getSize() > 10 * 1024 * 1024) {
                return ApiResponse.error("文件大小不能超过10MB");
            }
            
            byte[] imageBytes = file.getBytes();
            
            String faceId = faceService.registerFaceFromBytes(
                    name,
                    personId,
                    imageBytes,
                    remark
            );
            
            return ApiResponse.success("人脸注册成功", faceId);
            
        } catch (Exception e) {
            log.error("注册人脸失败(上传)", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 识别人脸 (Base64方式)
     */
    @PostMapping("/recognize")
    public ApiResponse<List<RecognizeResult>> recognizeFace(@Valid @RequestBody RecognizeRequest request) {
        try {
            log.info("识别人脸请求: threshold={}, topK={}", request.getThreshold(), request.getTopK());
            
            List<RecognizeResult> results = faceService.recognizeFace(
                    request.getImageBase64(),
                    request.getThreshold(),
                    request.getTopK()
            );
            
            return ApiResponse.success(results);
            
        } catch (Exception e) {
            log.error("识别人脸失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 识别人脸 (文件上传方式)
     */
    @PostMapping("/recognize/upload")
    public ApiResponse<List<RecognizeResult>> recognizeFaceUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "threshold", required = false, defaultValue = "0.6") Float threshold,
            @RequestParam(value = "topK", required = false, defaultValue = "5") Integer topK) {
        try {
            log.info("识别人脸请求(上传): threshold={}, topK={}, fileName={}", 
                    threshold, topK, file.getOriginalFilename());
            
            // 检查文件是否为空
            if (file.isEmpty()) {
                return ApiResponse.error("文件不能为空");
            }
            
            // 检查文件大小
            if (file.getSize() > 10 * 1024 * 1024) {
                return ApiResponse.error("文件大小不能超过10MB");
            }
            
            byte[] imageBytes = file.getBytes();
            
            List<RecognizeResult> results = faceService.recognizeFaceFromBytes(
                    imageBytes,
                    threshold,
                    topK
            );
            
            return ApiResponse.success(results);
            
        } catch (Exception e) {
            log.error("识别人脸失败(上传)", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 删除人脸
     */
    @DeleteMapping("/{faceId}")
    public ApiResponse<Boolean> deleteFace(@PathVariable String faceId) {
        try {
            log.info("删除人脸请求: faceId={}", faceId);
            
            boolean success = faceService.deleteFace(faceId);
            
            if (success) {
                return ApiResponse.success("人脸删除成功", true);
            } else {
                return ApiResponse.error("人脸删除失败");
            }
            
        } catch (Exception e) {
            log.error("删除人脸失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 删除人员所有人脸
     */
    @DeleteMapping("/person/{personId}")
    public ApiResponse<Boolean> deletePersonFaces(@PathVariable String personId) {
        try {
            log.info("删除人员所有人脸请求: personId={}", personId);
            
            boolean success = faceService.deletePersonFaces(personId);
            
            if (success) {
                return ApiResponse.success("人员人脸删除成功", true);
            } else {
                return ApiResponse.error("人员人脸删除失败");
            }
            
        } catch (Exception e) {
            log.error("删除人员人脸失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 重置人脸库
     */
    @PostMapping("/reset")
    public ApiResponse<Boolean> resetDatabase() {
        try {
            log.info("重置人脸库请求");
            
            boolean success = faceService.resetDatabase();
            
            if (success) {
                return ApiResponse.success("人脸库重置成功", true);
            } else {
                return ApiResponse.error("人脸库重置失败");
            }
            
        } catch (Exception e) {
            log.error("重置人脸库失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 查询人脸列表
     * 支持按personId或name过滤
     */
    @GetMapping("/list")
    public ApiResponse<List<FaceInfo>> listFaces(
            @RequestParam(value = "personId", required = false) String personId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        try {
            log.info("查询人脸列表: personId={}, name={}, limit={}", personId, name, limit);
            
            List<FaceInfo> faceInfoList;
            
            if (personId != null && !personId.isEmpty()) {
                // 按personId查询
                faceInfoList = faceService.queryByPersonId(personId);
            } else if (name != null && !name.isEmpty()) {
                // 按name查询
                faceInfoList = faceService.queryByName(name);
            } else {
                // 查询所有
                faceInfoList = faceService.listAllFaces(limit);
            }
            
            return ApiResponse.success(faceInfoList);
            
        } catch (Exception e) {
            log.error("查询人脸列表失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 根据person_id查询人脸
     */
    @GetMapping("/person/{personId}")
    public ApiResponse<List<FaceInfo>> queryByPersonId(@PathVariable String personId) {
        try {
            log.info("根据personId查询人脸: {}", personId);
            
            List<FaceInfo> faceInfoList = faceService.queryByPersonId(personId);
            
            return ApiResponse.success(faceInfoList);
            
        } catch (Exception e) {
            log.error("根据personId查询人脸失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 调试接口：测试人脸检测（仅用于开发调试）
     */
    @PostMapping("/debug/detect")
    public ApiResponse<String> debugDetect(@RequestParam("file") MultipartFile file) {
        try {
            log.info("调试接口：人脸检测测试");
            
            if (file.isEmpty()) {
                return ApiResponse.error("文件不能为空");
            }
            
            byte[] imageBytes = file.getBytes();
            
            // 仅执行检测，查看日志输出
            List<RecognizeResult> results = faceService.recognizeFaceFromBytes(
                    imageBytes, 0.6f, 10
            );
            
            String message = String.format("检测完成，找到 %d 个人脸。请查看控制台日志了解详细信息。", 
                    results.size());
            
            return ApiResponse.success(message, message);
            
        } catch (Exception e) {
            log.error("调试检测失败", e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
