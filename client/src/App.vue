<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useThemeStore } from '@/store/theme'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import MobileBar from '@/components/layout/MobileBar.vue'

const userStore = useUserStore()
const themeStore = useThemeStore()
const route = useRoute()

/**
 * 是否使用「无外壳」版式。
 *
 * 落地页是面向站外的营销页，自带顶栏与页脚，套上工作台侧边栏会变成
 * 「营销页里嵌了个后台」的怪异结构。路由里用 meta.bare 标记，
 * 这里据此决定是否渲染侧边栏与外层容器。
 */
const bare = computed(() => route.meta.bare === true)

onMounted(async () => {
  themeStore.start()
  await Promise.all([userStore.restore(), userStore.loadDashboard()])
})
</script>

<template>
  <!-- 落地页：整页自行排版，不套工作台外壳 -->
  <RouterView v-if="bare" />

  <!-- 左侧栏工作台版式 -->
  <div v-else class="flex h-screen overflow-hidden bg-[var(--k-bg)]">
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
            <p>题知链 408 · 真题 2009–2026 共 18 卷。</p>
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