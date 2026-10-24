<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { useThemeStore } from '@/store/theme'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import MobileBar from '@/components/layout/MobileBar.vue'

const userStore = useUserStore()
const themeStore = useThemeStore()

onMounted(async () => {
  themeStore.start()
  await Promise.all([userStore.restore(), userStore.loadDashboard()])
})
</script>

<template>
  <!-- 左侧栏工作台版式（首页即工作台） -->
  <div class="flex h-screen overflow-hidden bg-[var(--k-bg)]">
    <AppSidebar />

    <div class="flex min-w-0 flex-1 flex-col">
      <MobileBar />

      <main class="flex-1 overflow-y-auto">
        <div class="mx-auto w-full max-w-[1180px] px-4 py-6 md:px-8 md:py-8">
          <RouterView />
        </div>

        <footer class="mx-auto w-full max-w-[1180px] px-4 pb-8 md:px-8">
          <div
            class="flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-[var(--k-border)] pt-5 text-xs leading-relaxed text-[var(--k-text-faint)]"
          >
            <p>考研 408 工作台 · 真题 2009–2026 共 18 卷。</p>
            <!--
              内容来源说明（simple408，CC BY 4.0）的完整声明放在仓库的 LICENSE 与 README 中。
              CC BY 4.0 只要求「以合理方式署名」，未要求逐页展示，因此此处仅保留一句指路。
            -->
            <RouterLink to="/about" class="text-[var(--k-text-faint)] no-underline hover:text-[var(--k-accent)]">
              数据说明
            </RouterLink>
          </div>
        </footer>
      </main>
    </div>
  </div>
</template>