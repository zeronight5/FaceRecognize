// 工具函数

// 显示Toast提示
function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = `toast ${type} show`;
    
    setTimeout(() => {
        toast.className = 'toast';
    }, 3000);
}

// 显示/隐藏加载遮罩
function showLoading(show = true) {
    const overlay = document.getElementById('loadingOverlay');
    overlay.className = show ? 'loading-overlay show' : 'loading-overlay';
}

// 验证文件
function validateFile(file) {
    // 检查文件类型
    if (!CONFIG.UPLOAD.ACCEPTED_TYPES.includes(file.type)) {
        showToast('只支持 JPG 和 PNG 格式的图片', 'error');
        return false;
    }
    
    // 检查文件大小
    if (file.size > CONFIG.UPLOAD.MAX_SIZE) {
        showToast('图片大小不能超过 10MB', 'error');
        return false;
    }
    
    return true;
}

// 文件转Base64
function fileToBase64(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
            // 移除 data:image/jpeg;base64, 前缀
            const base64 = reader.result.split(',')[1];
            resolve(base64);
        };
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
}

// 格式化日期时间
function formatDateTime(timestamp) {
    const date = new Date(timestamp);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}`;
}

// 格式化相似度
function formatSimilarity(similarity) {
    return (similarity * 100).toFixed(2) + '%';
}

// 获取相似度等级
function getSimilarityLevel(similarity) {
    if (similarity >= 0.8) return 'high';
    if (similarity >= 0.6) return 'medium';
    return 'low';
}

// 防抖函数
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// 确认对话框
function confirmDialog(message) {
    return confirm(message);
}
