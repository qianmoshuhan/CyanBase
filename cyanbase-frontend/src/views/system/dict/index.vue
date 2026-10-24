<template>
  <div class="dict-container">
    <el-card shadow="hover">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- Tab 1：字典类型管理 -->
        <el-tab-pane label="字典类型管理" name="type">
          <DictType />
        </el-tab-pane>

        <!-- Tab 2：字典数据管理 -->
        <el-tab-pane label="字典数据管理" name="data">
          <DictData />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import DictType from './type/index.vue'
import DictData from './data/index.vue'

const route = useRoute()

// 根据当前路由初始化激活的 Tab（侧边栏点进来时能直接落到对应 Tab）
// 纯读取，不做路由写入 — 对齐 workflow/start 的简单风格
const activeTab = ref(route.path.includes('/dict/data') ? 'data' : 'type')

// Tab 切换时按需触发（这里暂时不需要额外数据加载，保持空函数对齐）
const onTabChange = (name) => {
  // 预留：如果 Tab2 需要切换时才拉数据，这里写 fetchXxxList()
}
</script>

<style scoped>
.dict-container {
  width: 100%;
}
</style>