<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import MobileBar from '@/components/layout/MobileBar.vue'

const route = useRoute()
const userStore = useUserStore()
const { dashboard } = storeToRefs(userStore)

/** 首页是整幅 Hero 版式，用它自己的全宽背景，不套内容区留白。 */
const isHome = computed(() => route.name === 'home')

onMounted(async () => {
  await Promise.all([userStore.restore(), userStore.loadDashboard()])
})
</script>

<template>
  <!-- 首页：独立 Hero 版式（保留鼠标跟随背景） -->
  <div v-if="isHome" class="min-h-screen">
    <RouterView />
  </div>

  <!-- 其余页面：左侧栏工作台版式 -->
  <div v-else class="flex h-screen overflow-hidden bg-[#f7faff]">
    <AppSidebar />

    <div class="flex min-w-0 flex-1 flex-col">
      <MobileBar />

      <main class="flex-1 overflow-y-auto">
        <div class="mx-auto w-full max-w-[1180px] px-4 py-6 md:px-8 md:py-8">
          <RouterView />
        </div>

        <footer class="mx-auto w-full max-w-[1180px] px-4 pb-8 md:px-8">
          <div class="border-t border-[#e2e9f5] pt-5 text-xs leading-relaxed text-[#9aa9c3]">
            <p>408 考盐 · 真题 2009–2026 共 18 卷。</p>
            <p class="mt-1">
              知识正文与真题数据源自
              <a
                class="text-[#1f49ba] hover:underline"
                href="https://github.com/liangbohan/postgraduate-exam-website"
                target="_blank"
                rel="noreferrer"
                >simple408</a
              >
              （CC BY 4.0），本站为复习用途的二次组织。
            </p>
          </div>
        </footer>
      </main>
    </div>
  </div>
</template>