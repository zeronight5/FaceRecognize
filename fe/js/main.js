// 主程序入口

// 应用状态
const appState = {
    allFaces: [],  // 从服务器加载的所有人脸
    filteredFaces: []  // 过滤后的人脸列表
};

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

async function initApp() {
    // 检查服务状态
    await checkServiceStatus();
    
    // 初始化Tab切换
    initTabs();
    
    // 初始化注册表单
    initRegisterForm();
    
    // 初始化识别功能
    initRecognizeForm();
    
    // 初始化人脸管理
    initManagement();
    
    // 定期检查服务状态
    setInterval(checkServiceStatus, 30000);
}

// 检查服务状态
async function checkServiceStatus() {
    const isOnline = await API.checkHealth();
    const indicator = document.getElementById('statusIndicator');
    const statusText = document.getElementById('statusText');
    
    if (isOnline) {
        indicator.className = 'status-indicator online';
        statusText.textContent = '服务正常';
    } else {
        indicator.className = 'status-indicator offline';
        statusText.textContent = '服务离线';
    }
}

// Tab切换
function initTabs() {
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');
    
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetTab = btn.dataset.tab;
            
            // 切换按钮状态
            tabBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            
            // 切换内容显示
            tabContents.forEach(content => {
                content.classList.remove('active');
                if (content.id === targetTab) {
                    content.classList.add('active');
                }
            });
        });
    });
}

// 初始化注册表单
function initRegisterForm() {
    const form = document.getElementById('registerForm');
    const uploadArea = document.getElementById('registerUploadArea');
    const fileInput = document.getElementById('registerImageInput');
    const placeholder = document.getElementById('registerPlaceholder');
    const preview = document.getElementById('registerPreview');
    const previewImg = document.getElementById('registerPreviewImg');
    const removeBtn = document.getElementById('registerRemoveBtn');
    
    let selectedFile = null;
    
    // 点击上传区域
    uploadArea.addEventListener('click', (e) => {
        if (!e.target.closest('.remove-btn')) {
            fileInput.click();
        }
    });
    
    // 文件选择
    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file && validateFile(file)) {
            selectedFile = file;
            showPreview(file, previewImg, placeholder, preview);
        }
    });
    
    // 拖拽上传
    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.style.borderColor = 'var(--primary-color)';
    });
    
    uploadArea.addEventListener('dragleave', () => {
        uploadArea.style.borderColor = 'var(--border-color)';
    });
    
    uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadArea.style.borderColor = 'var(--border-color)';
        
        const file = e.dataTransfer.files[0];
        if (file && validateFile(file)) {
            selectedFile = file;
            fileInput.files = e.dataTransfer.files;
            showPreview(file, previewImg, placeholder, preview);
        }
    });
    
    // 移除图片
    removeBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        selectedFile = null;
        fileInput.value = '';
        placeholder.style.display = 'block';
        preview.style.display = 'none';
    });
    
    // 表单提交
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        if (!selectedFile) {
            showToast('请上传人脸图片', 'warning');
            return;
        }
        
        const formData = new FormData();
        formData.append('file', selectedFile);
        formData.append('name', document.getElementById('personName').value);
        formData.append('personId', document.getElementById('personId').value);
        formData.append('remark', document.getElementById('remark').value);
        
        showLoading(true);
        
        const result = await API.registerFace(formData);
        
        showLoading(false);
        
        if (result.success) {
            showToast('人脸注册成功！', 'success');
            form.reset();
            selectedFile = null;
            placeholder.style.display = 'block';
            preview.style.display = 'none';
            
            // 重新加载人脸列表
            await loadFaceList();
        } else {
            showToast(result.message || '注册失败', 'error');
        }
    });
    
    // 重置表单
    form.addEventListener('reset', () => {
        selectedFile = null;
        fileInput.value = '';
        placeholder.style.display = 'block';
        preview.style.display = 'none';
    });
}

// 初始化识别表单
function initRecognizeForm() {
    const uploadArea = document.getElementById('recognizeUploadArea');
    const fileInput = document.getElementById('recognizeImageInput');
    const placeholder = document.getElementById('recognizePlaceholder');
    const preview = document.getElementById('recognizePreview');
    const previewImg = document.getElementById('recognizePreviewImg');
    const removeBtn = document.getElementById('recognizeRemoveBtn');
    const recognizeBtn = document.getElementById('recognizeBtn');
    const thresholdInput = document.getElementById('threshold');
    const thresholdValue = document.getElementById('thresholdValue');
    const resultsDiv = document.getElementById('recognizeResults');
    const resultsList = document.getElementById('resultsList');
    
    let selectedFile = null;
    
    // 阈值滑块
    thresholdInput.addEventListener('input', (e) => {
        const value = (e.target.value / 100).toFixed(2);
        thresholdValue.textContent = value;
    });
    
    // 点击上传
    uploadArea.addEventListener('click', (e) => {
        if (!e.target.closest('.remove-btn')) {
            fileInput.click();
        }
    });
    
    // 文件选择
    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file && validateFile(file)) {
            selectedFile = file;
            showPreview(file, previewImg, placeholder, preview);
        }
    });
    
    // 拖拽上传
    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.style.borderColor = 'var(--primary-color)';
    });
    
    uploadArea.addEventListener('dragleave', () => {
        uploadArea.style.borderColor = 'var(--border-color)';
    });
    
    uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadArea.style.borderColor = 'var(--border-color)';
        
        const file = e.dataTransfer.files[0];
        if (file && validateFile(file)) {
            selectedFile = file;
            fileInput.files = e.dataTransfer.files;
            showPreview(file, previewImg, placeholder, preview);
        }
    });
    
    // 移除图片
    removeBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        selectedFile = null;
        fileInput.value = '';
        placeholder.style.display = 'block';
        preview.style.display = 'none';
        resultsDiv.style.display = 'none';
    });
    
    // 开始识别
    recognizeBtn.addEventListener('click', async () => {
        if (!selectedFile) {
            showToast('请先上传待识别的图片', 'warning');
            return;
        }
        
        const formData = new FormData();
        formData.append('file', selectedFile);
        formData.append('threshold', thresholdInput.value / 100);
        formData.append('topK', document.getElementById('topK').value);
        
        showLoading(true);
        
        const result = await API.recognizeFace(formData);
        
        showLoading(false);
        
        if (result.success) {
            displayResults(result.data, resultsList, resultsDiv);
            if (result.data && result.data.length > 0) {
                showToast(`识别完成，找到 ${result.data.length} 个匹配结果`, 'success');
            } else {
                showToast('未找到匹配的人脸', 'warning');
            }
        } else {
            showToast(result.message || '识别失败', 'error');
            resultsDiv.style.display = 'none';
        }
    });
}

// 初始化人脸管理
function initManagement() {
    const searchInput = document.getElementById('searchInput');
    const clearSearchBtn = document.getElementById('clearSearchBtn');
    const refreshBtn = document.getElementById('refreshListBtn');
    const resetDbBtn = document.getElementById('resetDbBtn');
    
    // 搜索功能 - 从服务器搜索
    searchInput.addEventListener('input', debounce(async (e) => {
        const keyword = e.target.value.trim();
        if (keyword) {
            await searchFaces(keyword);
        } else {
            await loadFaceList();
        }
    }, 500));
    
    // 清除搜索
    clearSearchBtn.addEventListener('click', async () => {
        searchInput.value = '';
        await loadFaceList();
    });
    
    // 刷新列表
    refreshBtn.addEventListener('click', async () => {
        await loadFaceList();
        showToast('列表已刷新', 'success');
    });
    
    // 重置数据库
    resetDbBtn.addEventListener('click', async () => {
        if (confirm('⚠️ 警告：此操作将删除所有人脸数据且无法恢复！\\n\\n确定要重置数据库吗？')) {
            showLoading(true);
            const result = await API.resetDatabase();
            showLoading(false);
            
            if (result.success) {
                showToast('数据库已重置', 'success');
                await loadFaceList();
            } else {
                showToast(result.message || '重置失败', 'error');
            }
        }
    });
    
    // 初始加载
    loadFaceList();
}

// 显示图片预览
function showPreview(file, imgElement, placeholder, previewContainer) {
    const reader = new FileReader();
    reader.onload = (e) => {
        imgElement.src = e.target.result;
        placeholder.style.display = 'none';
        previewContainer.style.display = 'block';
    };
    reader.readAsDataURL(file);
}

// 显示识别结果
function displayResults(results, container, resultsDiv) {
    if (!results || results.length === 0) {
        container.innerHTML = '<div class="empty-state"><p>未找到匹配的人脸</p></div>';
        resultsDiv.style.display = 'block';
        return;
    }
    
    container.innerHTML = results.map(result => `
        <div class="result-item">
            <div class="result-info">
                <h4>${result.name}</h4>
                <p>人员编号: ${result.personId}</p>
                ${result.remark ? `<p>备注: ${result.remark}</p>` : ''}
            </div>
            <div class="similarity-badge similarity-${getSimilarityLevel(result.similarity)}">
                ${formatSimilarity(result.similarity)}
            </div>
        </div>
    `).join('');
    
    resultsDiv.style.display = 'block';
}

// 渲染人脸列表
async function renderFaceList() {
    const container = document.getElementById('faceList');
    const totalCount = document.getElementById('totalCount');
    const faces = appState.filteredFaces.length > 0 ? appState.filteredFaces : appState.allFaces;
    
    if (totalCount) {
        totalCount.textContent = faces.length;
    }
    
    if (faces.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-inbox"></i>
                <p>暂无人脸数据</p>
                <p style="font-size: 12px; margin-top: 10px;">请前往"注册人脸"添加数据</p>
            </div>
        `;
        return;
    }
    
    container.innerHTML = faces.map(face => `
        <div class="face-item" data-face-id="${face.faceId}" data-person-id="${face.personId}">
            <div class="face-info">
                <h4>${face.name || '未知'}</h4>
                <p>编号: ${face.personId || 'N/A'} ${face.createTime ? '| 注册时间: ' + formatDateTime(face.createTime) : ''}</p>
                ${face.remark && face.remark !== 'null' ? `<p>备注: ${face.remark}</p>` : ''}
            </div>
            <div class="face-actions">
                <button class="btn-icon delete-face" title="删除此人脸">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
        </div>
    `).join('');
    
    // 绑定删除事件
    container.querySelectorAll('.delete-face').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            const item = e.target.closest('.face-item');
            const faceId = item.dataset.faceId;
            
            if (confirm('确定要删除这个人脸吗？')) {
                showLoading(true);
                const result = await API.deleteFace(faceId);
                showLoading(false);
                
                if (result.success) {
                    showToast('删除成功', 'success');
                    await loadFaceList();
                } else {
                    showToast(result.message || '删除失败', 'error');
                }
            }
        });
    });
}

// 从服务器加载人脸列表
async function loadFaceList() {
    showLoading(true);
    const result = await API.listFaces();
    showLoading(false);
    
    if (result.success) {
        appState.allFaces = result.data || [];
        appState.filteredFaces = [];
        renderFaceList();
    } else {
        showToast('加载人脸列表失败: ' + (result.message || ''), 'error');
        appState.allFaces = [];
        appState.filteredFaces = [];
        renderFaceList();
    }
}

// 搜索人脸
async function searchFaces(keyword) {
    showLoading(true);
    
    // 尝试按person_id搜索
    let result = await API.listFaces(keyword, null);
    
    // 如果没结果，再按name搜索
    if (result.success && (!result.data || result.data.length === 0)) {
        result = await API.listFaces(null, keyword);
    }
    
    showLoading(false);
    
    if (result.success) {
        appState.filteredFaces = result.data || [];
        renderFaceList();
        
        if (appState.filteredFaces.length === 0) {
            showToast('未找到匹配的人脸', 'warning');
        }
    } else {
        showToast('搜索失败: ' + (result.message || ''), 'error');
    }
}
