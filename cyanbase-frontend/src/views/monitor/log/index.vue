<template>
  <div class="log-container">
    <el-card shadow="hover">
      <template #header>
        <span>日志监控</span>
      </template>
      <el-empty description="暂无日志数据">
        <el-button type="primary">刷新</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
</script>

<style scoped>
.log-container { width: 100%; }
</style>