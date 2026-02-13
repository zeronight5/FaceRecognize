// API配置
const CONFIG = {
    // 后端API地址
    API_BASE_URL: 'http://localhost:8080/api',
    
    // API端点
    ENDPOINTS: {
        HEALTH: '/face/health',
        REGISTER: '/face/register/upload',
        RECOGNIZE: '/face/recognize/upload',
        DELETE_FACE: '/face',
        DELETE_PERSON: '/face/person',
        LIST_FACES: '/face/list',
        QUERY_BY_PERSON: '/face/person',
        RESET_DB: '/face/reset'
    },
    
    // 默认配置
    DEFAULTS: {
        THRESHOLD: 0.6,
        TOP_K: 5
    },
    
    // 文件上传限制
    UPLOAD: {
        MAX_SIZE: 10 * 1024 * 1024, // 10MB
        ACCEPTED_TYPES: ['image/jpeg', 'image/jpg', 'image/png']
    }
};
