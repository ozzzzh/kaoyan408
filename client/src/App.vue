<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import MobileBar from '@/components/layout/MobileBar.vue'

const userStore = useUserStore()

onMounted(async () => {
  await Promise.all([userStore.restore(), userStore.loadDashboard()])
})
</script>

<template>
  <!-- 左侧栏工作台版式（首页即工作台） -->
  <div class="flex h-screen overflow-hidden bg-[#f7faff]">
    <AppSidebar />

    <div class="flex min-w-0 flex-1 flex-col">
      <MobileBar />

      <main class="flex-1 overflow-y-auto">
        <div class="mx-auto w-full max-w-[1180px] px-4 py-6 md:px-8 md:py-8">
          <RouterView />
        </div>

        <footer class="mx-auto w-full max-w-[1180px] px-4 pb-8 md:px-8">
          <div class="border-t border-[#e2e9f5] pt-5 text-xs leading-relaxed text-[#9aa9c3]">
            <p>考研 408 工作台 · 真题 2009–2026 共 18 卷。</p>
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