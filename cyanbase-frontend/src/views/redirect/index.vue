<script setup>
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsViewStore } from '@/store/tagsView'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsViewStore()

// 刷新标签逻辑：移除缓存 → replace 到目标路由
onMounted(() => {
  const { params, query } = route
  // params.path 是一个数组（因为 :path(.*)）
  const realPath = '/' + (Array.isArray(params.path) ? params.path.join('/') : (params.path || ''))
  tagsStore.removeCachedView(route.name)
  // replace 后前进历史不会增加
  router.replace({ path: realPath, query })
})
</script>

<template>
  <div />
</template>
