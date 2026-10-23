<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/store/theme'

const theme = useThemeStore()

const label = computed(() => {
  if (theme.mode === 'auto') return '跟随时间'
  return theme.mode === 'dark' ? '夜间' : '日间'
})

const icon = computed(() => {
  if (theme.mode === 'auto') return 'auto'
  return theme.mode === 'dark' ? 'moon' : 'sun'
})
</script>

<template>
  <button
    class="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] hover:text-[var(--k-text)]"
    :title="`当前：${label}${theme.mode === 'auto' ? ` · ${theme.nextSwitchHint}` : ''}（点击切换 自动/日间/夜间）`"
    @click="theme.cycle()"
  >
    <!-- 自动：时钟 -->
    <svg
      v-if="icon === 'auto'"
      class="h-[18px] w-[18px] shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
    <!-- 日间：太阳 -->
    <svg
      v-else-if="icon === 'sun'"
      class="h-[18px] w-[18px] shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
    >
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M19 5l-1.5 1.5M6.5 17.5 5 19" />
    </svg>
    <!-- 夜间：月亮 -->
    <svg
      v-else
      class="h-[18px] w-[18px] shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
    >
      <path d="M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5Z" />
    </svg>
    <span class="text-sm font-semibold">{{ label }}</span>
    <span v-if="theme.mode === 'auto'" class="ml-auto text-[10px] text-[var(--k-text-faint)]">
      {{ theme.isDark ? '夜间' : '日间' }}
    </span>
  </button>
</template>