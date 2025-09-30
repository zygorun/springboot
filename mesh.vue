<template>
  <div class="mesh-generator">
    <div class="layout-container">
      <!-- 左侧边栏 - 网格配置 -->
      <div class="left-sidebar">
        <el-card class="config-card">
          <template #header>
            <span>网格配置</span>
          </template>
          
          <el-form :model="meshForm" label-width="100px" class="mesh-config-form">
            <!-- 预设方案选择 -->
            <el-form-item label="质量预设">
              <el-select 
                v-model="qualityPreset" 
                placeholder="选择质量预设" 
                size="large"
                style="width: 100%;"
                @change="applyQualityPreset"
              >
                <el-option label="🚀 快速 - 快速预览" value="fast">
                  <div>
                    <div>🚀 快速</div>
                    <div style="font-size: 12px; color: #909399;">适合快速预览，精细度较低</div>
                  </div>
                </el-option>
                <el-option label="⚡ 标准 - 日常使用" value="standard">
                  <div>
                    <div>⚡ 标准</div>
                    <div style="font-size: 12px; color: #909399;">平衡精度和速度，推荐使用</div>
                  </div>
                </el-option>
                <el-option label="💎 精细 - 高精度" value="fine">
                  <div>
                    <div>💎 精细</div>
                    <div style="font-size: 12px; color: #909399;">高精度计算，生成较慢</div>
                  </div>
                </el-option>
                <el-option label="🔬 超精细 - 研究用" value="ultrafine">
                  <div>
                    <div>🔬 超精细</div>
                    <div style="font-size: 12px; color: #909399;">极高精度，需要大量时间</div>
                  </div>
                </el-option>
                <el-option label="⚙️ 自定义" value="custom">
                  <div>
                    <div>⚙️ 自定义</div>
                    <div style="font-size: 12px; color: #909399;">手动设置精细度参数</div>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
            
            <!-- 精细度数字选择（自定义模式） -->
            <el-form-item label="网格精细度" v-if="qualityPreset === 'custom'">
              <el-slider
                v-model="meshForm.hauto"
                :min="1"
                :max="9"
                :marks="{
                  1: '粗',
                  5: '中',
                  9: '细'
                }"
                show-stops
                style="width: 100%; padding: 0 8px;"
              />
              <div class="form-tip">
                当前等级: {{ meshForm.hauto }} - {{ getMeshLevelDescription(meshForm.hauto) }}
              </div>
            </el-form-item>
            
            <!-- 网格信息提示 -->
            <el-alert 
              v-if="meshInfo"
              type="success"
              :closable="false"
              style="margin-top: 16px;"
            >
              <template #title>
                <div style="font-size: 13px;">
                  <div>✓ 上次生成: {{ meshInfo.generated_at }}</div>
                  <div>节点: {{ statistics.nodes?.toLocaleString() }}</div>
                  <div>单元: {{ statistics.elements?.toLocaleString() }}</div>
                  <div v-if="statistics.estimated_memory_mb">
                    内存: ~{{ statistics.estimated_memory_mb }} MB
                  </div>
                </div>
              </template>
            </el-alert>
          </el-form>
          
          <!-- 生成网格按钮 -->
          <div class="action-buttons">
            <el-button 
              type="primary" 
              @click="generateMeshAndVisualization"
              :disabled="!modelStore.currentModel"
              :loading="generating"
              size="large"
              style="width: 100%;"
            >
              <el-icon><Grid /></el-icon>
              {{ generating ? '正在生成...' : '生成网格' }}
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- 中央区域 - 3D可视化（扩大显示） -->
      <div class="center-area">
        <el-card class="visualization-card">
          <template #header>
            <div class="card-header">
              <span>3D网格可视化</span>
              <div class="header-actions">
                <el-button 
                  v-if="meshVisualizationData" 
                  type="primary" 
                  @click="resetCamera"
                  size="small"
                >
                  <el-icon><Refresh /></el-icon>
                  重置视角
                </el-button>
              </div>
            </div>
          </template>
          
          <div class="visualization-content">
            <div v-if="!modelStore.currentModel" class="no-model">
              <el-empty description="请先选择一个模型" />
            </div>
            
            <div v-else-if="meshStatus === 'pending'" class="no-mesh">
              <el-empty description="点击生成网格按钮开始生成网格" />
            </div>
            
            <div v-else-if="generating" class="generating-visualization">
              <div class="loading-container">
                <el-icon class="is-loading" size="48"><Loading /></el-icon>
                <p>{{ generatingStep }}</p>
              </div>
            </div>
            
            <div v-else-if="meshStatus === 'error'" class="error-visualization">
              <el-empty description="网格生成失败，请重试" />
            </div>
            
            <!-- Three.js 3D 渲染容器 - 始终存在但可能隐藏 -->
            <div 
              class="three-container" 
              ref="threeContainer"
              :style="{ display: meshVisualizationData ? 'block' : 'none' }"
            ></div>
          </div>
        </el-card>
      </div>


    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Grid, View, Download, Loading, Refresh } from '@element-plus/icons-vue'
import { useModelStore } from '@/stores/model'
import apiService from '@/services/api'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'

// Store
const modelStore = useModelStore()

// 简化的网格配置表单
const meshForm = ref({
  type: 'FreeTet', // 固定使用四面体网格
  refinementMode: 'auto', // 固定使用自动模式
  hauto: 5, // 默认精细度等级为5（正常）- 数字类型
  exportFormat: 'vtk' // 固定导出格式
})

// 质量预设
const qualityPreset = ref('standard')

// 质量预设配置
const qualityPresets = {
  fast: { hauto: 3, description: '快速预览，适合检查几何' },
  standard: { hauto: 5, description: '标准质量，日常使用推荐' },
  fine: { hauto: 7, description: '精细网格，高精度计算' },
  ultrafine: { hauto: 9, description: '超精细网格，研究级精度' },
  custom: { hauto: 5, description: '自定义精细度' }
}

// 应用质量预设
const applyQualityPreset = () => {
  const preset = qualityPresets[qualityPreset.value]
  if (preset) {
    meshForm.value.hauto = preset.hauto
    console.log(`应用预设 ${qualityPreset.value}: hauto=${preset.hauto}`)
  }
}

// 获取网格等级描述
const getMeshLevelDescription = (level) => {
  const descriptions = {
    1: '极粗 - 极快',
    2: '很粗 - 很快',
    3: '粗糙 - 快速',
    4: '较粗 - 较快',
    5: '正常 - 平衡',
    6: '较细 - 较慢',
    7: '精细 - 慢',
    8: '很细 - 很慢',
    9: '极细 - 极慢'
  }
  return descriptions[level] || '正常'
}

// 状态管理
const generating = ref(false)
const generatingStep = ref('准备生成网格...')
const meshStatus = ref('pending') // pending, generating, success, error
const meshInfo = ref(null)
const meshLog = ref([])
const statistics = ref({ nodes: 0, elements: 0 })

// Three.js 相关变量
const threeContainer = ref(null)
let scene = null
let camera = null
let renderer = null
let controls = null
let meshGroup = null

// 网格可视化数据
const meshVisualizationData = ref(null)

// 渲染选项
const renderOptions = reactive({
  wireframe: true,
  showNodes: false,
  showEdges: true,
  colorByType: true
})

// 计算属性
const meshStatusText = computed(() => {
  switch (meshStatus.value) {
    case 'success': return '生成成功'
    case 'error': return '生成失败'
    case 'generating': return '正在生成'
    default: return '未生成'
  }
})

const meshStatusType = computed(() => {
  switch (meshStatus.value) {
    case 'success': return 'success'
    case 'error': return 'danger'
    case 'generating': return 'warning'
    default: return 'info'
  }
})

// 添加日志
const addLog = (level, message) => {
  const timestamp = new Date().toLocaleTimeString()
  meshLog.value.push({
    timestamp,
    level,
    message,
    type: level === 'error' ? 'danger' : level === 'warning' ? 'warning' : 'success'
  })
}

// 优化VTK数据解析和Three.js渲染
const parseVTKAndRender = async (vtkFilePath) => {
  try {
    console.log('开始解析VTK文件:', vtkFilePath)
    
    // 方法1: 如果有VTK.js库，使用标准VTK解析
    if (typeof vtk !== 'undefined') {
      const reader = vtk.IO.Core.vtkXMLPolyDataReader.newInstance()
      await reader.setUrl(vtkFilePath)
      const polyData = reader.getOutputData()
      
      // 提取顶点坐标
      const points = polyData.getPoints().getData()
      const cells = polyData.getPolys().getData()
      
      // 构建Three.js几何体
      const geometry = new THREE.BufferGeometry()
      geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(points), 3))
      
      // 解析面索引
      const indices = []
      let i = 0
      while (i < cells.length) {
        const vertexCount = cells[i++]
        for (let j = 0; j < vertexCount; j++) {
          indices.push(cells[i + j])
        }
        i += vertexCount
      }
      geometry.setIndex(new THREE.Uint32BufferAttribute(indices, 1))
      
      // 创建线框材质
      const material = new THREE.MeshBasicMaterial({
        color: meshOptions.wireframeColor,
        wireframe: true,
        transparent: true,
        opacity: meshOptions.opacity
      })
      
      const mesh = new THREE.Mesh(geometry, material)
      scene.add(mesh)
      
      // 调整相机位置
      geometry.computeBoundingBox()
      const box = geometry.boundingBox
      const center = box.getCenter(new THREE.Vector3())
      const size = box.getSize(new THREE.Vector3())
      const maxSize = Math.max(size.x, size.y, size.z)
      
      camera.position.set(
        center.x + maxSize,
        center.y + maxSize,
        center.z + maxSize
      )
      
      if (controls) {
        controls.target.copy(center)
        controls.update()
      }
      
      console.log('VTK渲染完成')
    } else {
      // 方法2: 回退到当前的mesh数据解析方式
      console.log('VTK.js不可用，使用现有解析方式')
      if (meshVisualizationData.value) {
        renderMeshData(meshVisualizationData.value.mesh_data)
      }
    }
  } catch (error) {
    console.error('VTK解析失败:', error)
    ElMessage.error('VTK解析失败: ' + error.message)
  }
}

// 生成网格和可视化
const generateMeshAndVisualization = async () => {
  if (!modelStore.currentModel) {
    ElMessage.warning('请先选择一个模型')
    return
  }

  generating.value = true
  meshStatus.value = 'generating'
  meshLog.value = []
  
  try {
    // 第一步：生成网格
    generatingStep.value = '正在检查几何并生成网格...'
    const presetName = Object.keys(qualityPresets).find(k => qualityPresets[k].hauto === meshForm.value.hauto) || 'custom'
    addLog('info', `开始生成网格 - 预设: ${presetName}, 精细度: ${meshForm.value.hauto}`)
    
    const meshConfig = {
      type: meshForm.value.type,
      refinement_mode: 'auto',
      hauto: meshForm.value.hauto,  // 直接使用精细度等级
      curvature_factor: 0.3,  // 曲率因子
      resolution_factor: 1.0, // 分辨率因子
      export_format: meshForm.value.exportFormat,
      sequence: meshForm.value.sequence || undefined,
      auto_recommend: false  // 不使用自动推荐，使用用户选择
    }
    
    addLog('info', `网格配置: 精细度=${meshConfig.hauto}, 类型=${meshConfig.type}`)
    
    const meshResponse = await apiService.generateMesh(modelStore.currentModel.id, meshConfig)
    
    if (meshResponse.data.success) {
      addLog('success', meshResponse.data.message || '网格生成成功')
      meshStatus.value = 'success'
      meshInfo.value = meshResponse.data.mesh_info
      statistics.value = meshResponse.data.statistics || { nodes: 0, elements: 0 }
      
      // 显示统计信息
      if (meshResponse.data.statistics) {
        addLog('info', `节点数: ${statistics.value.nodes?.toLocaleString()}, 单元数: ${statistics.value.elements?.toLocaleString()}`)
        if (meshResponse.data.mesh_info?.generation_time) {
          addLog('info', `生成耗时: ${meshResponse.data.mesh_info.generation_time} 秒`)
        }
      }
      
      // 如果有VTK文件，优先使用VTK渲染
      if (meshResponse.data.mesh_info.vtk_file) {
        await parseVTKAndRender(`/api/download/${meshResponse.data.mesh_info.vtk_file}`)
      } else {
        // 回退到现有的可视化方式
        generatingStep.value = '正在生成3D可视化...'
        addLog('info', '开始生成3D可视化')
        
        const vizResponse = await apiService.getMeshVisualization(modelStore.currentModel.id)
        
        console.log('=== 可视化响应 ===')
        console.log('vizResponse:', vizResponse)
        console.log('vizResponse.data:', vizResponse.data)
        
        if (vizResponse.data.success) {
          meshVisualizationData.value = vizResponse.data
          addLog('success', '3D可视化生成成功')
          
          console.log('准备渲染，完整数据:', vizResponse.data)
          
          // 等待DOM更新后渲染网格
          await nextTick()
          // 传递完整的响应数据（包含 mesh_data 和 geometry_surface）
          renderMeshData(vizResponse.data)
        } else if (vizResponse.data && vizResponse.data.vertices) {
          // 旧格式兼容
          meshVisualizationData.value = { mesh_data: vizResponse.data }
          
          // 等待DOM更新后渲染网格
          await nextTick()
          renderMeshData(vizResponse.data)
        } else {
          throw new Error(vizResponse.data.error || '生成可视化失败')
        }
      }
      
      ElMessage.success('网格生成和可视化完成')
    } else {
      throw new Error(meshResponse.data.error || '生成网格失败')
    }
  } catch (error) {
    console.error('生成失败:', error)
    addLog('error', `生成失败: ${error.message}`)
    meshStatus.value = 'error'
    ElMessage.error(`生成失败: ${error.message}`)
  } finally {
    generating.value = false
  }
}
// 在 script setup 部分添加增强的显示控制
const displayMode = ref('wireframe') // 默认线框模式，更适合网格可视化
const showStats = ref(true)
const selectedElement = ref(null)
const showSolid = ref(false) // 控制是否显示实体

// 网格显示选项
const meshOptions = reactive({
  showEdges: true,
  showNodes: false,
  wireframeOnly: false,
  wireframeColor: '#0077ff',
  solidColor: '#00aa44',
  opacity: 0.8,
  enableSelection: false,
  lineWidth: 1
})

// 渲染灰色实体几何
const renderGeometrySolid = (geometryData, group) => {
  try {
    console.log('=== renderGeometrySolid ===')
    console.log('几何顶点数:', geometryData.vertices.length)
    console.log('几何三角面数:', geometryData.triangular_faces.length)
    
    // 处理顶点数据
    let geoVertices
    if (geometryData.vertices[0] && Array.isArray(geometryData.vertices[0])) {
      geoVertices = new Float32Array(geometryData.vertices.flat())
    } else {
      geoVertices = new Float32Array(geometryData.vertices)
    }
    
    // 创建几何体
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(geoVertices, 3))
    
    // 设置索引
    if (geometryData.triangular_faces && geometryData.triangular_faces.length > 0) {
      const indices = geometryData.triangular_faces.flat()
      geometry.setIndex(indices)
      geometry.computeVertexNormals()
      
      // 创建灰色半透明材质
      const material = new THREE.MeshStandardMaterial({
        color: 0x999999,  // 灰色
        transparent: true,
        opacity: 0.6,      // 半透明
        side: THREE.DoubleSide,
        flatShading: false,
        metalness: 0.3,
        roughness: 0.7
      })
      
      // 创建网格对象
      const mesh = new THREE.Mesh(geometry, material)
      mesh.name = 'geometry-solid'
      group.add(mesh)
      
      console.log('✓ 几何实体渲染完成')
    }
  } catch (error) {
    console.error('渲染几何实体失败:', error)
  }
}

// 简化的网格数据修复函数 - 改为非递归实现
const fixMeshIndices = (meshData) => {
  const result = { ...meshData }
  
  if (!meshData.triangular_faces || meshData.triangular_faces.length === 0) {
    result.triangular_faces = []
    return result
  }
  
  try {
    const faces = meshData.triangular_faces
    
    // 验证数据结构
    if (!Array.isArray(faces)) {
      console.error('triangular_faces不是数组')
      result.triangular_faces = []
      return result
    }
    
    // 检查是否需要索引转换
    let needsConversion = false
    let minIndex = Infinity
    let maxIndex = -Infinity
    
    // 使用迭代而非递归来检查索引
    for (let i = 0; i < faces.length; i++) {
      const face = faces[i]
      if (!Array.isArray(face) || face.length !== 3) {
        console.warn(`面 ${i} 格式错误:`, face)
        continue
      }
      
      for (let j = 0; j < face.length; j++) {
        const index = face[j]
        if (typeof index === 'number' && Number.isInteger(index)) {
          minIndex = Math.min(minIndex, index)
          maxIndex = Math.max(maxIndex, index)
        }
      }
    }
    
    // 如果最小索引是1，说明是1-based索引，需要转换为0-based
    if (minIndex === 1) {
      needsConversion = true
    }
    
    console.log(`索引范围: ${minIndex} - ${maxIndex}, 需要转换: ${needsConversion}`)
    
    // 转换索引（使用迭代）
    if (needsConversion) {
      const convertedFaces = []
      for (let i = 0; i < faces.length; i++) {
        const face = faces[i]
        if (Array.isArray(face) && face.length === 3) {
          const convertedFace = []
          for (let j = 0; j < face.length; j++) {
            const index = face[j]
            if (typeof index === 'number' && Number.isInteger(index) && index > 0) {
              convertedFace.push(index - 1)
            } else {
              console.warn(`无效索引: ${index}`)
              convertedFace.push(0) // 使用默认值
            }
          }
          convertedFaces.push(convertedFace)
        }
      }
      result.triangular_faces = convertedFaces
    } else {
      // 验证并清理数据
      const cleanedFaces = []
      for (let i = 0; i < faces.length; i++) {
        const face = faces[i]
        if (Array.isArray(face) && face.length === 3) {
          const validFace = []
          let isValid = true
          for (let j = 0; j < face.length; j++) {
            const index = face[j]
            if (typeof index === 'number' && Number.isInteger(index) && index >= 0) {
              validFace.push(index)
            } else {
              isValid = false
              break
            }
          }
          if (isValid) {
            cleanedFaces.push(validFace)
          }
        }
      }
      result.triangular_faces = cleanedFaces
    }
    
    console.log(`处理完成: 原始面数 ${faces.length}, 有效面数 ${result.triangular_faces.length}`)
    
  } catch (error) {
    console.error('修复网格索引时出错:', error)
    result.triangular_faces = []
  }
  
  return result
}

// 渲染网格数据 - 双层渲染（STL几何实体 + 网格边界线）
const renderMeshData = async (data) => {
  console.log('=== renderMeshData 被调用 ===')
  console.log('接收到的数据:', data)
  
  if (!scene || !renderer) {
    console.error('Three.js未初始化')
    ElMessage.error('Three.js未初始化，请刷新页面重试')
    return
  }

  // 提取数据
  const meshData = data.mesh_data
  const renderMode = data.render_mode
  const plot3D = data.plot_3d  // 自动找到的3D绘图
  
  // 验证网格数据
  const vertices = meshData?.vertices || []
  const boundaryFaces = meshData?.triangular_faces || []
  
  if (!vertices || vertices.length === 0) {
    console.error('网格数据无效:', meshData)
    ElMessage.error('网格数据无效')
    return
  }

  console.log('=== 开始双层渲染 ===')
  console.log('渲染模式:', renderMode)
  console.log('网格节点数量:', vertices.length)
  console.log('网格边界面数量:', boundaryFaces.length)
  console.log('3D绘图:', plot3D)

  try {
    // 清除现有网格
    if (meshGroup) {
      scene.remove(meshGroup)
      meshGroup = null
    }

    // 创建新的网格组
    meshGroup = new THREE.Group()
    meshGroup.name = 'meshGroup'

    console.log('=== 使用COMSOL风格：网格表面 + 边线 ===')
    
    // 使用从tet四面体中提取的边界面
    const surfaceFaces = boundaryFaces
    
    console.log('表面三角形数据:', {
      exists: !!surfaceFaces,
      count: surfaceFaces?.length,
      sample: surfaceFaces?.slice(0, 3)
    })
    
    if (!surfaceFaces || surfaceFaces.length === 0) {
      console.error('没有表面三角形数据')
      ElMessage.error('网格数据无效：缺少表面三角形')
      return
    }
    
    // 处理顶点数据
    let meshVertices
    if (vertices[0] && Array.isArray(vertices[0])) {
      meshVertices = new Float32Array(vertices.flat())
    } else {
      meshVertices = new Float32Array(vertices)
    }
    
    console.log(`✓ 顶点数据: ${vertices.length} 个点`)
    console.log(`✓ 表面三角形: ${surfaceFaces.length} 个`)
    
    // === 1. 创建网格几何体（用于实体表面和边线） ===
    const meshGeometry = new THREE.BufferGeometry()
    meshGeometry.setAttribute('position', new THREE.BufferAttribute(meshVertices, 3))
    
    // 设置三角形索引
    const faceIndices = surfaceFaces.flat()
    meshGeometry.setIndex(faceIndices)
    meshGeometry.computeVertexNormals()  // 计算法线以便正确光照
    
    // === 2. 创建灰色实体表面（像COMSOL一样） ===
    const surfaceMaterial = new THREE.MeshStandardMaterial({
      color: 0xcccccc,  // 亮灰色
      metalness: 0.1,
      roughness: 0.8,
      side: THREE.DoubleSide,  // ⭐ 双面渲染，确保无论法线方向都能看到
      flatShading: false,
      polygonOffset: true,
      polygonOffsetFactor: 1,
      polygonOffsetUnits: 1
    })
    
    const surfaceMesh = new THREE.Mesh(meshGeometry, surfaceMaterial)
    surfaceMesh.name = 'mesh-surface'
    meshGroup.add(surfaceMesh)
    
    // 调试：检查几何体信息
    console.log('✓ 网格表面已创建（灰色）')
    console.log('BufferGeometry信息:', {
      hasPosition: !!meshGeometry.attributes.position,
      hasIndex: !!meshGeometry.index,
      hasNormals: !!meshGeometry.attributes.normal,
      vertexCount: meshGeometry.attributes.position.count,
      indexCount: meshGeometry.index ? meshGeometry.index.count : 0,
      triangleCount: meshGeometry.index ? meshGeometry.index.count / 3 : 0
    })
    
    // === 3. 创建黑色边线（使用WireframeGeometry显示所有三角形边） ===
    const wireframeGeometry = new THREE.WireframeGeometry(meshGeometry)
    const wireframeMaterial = new THREE.LineBasicMaterial({
      color: 0x000000,  // 黑色
      linewidth: 1,
      depthTest: true,
      depthWrite: false
    })
    
    const wireframe = new THREE.LineSegments(wireframeGeometry, wireframeMaterial)
    wireframe.name = 'mesh-wireframe'
    wireframe.renderOrder = 1  // 确保线框在表面之后渲染
    meshGroup.add(wireframe)
    
    console.log(`✓ 网格线框已创建（黑色）- ${wireframeGeometry.attributes.position.count / 2} 条边`)

    // 添加到场景
    scene.add(meshGroup)
    console.log('✓ meshGroup已添加到场景')
    console.log('meshGroup children:', meshGroup.children.map(c => ({
      name: c.name,
      type: c.type,
      visible: c.visible
    })))

    // 适配相机视角
    fitCameraToMesh(vertices)
    console.log('✓ 相机已适配')

    console.log('=== 网格渲染完成 ===')
    console.log('场景对象数量:', scene.children.length)
    console.log('渲染器状态:', {
      domElement: !!renderer.domElement,
      size: renderer.getSize(new THREE.Vector2())
    })
    
    ElMessage.success('网格渲染成功')

  } catch (error) {
    console.error('渲染网格时出错:', error)
    console.error('错误堆栈:', error.stack)
    ElMessage.error(`渲染失败: ${error.message}`)
  }
}

// 线框模式渲染 - 优化性能和可视化效果
const renderWireframe = (geometry, meshData, isOverlay = false) => {
  try {
    // 验证几何体
    if (!geometry || !geometry.attributes || !geometry.attributes.position) {
      console.error('几何体无效或缺少位置属性')
      return
    }
    
    if (meshData.triangular_faces && meshData.triangular_faces.length > 0) {
      // 验证面数据
      const flatFaces = meshData.triangular_faces.flat()
      if (flatFaces.length === 0 || flatFaces.length % 3 !== 0) {
        console.error('三角面数据格式错误:', flatFaces.length)
        return
      }
      
      // 验证索引值的有效性
      const vertexCount = geometry.attributes.position.count
      const invalidIndices = flatFaces.filter(index => 
        typeof index !== 'number' || 
        !Number.isInteger(index) || 
        index < 0 || 
        index >= vertexCount
      )
      
      if (invalidIndices.length > 0) {
        console.error('面数据包含无效索引:', invalidIndices.slice(0, 10))
        return
      }
      
      // 创建线框几何体
      const wireframeGeometry = new THREE.BufferGeometry()
      wireframeGeometry.setAttribute('position', geometry.attributes.position)
      
      // 创建边线索引
      const edges = new Set()
      for (let i = 0; i < meshData.triangular_faces.length; i++) {
        const face = meshData.triangular_faces[i]
        // 添加三条边
        const edges_to_add = [
          [face[0], face[1]],
          [face[1], face[2]],
          [face[2], face[0]]
        ]
        
        edges_to_add.forEach(edge => {
          const [a, b] = edge.sort((x, y) => x - y) // 确保边的一致性
          edges.add(`${a}-${b}`)
        })
      }
      
      // 转换为索引数组
      const edgeIndices = []
      edges.forEach(edgeStr => {
        const [a, b] = edgeStr.split('-').map(Number)
        edgeIndices.push(a, b)
      })
      
      wireframeGeometry.setIndex(edgeIndices)
      
      // 创建线框材质
      const wireframeMaterial = new THREE.LineBasicMaterial({ 
        color: meshOptions.wireframeColor,
        linewidth: meshOptions.lineWidth,
        transparent: !isOverlay,
        opacity: isOverlay ? 1.0 : meshOptions.opacity
      })
      
      // 创建线段对象
      const wireframe = new THREE.LineSegments(wireframeGeometry, wireframeMaterial)
      wireframe.name = isOverlay ? 'wireframe-overlay' : 'wireframe'
      
      meshGroup.add(wireframe)
      console.log(`线框渲染完成 - 边数: ${edgeIndices.length / 2}`)
      
      // 如果启用节点显示
      if (meshOptions.showNodes) {
        renderNodes(geometry)
      }
      
    } else {
      console.log('没有三角面数据，跳过线框渲染')
    }
  } catch (error) {
    console.error('渲染线框时出错:', error)
    ElMessage.error('渲染线框时出错: ' + error.message)
  }
}

// 实体模式渲染
const renderSolid = (geometry, meshData) => {
  try {
    console.log('=== renderSolid 开始 ===')
    
    // 修复网格索引
    const fixedMeshData = fixMeshIndices(meshData)
    
    // 验证几何体
    if (!geometry || !geometry.attributes || !geometry.attributes.position) {
      console.error('几何体无效或缺少位置属性')
      return
    }
    
    console.log('几何体验证:')
    console.log('- position count:', geometry.attributes.position.count)
    
    if (fixedMeshData.triangular_faces && fixedMeshData.triangular_faces.length > 0) {
      console.log('处理修复后的三角面数据...')
      
      const faces = fixedMeshData.triangular_faces
      const flatFaces = faces.flat()
      console.log('- 修复后面数据长度:', faces.length)
      console.log('- 扁平化后长度:', flatFaces.length)
      console.log('- 前9个面索引:', flatFaces.slice(0, 9))
      
      if (flatFaces.length === 0 || flatFaces.length % 3 !== 0) {
        console.error('三角面数据格式错误:', flatFaces.length)
        return
      }
      
      // 验证索引范围
      const vertexCount = geometry.attributes.position.count
      const invalidIndices = flatFaces.filter(index => 
        typeof index !== 'number' || 
        !Number.isInteger(index) || 
        index < 0 || 
        index >= vertexCount
      )
      
      if (invalidIndices.length > 0) {
        console.error('面数据包含无效索引:', invalidIndices.slice(0, 10))
        console.error('顶点数量:', vertexCount)
        return
      }
      
      // 设置索引
      geometry.setIndex(flatFaces)
      
      // 计算法向量
      geometry.computeVertexNormals()
      
      // 创建材质
      const material = new THREE.MeshLambertMaterial({ 
        color: meshOptions.solidColor,
        transparent: true,
        opacity: meshOptions.opacity,
        side: THREE.DoubleSide
      })
      
      // 创建网格
      const mesh = new THREE.Mesh(geometry, material)
      mesh.name = 'solid'
      meshGroup.add(mesh)
      
      // 适配相机到网格
      fitCameraToMesh(meshData.vertices)
      
      console.log('=== renderSolid 完成 ===')
    } else {
      console.log('没有三角面数据，跳过实体渲染')
    }
  } catch (error) {
    console.error('渲染实体时出错:', error)
    console.error('错误类型:', error.constructor.name)
    console.error('错误消息:', error.message)
    console.error('错误堆栈:', error.stack)
    ElMessage.error('渲染实体时出错: ' + error.message)
  }
}

// 点云模式渲染
const renderPoints = (geometry) => {
  try {
    // 验证几何体
    if (!geometry || !geometry.attributes || !geometry.attributes.position) {
      console.error('几何体无效或缺少位置属性')
      return
    }
    
    const material = new THREE.PointsMaterial({ 
      color: 0xff0000, 
      size: 2 
    })
    const points = new THREE.Points(geometry, material)
    scene.add(points)
  } catch (error) {
    console.error('渲染点云时出错:', error)
    ElMessage.error('渲染点云时出错: ' + error.message)
  }
}

// 节点渲染
const renderNodes = (geometry) => {
  try {
    const nodesMaterial = new THREE.PointsMaterial({ 
      color: 0xff0000, 
      size: 3,
      sizeAttenuation: false
    })
    const nodes = new THREE.Points(geometry, nodesMaterial)
    nodes.name = 'nodes'
    scene.add(nodes)
    console.log('节点渲染完成')
  } catch (error) {
    console.error('渲染节点时出错:', error)
  }
}

// 更新网格显示
const updateMeshDisplay = () => {
  if (meshVisualizationData.value) {
    renderMeshData(meshVisualizationData.value.mesh_data)
  }
}

// 显示模式切换 - 优化为线框优先
const switchDisplayMode = (mode) => {
  displayMode.value = mode
  console.log('切换显示模式到:', mode)
  
  // 根据模式调整默认设置
  switch (mode) {
    case 'wireframe':
      meshOptions.wireframeOnly = true
      meshOptions.showEdges = true
      break
    case 'solid':
      meshOptions.wireframeOnly = false
      meshOptions.showEdges = false
      break
    case 'mixed':
      meshOptions.wireframeOnly = false
      meshOptions.showEdges = true
      break
    case 'points':
      meshOptions.showNodes = true
      break
  }
  
  if (meshVisualizationData.value) {
    renderMeshData(meshVisualizationData.value.mesh_data)
  }
}

// 初始化Three.js场景
const initThreeJS = () => {
  try {
    if (!threeContainer.value) {
      console.error('Three.js容器未找到')
      return false
    }

    // 如果已经初始化过，先清理
    if (renderer && threeContainer.value.contains(renderer.domElement)) {
      threeContainer.value.removeChild(renderer.domElement)
    }

    // 创建场景
    scene = new THREE.Scene()
    scene.background = new THREE.Color(0xf0f0f0)

    // 创建相机
    const container = threeContainer.value
    const width = container.clientWidth || 800
    const height = container.clientHeight || 700
    
    camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 1000)
    camera.position.set(5, 5, 5)

    // 创建渲染器
    renderer = new THREE.WebGLRenderer({ antialias: true })
    renderer.setSize(width, height)
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFSoftShadowMap
    
    container.appendChild(renderer.domElement)

    // 添加控制器
    controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.05

    // 添加光源
    const ambientLight = new THREE.AmbientLight(0x404040, 0.6)
    scene.add(ambientLight)

    const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8)
    directionalLight.position.set(10, 10, 5)
    directionalLight.castShadow = true
    scene.add(directionalLight)

    // 添加网格
    const gridHelper = new THREE.GridHelper(10, 10)
    scene.add(gridHelper)

    // 渲染循环
    const animate = () => {
      requestAnimationFrame(animate)
      controls.update()
      renderer.render(scene, camera)
    }
    animate()

    console.log('Three.js初始化成功')
    return true
  } catch (error) {
    console.error('Three.js初始化失败:', error)
    return false
  }
}

// 适配相机到网格
const fitCameraToMesh = (vertices, coordRange = null) => {
  if (!vertices || vertices.length === 0) return

  // 计算边界框
  let minX = Infinity, minY = Infinity, minZ = Infinity
  let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity

  for (const vertex of vertices) {
    const [x, y, z] = Array.isArray(vertex) ? vertex : [vertex, 0, 0]
    minX = Math.min(minX, x)
    minY = Math.min(minY, y)
    minZ = Math.min(minZ, z)
    maxX = Math.max(maxX, x)
    maxY = Math.max(maxY, y)
    maxZ = Math.max(maxZ, z)
  }

  // 计算中心点和尺寸
  const centerX = (minX + maxX) / 2
  const centerY = (minY + maxY) / 2
  const centerZ = (minZ + maxZ) / 2
  
  const sizeX = maxX - minX
  const sizeY = maxY - minY
  const sizeZ = maxZ - minZ
  const maxSize = Math.max(sizeX, sizeY, sizeZ)

  console.log(`模型边界框: [${minX.toFixed(3)}, ${minY.toFixed(3)}, ${minZ.toFixed(3)}] 到 [${maxX.toFixed(3)}, ${maxY.toFixed(3)}, ${maxZ.toFixed(3)}]`)
  console.log(`模型中心: [${centerX.toFixed(3)}, ${centerY.toFixed(3)}, ${centerZ.toFixed(3)}]`)
  console.log(`模型尺寸: ${sizeX.toFixed(3)} x ${sizeY.toFixed(3)} x ${sizeZ.toFixed(3)}`)

  // 设置相机位置
  const distance = maxSize * 2.5 // 适当的观察距离
  camera.position.set(
    centerX + distance * 0.7,
    centerY + distance * 0.7,
    centerZ + distance * 0.7
  )
  
  // 设置相机目标
  controls.target.set(centerX, centerY, centerZ)
  
  // 更新控制器
  controls.update()
  
  console.log(`相机位置: [${camera.position.x.toFixed(3)}, ${camera.position.y.toFixed(3)}, ${camera.position.z.toFixed(3)}]`)
  console.log(`相机目标: [${controls.target.x.toFixed(3)}, ${controls.target.y.toFixed(3)}, ${controls.target.z.toFixed(3)}]`)
}

// 重置相机视角
const resetCamera = () => {
  if (!camera || !controls) {
    ElMessage.warning('3D场景未初始化')
    return
  }
  
  // 如果有网格数据，重新适配相机
  if (meshVisualizationData.value && meshVisualizationData.value.mesh_data && meshVisualizationData.value.mesh_data.vertices) {
    fitCameraToMesh(meshVisualizationData.value.mesh_data.vertices)
  } else {
    // 默认相机位置
    camera.position.set(5, 5, 5)
    controls.target.set(0, 0, 0)
    controls.update()
  }
  
  ElMessage.success('视角已重置')
}

// 生命周期钩子
onMounted(() => {
  // 在组件挂载后立即初始化Three.js容器
  nextTick(() => {
    if (threeContainer.value) {
      try {
        initThreeJS()
        console.log('Three.js在组件挂载时初始化成功')
      } catch (error) {
        console.error('Three.js在组件挂载时初始化失败:', error)
      }
    }
  })
})

onUnmounted(() => {
  // 清理Three.js资源
  if (renderer) {
    renderer.dispose()
  }
  if (controls) {
    controls.dispose()
  }
})
</script>

<style scoped>
.mesh-generator {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.layout-container {
  display: flex;
  flex: 1;
  gap: 20px;
  padding: 20px;
}

.left-sidebar {
  width: 280px;
  flex-shrink: 0;
}

.center-area {
  flex: 1;
  min-width: 0;
}

.mesh-config-form {
  padding: 0 8px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  line-height: 1.4;
}

.config-card, .visualization-card {
  height: fit-content;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.action-buttons {
  margin-top: 24px;
  padding: 0 8px;
}

.visualization-content {
  min-height: 700px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.three-container {
  width: 100%;
  height: 700px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}



.loading-container {
  text-align: center;
}

.loading-container p {
  margin-top: 10px;
  color: #606266;
}



.no-model, .no-mesh, .error-visualization {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 600px;
}
</style>
