package com.facerecognition.service;

import com.facerecognition.config.MilvusConfig;
import com.facerecognition.model.FaceInfo;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

/**
 * Milvus向量数据库服务
 * 负责人脸向量的存储和检索
 */
@Slf4j
@Service
public class MilvusService {
    
    @Autowired
    private MilvusConfig milvusConfig;
    
    private MilvusServiceClient milvusClient;
    
    private static final String FIELD_FACE_ID = "face_id";
    private static final String FIELD_PERSON_ID = "person_id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_FEATURE = "feature";
    private static final String FIELD_REMARK = "remark";
    private static final String FIELD_REGISTER_TIME = "register_time";
    
    @PostConstruct
    public void init() {
        log.info("初始化Milvus连接...");
        
        try {
            // 创建Milvus客户端
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(milvusConfig.getHost())
                    .withPort(milvusConfig.getPort())
                    .withAuthorization(milvusConfig.getUsername(), milvusConfig.getPassword())
                    .build();
            
            milvusClient = new MilvusServiceClient(connectParam);
            
            log.info("Milvus连接成功: {}:{}", milvusConfig.getHost(), milvusConfig.getPort());
            
            // 创建集合（如果不存在）
            createCollectionIfNotExists();
            
        } catch (Exception e) {
            log.error("Milvus初始化失败", e);
            throw new RuntimeException("Milvus初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建集合（如果不存在）
     */
    private void createCollectionIfNotExists() {
        String collectionName = milvusConfig.getCollection().getName();
        
        // 检查集合是否存在
        R<Boolean> hasCollectionResp = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );
        
        if (hasCollectionResp.getData()) {
            log.info("Milvus集合已存在: {}", collectionName);
            
            // 加载集合到内存
            milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            return;
        }
        
        // 创建集合Schema
        log.info("创建Milvus集合: {}", collectionName);
        
        FieldType faceIdField = FieldType.newBuilder()
                .withName(FIELD_FACE_ID)
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
        
        FieldType personIdField = FieldType.newBuilder()
                .withName(FIELD_PERSON_ID)
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build();
        
        FieldType nameField = FieldType.newBuilder()
                .withName(FIELD_NAME)
                .withDataType(DataType.VarChar)
                .withMaxLength(128)
                .build();
        
        FieldType featureField = FieldType.newBuilder()
                .withName(FIELD_FEATURE)
                .withDataType(DataType.FloatVector)
                .withDimension(milvusConfig.getCollection().getDimension())
                .build();
        
        FieldType remarkField = FieldType.newBuilder()
                .withName(FIELD_REMARK)
                .withDataType(DataType.VarChar)
                .withMaxLength(256)
                .build();
        
        FieldType registerTimeField = FieldType.newBuilder()
                .withName(FIELD_REGISTER_TIME)
                .withDataType(DataType.Int64)
                .build();
        
        CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("人脸特征向量集合")
                .withShardsNum(2)
                .addFieldType(faceIdField)
                .addFieldType(personIdField)
                .addFieldType(nameField)
                .addFieldType(featureField)
                .addFieldType(remarkField)
                .addFieldType(registerTimeField)
                .build();
        
        R<RpcStatus> createCollectionResp = milvusClient.createCollection(createCollectionReq);
        
        if (createCollectionResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建集合失败: " + createCollectionResp.getMessage());
        }
        
        log.info("Milvus集合创建成功: {}", collectionName);
        
        // 创建向量索引
        createIndex();
        
        // 加载集合到内存
        milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );
        
        log.info("Milvus集合已加载到内存");
    }
    
    /**
     * 创建向量索引
     */
    private void createIndex() {
        String collectionName = milvusConfig.getCollection().getName();
        String indexType = milvusConfig.getCollection().getIndexType();
        String metricType = milvusConfig.getCollection().getMetricType();
        
        log.info("创建向量索引: indexType={}, metricType={}", indexType, metricType);
        
        String indexParamsJson = "{\"nlist\":" + milvusConfig.getCollection().getNlist() + "}";
        
        CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_FEATURE)
                .withIndexType(IndexType.valueOf(indexType))
                .withMetricType(MetricType.valueOf(metricType))
                .withExtraParam(indexParamsJson)
                .withSyncMode(Boolean.TRUE)
                .build();
        
        R<RpcStatus> createIndexResp = milvusClient.createIndex(createIndexParam);
        
        if (createIndexResp.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建索引失败: " + createIndexResp.getMessage());
        }
        
        log.info("向量索引创建成功");
    }
    
    /**
     * 插入人脸向量
     * 
     * @param faceInfo 人脸信息
     * @return 是否成功
     */
    public boolean insertFace(FaceInfo faceInfo) {
        try {
            String collectionName = milvusConfig.getCollection().getName();
            
            List<String> faceIds = Collections.singletonList(faceInfo.getFaceId());
            List<String> personIds = Collections.singletonList(faceInfo.getPersonId());
            List<String> names = Collections.singletonList(faceInfo.getName());
            List<List<Float>> features = Collections.singletonList(toFloatList(faceInfo.getFeature()));
            List<String> remarks = Collections.singletonList(faceInfo.getRemark() != null ? faceInfo.getRemark() : "");
            List<Long> registerTimes = Collections.singletonList(faceInfo.getRegisterTime());
            
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field(FIELD_FACE_ID, faceIds));
            fields.add(new InsertParam.Field(FIELD_PERSON_ID, personIds));
            fields.add(new InsertParam.Field(FIELD_NAME, names));
            fields.add(new InsertParam.Field(FIELD_FEATURE, features));
            fields.add(new InsertParam.Field(FIELD_REMARK, remarks));
            fields.add(new InsertParam.Field(FIELD_REGISTER_TIME, registerTimes));
            
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();
            
            R<MutationResult> insertResp = milvusClient.insert(insertParam);
            
            if (insertResp.getStatus() != R.Status.Success.getCode()) {
                log.error("插入人脸向量失败: {}", insertResp.getMessage());
                return false;
            }
            
            log.info("人脸向量插入成功: faceId={}, personId={}", faceInfo.getFaceId(), faceInfo.getPersonId());
            return true;
            
        } catch (Exception e) {
            log.error("插入人脸向量异常", e);
            return false;
        }
    }
    
    /**
     * 搜索相似人脸
     * 
     * @param feature 查询特征向量
     * @param topK 返回前K个结果
     * @return 搜索结果列表（包含相似度分数）
     */
    public List<SearchResult> searchSimilarFaces(float[] feature, int topK) {
        try {
            String collectionName = milvusConfig.getCollection().getName();
            
            List<List<Float>> searchVectors = Collections.singletonList(toFloatList(feature));
            
            String searchParamsJson = "{\"nprobe\":" + milvusConfig.getCollection().getNprobe() + "}";
            
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.valueOf(milvusConfig.getCollection().getMetricType()))
                    .withOutFields(Arrays.asList(FIELD_FACE_ID, FIELD_PERSON_ID, FIELD_NAME, FIELD_REMARK))
                    .withTopK(topK)
                    .withVectors(searchVectors)
                    .withVectorFieldName(FIELD_FEATURE)
                    .withParams(searchParamsJson)
                    .build();
            
            R<SearchResults> searchResp = milvusClient.search(searchParam);
            
            if (searchResp.getStatus() != R.Status.Success.getCode()) {
                log.error("搜索人脸向量失败: {}", searchResp.getMessage());
                return Collections.emptyList();
            }
            
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResp.getData().getResults());
            
            List<SearchResult> results = new ArrayList<>();
            
            List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
            
            for (int i = 0; i < idScores.size(); i++) {
                SearchResultsWrapper.IDScore idScore = idScores.get(i);
                
                // 获取字段值
                Object faceIdObj = idScore.get(FIELD_FACE_ID);
                Object personIdObj = idScore.get(FIELD_PERSON_ID);
                Object nameObj = idScore.get(FIELD_NAME);
                Object remarkObj = idScore.get(FIELD_REMARK);
                
                FaceInfo faceInfo = FaceInfo.builder()
                        .faceId(faceIdObj != null ? faceIdObj.toString() : "")
                        .personId(personIdObj != null ? personIdObj.toString() : "")
                        .name(nameObj != null ? nameObj.toString() : "")
                        .remark(remarkObj != null ? remarkObj.toString() : "")
                        .build();
                
                // 获取相似度分数
                float score = idScore.getScore();
                
                // COSINE相似度转换：Milvus返回的是距离，需要转换为相似度
                // 对于COSINE距离，相似度 = (1 + distance) / 2 或 1 - distance
                float similarity = convertScoreToSimilarity(score);
                
                SearchResult searchResult = new SearchResult();
                searchResult.faceInfo = faceInfo;
                searchResult.similarity = similarity;
                
                results.add(searchResult);
            }
            
            log.debug("搜索到 {} 个相似人脸", results.size());
            
            return results;
            
        } catch (Exception e) {
            log.error("搜索人脸向量异常", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 将Milvus距离分数转换为相似度
     * 
     * @param score Milvus返回的距离分数
     * @return 相似度 (0-1，越大越相似)
     */
    private float convertScoreToSimilarity(float score) {
        String metricType = milvusConfig.getCollection().getMetricType();
        
        if ("COSINE".equals(metricType)) {
            // COSINE相似度直接就是[-1, 1]，转换到[0, 1]
            return (score + 1.0f) / 2.0f;
        } else if ("IP".equals(metricType)) {
            // 内积，分数越大越相似，已经是相似度
            return score;
        } else if ("L2".equals(metricType)) {
            // L2距离，距离越小越相似，转换为相似度
            return 1.0f / (1.0f + score);
        }
        
        return score;
    }
    
    /**
     * 搜索结果包装类
     */
    public static class SearchResult {
        public FaceInfo faceInfo;
        public float similarity;
    }
    
    /**
     * 删除人脸
     * 
     * @param faceId 人脸ID
     * @return 是否成功
     */
    public boolean deleteFace(String faceId) {
        try {
            String collectionName = milvusConfig.getCollection().getName();
            String expr = FIELD_FACE_ID + " == \"" + faceId + "\"";
            
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();
            
            R<MutationResult> deleteResp = milvusClient.delete(deleteParam);
            
            if (deleteResp.getStatus() != R.Status.Success.getCode()) {
                log.error("删除人脸失败: {}", deleteResp.getMessage());
                return false;
            }
            
            log.info("人脸删除成功: faceId={}", faceId);
            return true;
            
        } catch (Exception e) {
            log.error("删除人脸异常", e);
            return false;
        }
    }
    
    /**
     * 删除某人员的所有人脸
     * 
     * @param personId 人员ID
     * @return 是否成功
     */
    public boolean deletePersonFaces(String personId) {
        try {
            String collectionName = milvusConfig.getCollection().getName();
            String expr = FIELD_PERSON_ID + " == \"" + personId + "\"";
            
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();
            
            R<MutationResult> deleteResp = milvusClient.delete(deleteParam);
            
            if (deleteResp.getStatus() != R.Status.Success.getCode()) {
                log.error("删除人员人脸失败: {}", deleteResp.getMessage());
                return false;
            }
            
            log.info("人员人脸删除成功: personId={}", personId);
            return true;
            
        } catch (Exception e) {
            log.error("删除人员人脸异常", e);
            return false;
        }
    }
    
    /**
     * 重置人脸库（删除所有数据）
     * 
     * @return 是否成功
     */
    public boolean resetDatabase() {
        try {
            String collectionName = milvusConfig.getCollection().getName();
            
            // 删除集合
            R<RpcStatus> dropResp = milvusClient.dropCollection(
                    DropCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            
            if (dropResp.getStatus() != R.Status.Success.getCode()) {
                log.error("删除集合失败: {}", dropResp.getMessage());
                return false;
            }
            
            // 重新创建集合
            createCollectionIfNotExists();
            
            log.info("人脸库重置成功");
            return true;
            
        } catch (Exception e) {
            log.error("重置人脸库异常", e);
            return false;
        }
    }
    
    /**
     * 查询所有人脸
     * 
     * @param limit 最大返回数量
     * @return 人脸信息列表
     */
    public List<FaceInfo> queryAllFaces(int limit) {
        try {
            String collectionName = milvusConfig.getCollection().getName();
            
            List<String> outFields = Arrays.asList(
                    FIELD_FACE_ID, FIELD_PERSON_ID, FIELD_NAME, 
                    FIELD_REMARK, FIELD_REGISTER_TIME
            );
            
            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr("")  // 空表达式表示查询所有
                    .withOutFields(outFields)
                    .withLimit((long) limit)
                    .build();
            
            R<QueryResults> queryResp = milvusClient.query(queryParam);
            
            if (queryResp.getStatus() != R.Status.Success.getCode()) {
                log.error("查询人脸失败: {}", queryResp.getMessage());
                return Collections.emptyList();
            }
            
            return parseFaceInfoList(queryResp.getData());
            
        } catch (Exception e) {
            log.error("查询所有人脸异常", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 根据person_id查询人脸
     * 
     * @param personId 人员ID
     * @return 人脸信息列表
     */
    public List<FaceInfo> queryFacesByPersonId(String personId) {
        try {
            String collectionName = milvusConfig.getCollection().getName();
            
            List<String> outFields = Arrays.asList(
                    FIELD_FACE_ID, FIELD_PERSON_ID, FIELD_NAME, 
                    FIELD_REMARK, FIELD_REGISTER_TIME
            );
            
            String expr = FIELD_PERSON_ID + " == \"" + personId + "\"";
            
            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .withOutFields(outFields)
                    .build();
            
            R<QueryResults> queryResp = milvusClient.query(queryParam);
            
            if (queryResp.getStatus() != R.Status.Success.getCode()) {
                log.error("查询人脸失败: {}", queryResp.getMessage());
                return Collections.emptyList();
            }
            
            return parseFaceInfoList(queryResp.getData());
            
        } catch (Exception e) {
            log.error("根据personId查询人脸异常", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 根据姓名查询人脸
     * 
     * @param name 姓名
     * @return 人脸信息列表
     */
    public List<FaceInfo> queryFacesByName(String name) {
        try {
            String collectionName = milvusConfig.getCollection().getName();
            
            List<String> outFields = Arrays.asList(
                    FIELD_FACE_ID, FIELD_PERSON_ID, FIELD_NAME, 
                    FIELD_REMARK, FIELD_REGISTER_TIME
            );
            
            String expr = FIELD_NAME + " == \"" + name + "\"";
            
            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .withOutFields(outFields)
                    .build();
            
            R<QueryResults> queryResp = milvusClient.query(queryParam);
            
            if (queryResp.getStatus() != R.Status.Success.getCode()) {
                log.error("查询人脸失败: {}", queryResp.getMessage());
                return Collections.emptyList();
            }
            
            return parseFaceInfoList(queryResp.getData());
            
        } catch (Exception e) {
            log.error("根据name查询人脸异常", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 解析查询结果为FaceInfo列表
     */
    private List<FaceInfo> parseFaceInfoList(QueryResults queryResults) {
        List<FaceInfo> faceInfoList = new ArrayList<>();
        
        QueryResultsWrapper wrapper = new QueryResultsWrapper(queryResults);
        
        for (int i = 0; i < wrapper.getRowRecords().size(); i++) {
            QueryResultsWrapper.RowRecord record = wrapper.getRowRecords().get(i);
            
            FaceInfo faceInfo = FaceInfo.builder()
                    .faceId((String) record.get(FIELD_FACE_ID))
                    .personId((String) record.get(FIELD_PERSON_ID))
                    .name((String) record.get(FIELD_NAME))
                    .remark((String) record.get(FIELD_REMARK))
                    .registerTime((Long) record.get(FIELD_REGISTER_TIME))
                    .build();
            
            faceInfoList.add(faceInfo);
        }
        
        return faceInfoList;
    }
    
    /**
     * float[] 转 List<Float>
     */
    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float value : array) {
            list.add(value);
        }
        return list;
    }
    
    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            milvusClient.close();
            log.info("Milvus连接已关闭");
        }
    }
}
