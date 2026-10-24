<template>
  <div class="server-monitor-container">
    <el-row :gutter="20">
      <!-- 服务健康状态 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>服务健康状态</span>
            </div>
          </template>
          <div class="health-info">
            <el-tag :type="healthStatus === 'UP' ? 'success' : 'danger'" size="large">
              {{ healthStatus }}
            </el-tag>
            <el-descriptions :column="1" border style="margin-top: 20px;">
              <el-descriptions-item label="数据库">
                <el-tag :type="dbStatus === 'UP' ? 'success' : 'danger'">{{ dbStatus }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="Redis">
                <el-tag :type="redisStatus === 'UP' ? 'success' : 'danger'">{{ redisStatus }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="磁盘空间">
                <el-tag :type="diskStatus === 'UP' ? 'success' : 'danger'">{{ diskStatus }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>

      <!-- JVM信息 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>JVM信息</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Java版本">{{ jvmInfo.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="JVM名称">{{ jvmInfo.jvmName }}</el-descriptions-item>
            <el-descriptions-item label="启动时间">{{ jvmInfo.startTime }}</el-descriptions-item>
            <el-descriptions-item label="运行时长">{{ jvmInfo.uptime }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 内存使用 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>内存使用</span>
            </div>
          </template>
          <div class="memory-info">
            <el-progress type="dashboard" :percentage="memoryUsage" :color="colors" />
            <div class="memory-detail">
              <p>已用: {{ formatBytes(memory.used) }}</p>
              <p>最大: {{ formatBytes(memory.max) }}</p>
              <p>总计: {{ formatBytes(memory.total) }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统指标 -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>系统指标</span>
          <el-button type="primary" @click="refreshMetrics">刷新</el-button>
        </div>
      </template>
      <el-table :data="metricsList" border stripe>
        <el-table-column prop="name" label="指标名称" min-width="200" />
        <el-table-column prop="value" label="当前值" width="150" />
        <el-table-column prop="description" label="说明" min-width="300" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getServerHealth, getServerMetrics, getServerInfo } from '@/api/monitor'

const healthStatus = ref('UNKNOWN')
const dbStatus = ref('UNKNOWN')
const redisStatus = ref('UNKNOWN')
const diskStatus = ref('UNKNOWN')

const jvmInfo = reactive({
  javaVersion: '-',
  jvmName: '-',
  startTime: '-',
  uptime: '-'
})

const memory = reactive({
  used: 0,
  max: 0,
  total: 0
})

const memoryUsage = ref(0)
const colors = [
  { color: '#00b42a', percentage: 60 },
  { color: '#ff7d00', percentage: 80 },
  { color: '#f53f3f', percentage: 100 }
]

const metricsList = ref([])

const fetchHealth = async () => {
  try {
    const res = await getServerHealth()
    healthStatus.value = res.data.status || 'UNKNOWN'
    
    // 解析组件状态
    if (res.data.components) {
      dbStatus.value = res.data.components.db?.status || 'UNKNOWN'
      redisStatus.value = res.data.components.redis?.status || 'UNKNOWN'
      diskStatus.value = res.data.components.diskSpace?.status || 'UNKNOWN'
    }
  } catch (error) {
    console.error('获取健康状态失败', error)
  }
}

const fetchMetrics = async () => {
  try {
    const res = await getServerMetrics()
    // 解析指标数据
    metricsList.value = [
      { name: 'jvm.memory.used', value: formatBytes(res.data['jvm.memory.used']?.value || 0), description: 'JVM已用内存' },
      { name: 'jvm.memory.max', value: formatBytes(res.data['jvm.memory.max']?.value || 0), description: 'JVM最大内存' },
      { name: 'process.cpu.usage', value: ((res.data['process.cpu.usage']?.value || 0) * 100).toFixed(2) + '%', description: 'CPU使用率' },
      { name: 'system.cpu.count', value: res.data['system.cpu.count']?.value || 0, description: 'CPU核心数' }
    ]
    
    // 更新内存信息
    memory.used = res.data['jvm.memory.used']?.value || 0
    memory.max = res.data['jvm.memory.max']?.value || 0
    memory.total = res.data['jvm.memory.committed']?.value || 0
    memoryUsage.value = memory.max > 0 ? Math.round((memory.used / memory.max) * 100) : 0
  } catch (error) {
    console.error('获取指标失败', error)
  }
}

const fetchInfo = async () => {
  try {
    const res = await getServerInfo()
    if (res.data.java) {
      jvmInfo.javaVersion = res.data.java.version || '-'
      jvmInfo.jvmName = res.data.java.vmName || '-'
      jvmInfo.startTime = res.data.java.startTime || '-'
      jvmInfo.uptime = res.data.java.uptime || '-'
    }
  } catch (error) {
    console.error('获取信息失败', error)
  }
}

const refreshMetrics = () => {
  fetchHealth()
  fetchMetrics()
  fetchInfo()
}

const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

onMounted(() => {
  refreshMetrics()
  // 每30秒自动刷新
  setInterval(refreshMetrics, 30000)
})
</script>

<style scoped>
.server-monitor-container {
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.health-info {
  text-align: center;
  padding: 20px 0;
}

.memory-info {
  text-align: center;
  padding: 20px 0;
}

.memory-detail {
  margin-top: 20px;
  font-size: 14px;
  color: #606266;
}

.memory-detail p {
  margin: 5px 0;
}
</style>