// API调用封装

const API = {
    // 健康检查
    async checkHealth() {
        try {
            const response = await fetch(`${CONFIG.API_BASE_URL}${CONFIG.ENDPOINTS.HEALTH}`);
            const data = await response.json();
            return data.code === 200;
        } catch (error) {
            console.error('健康检查失败:', error);
            return false;
        }
    },

    // 注册人脸
    async registerFace(formData) {
        try {
            const response = await fetch(
                `${CONFIG.API_BASE_URL}${CONFIG.ENDPOINTS.REGISTER}`,
                {
                    method: 'POST',
                    body: formData
                }
            );
            
            const data = await response.json();
            
            if (data.code === 200) {
                return { success: true, data: data.data };
            } else {
                return { success: false, message: data.message };
            }
        } catch (error) {
            console.error('注册失败:', error);
            return { success: false, message: '网络错误，请检查服务是否启动' };
        }
    },

    // 识别人脸
    async recognizeFace(formData) {
        try {
            const response = await fetch(
                `${CONFIG.API_BASE_URL}${CONFIG.ENDPOINTS.RECOGNIZE}`,
                {
                    method: 'POST',
                    body: formData
                }
            );
            
            const data = await response.json();
            
            if (data.code === 200) {
                return { success: true, data: data.data };
            } else {
                return { success: false, message: data.message };
            }
        } catch (error) {
            console.error('识别失败:', error);
            return { success: false, message: '网络错误，请检查服务是否启动' };
        }
    },

    // 删除人脸
    async deleteFace(faceId) {
        try {
            const response = await fetch(
                `${CONFIG.API_BASE_URL}${CONFIG.ENDPOINTS.DELETE_FACE}/${faceId}`,
                {
                    method: 'DELETE'
                }
            );
            
            const data = await response.json();
            
            if (data.code === 200) {
                return { success: true };
            } else {
                return { success: false, message: data.message };
            }
        } catch (error) {
            console.error('删除失败:', error);
            return { success: false, message: '网络错误' };
        }
    },

    // 删除人员所有人脸
    async deletePersonFaces(personId) {
        try {
            const response = await fetch(
                `${CONFIG.API_BASE_URL}${CONFIG.ENDPOINTS.DELETE_PERSON}/${personId}`,
                {
                    method: 'DELETE'
                }
            );
            
            const data = await response.json();
            
            if (data.code === 200) {
                return { success: true };
            } else {
                return { success: false, message: data.message };
            }
        } catch (error) {
            console.error('删除失败:', error);
            return { success: false, message: '网络错误' };
        }
    },
    
    // 查询人脸列表
    async listFaces(personId = null, name = null, limit = 100) {
        try {
            let url = `${CONFIG.API_BASE_URL}${CONFIG.ENDPOINTS.LIST_FACES}?limit=${limit}`;
            if (personId) url += `&personId=${encodeURIComponent(personId)}`;
            if (name) url += `&name=${encodeURIComponent(name)}`;
            
            const response = await fetch(url);
            const data = await response.json();
            
            if (data.code === 200) {
                return { success: true, data: data.data };
            } else {
                return { success: false, message: data.message };
            }
        } catch (error) {
            console.error('查询失败:', error);
            return { success: false, message: '网络错误' };
        }
    },
    
    // 根据person_id查询
    async queryByPersonId(personId) {
        try {
            const response = await fetch(
                `${CONFIG.API_BASE_URL}${CONFIG.ENDPOINTS.QUERY_BY_PERSON}/${personId}`
            );
            const data = await response.json();
            
            if (data.code === 200) {
                return { success: true, data: data.data };
            } else {
                return { success: false, message: data.message };
            }
        } catch (error) {
            console.error('查询失败:', error);
            return { success: false, message: '网络错误' };
        }
    },
    
    // 重置数据库
    async resetDatabase() {
        try {
            const response = await fetch(
                `${CONFIG.API_BASE_URL}${CONFIG.ENDPOINTS.RESET_DB}`,
                { method: 'POST' }
            );
            const data = await response.json();
            
            if (data.code === 200) {
                return { success: true };
            } else {
                return { success: false, message: data.message };
            }
        } catch (error) {
            console.error('重置失败:', error);
            return { success: false, message: '网络错误' };
        }
    }
};
