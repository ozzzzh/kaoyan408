<script setup lang="ts">
/**
 * 落地页专用顶栏。
 *
 * 与工作台的 AppSidebar / MobileBar 不是同一套：落地页没有侧边栏，
 * 需要的是「横向导航 + 主 CTA」。因此单独一个组件，避免把两套版式
 * 塞进同一个文件里互相迁就。
 */
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/store/theme'
import BrandLogo from '@/components/brand/BrandLogo.vue'

const themeStore = useThemeStore()
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)

const themeLabel = computed(() => {
  if (themeStore.mode === 'auto') return themeStore.isDark ? '跟随时间 · 夜间' : '跟随时间 · 日间'
  return themeStore.mode === 'dark' ? '夜间' : '日间'
})

/** 复用 store 的循环逻辑，保证与工作台的切换顺序一致 */
function cycleTheme() {
  themeStore.cycle()
}

/**
 * 页内跳转。
 *
 * 不能用 href="#loop"：本项目是 hash 路由，# 之后的内容会被当成路由路径，
 * 于是跳到 /loop 并 404。改为写 query（#/welcome?section=loop）再滚动，
 * 地址栏能反映当前段落，链接也能直接分享。
 *
 * 顺序上必须先滚动再 replace：replace 会触发全局 scrollBehavior
 * （返回 { top: 0 }），后滚动会被拉回页首，表现为「点了没反应」。
 */
function scrollTo(id: string) {
  mobileOpen.value = false
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  router.replace({ name: 'landing', query: { ...route.query, section: id } }).catch(() => {})
}

const navItems = [
  { id: 'loop', label: '核心闭环' },
  { id: 'abilities', label: '四大能力' },
  { id: 'about-data', label: '数据说明' },
] as const
</script>

<template>
  <header
    class="sticky top-0 z-40 border-b border-[var(--k-border)] bg-[var(--k-bg)]/85 backdrop-blur-md"
  >
    <div class="mx-auto flex h-[64px] w-full max-w-[1200px] items-center gap-6 px-5 md:h-[78px] md:px-8">
      <RouterLink to="/" class="shrink-0 no-underline" aria-label="题知链 408">
        <BrandLogo />
      </RouterLink>

      <!-- 桌面导航 -->
      <nav class="hidden items-center gap-1 md:flex">
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          class="rounded-lg px-3 py-2 text-sm text-[var(--k-text-muted)] transition-colors hover:bg-[var(--k-surface-soft)] hover:text-[var(--k-text)]"
          @click="scrollTo(item.id)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="ml-auto flex items-center gap-2">
        <button
          type="button"
          class="grid h-9 w-9 place-items-center rounded-lg border border-[var(--k-border)] text-[var(--k-text-muted)] transition-colors hover:text-[var(--k-text)]"
          :title="`主题：${themeLabel}（点击切换）`"
          :aria-label="`主题：${themeLabel}`"
          @click="cycleTheme"
        >
          <svg
            v-if="themeStore.isDark"
            viewBox="0 0 24 24"
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
          >
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" stroke-linecap="round" />
          </svg>
          <svg
            v-else
            viewBox="0 0 24 24"
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
          >
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4" stroke-linecap="round" />
          </svg>
        </button>

        <RouterLink
          to="/"
          class="hidden items-center gap-1.5 rounded-[11px] bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-[var(--k-on-accent)] no-underline transition-colors hover:bg-[var(--k-accent-hover)] sm:inline-flex"
        >
          进入工作台
          <span aria-hidden="true">→</span>
        </RouterLink>

        <!-- 移动端菜单 -->
        <button
          type="button"
          class="grid h-9 w-9 place-items-center rounded-lg border border-[var(--k-border)] text-[var(--k-text-muted)] md:hidden"
          :aria-expanded="mobileOpen"
          aria-label="打开菜单"
          @click="mobileOpen = !mobileOpen"
        >
          <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
            <path v-if="!mobileOpen" d="M4 7h16M4 12h16M4 17h16" stroke-linecap="round" />
            <path v-else d="M6 6l12 12M18 6L6 18" stroke-linecap="round" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 移动端展开区 -->
    <div v-if="mobileOpen" class="border-t border-[var(--k-border)] bg-[var(--k-surface)] px-5 py-3 md:hidden">
      <button
        v-for="item in navItems"
        :key="item.id"
        type="button"
        class="block w-full rounded-lg px-2 py-2.5 text-left text-sm text-[var(--k-text-muted)]"
        @click="scrollTo(item.id)"
      >
        {{ item.label }}
      </button>
      <RouterLink
        to="/"
        class="mt-2 block rounded-[11px] bg-[var(--k-accent)] px-4 py-2.5 text-center text-sm font-semibold text-[var(--k-on-accent)] no-underline"
      >
        进入工作台 →
      </RouterLink>
    </div>
  </header>
</template>