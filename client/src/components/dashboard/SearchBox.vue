<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { SearchSuggestion } from '@/api/types'

/**
 * 工作台搜索框：输入时给出联想，回车进入搜索页。
 * 带 220ms 防抖，避免每敲一个字都发请求。
 */
const router = useRouter()

const keyword = ref('')
const suggestions = ref<SearchSuggestion[]>([])
const open = ref(false)
const activeIndex = ref(-1)

let timer: ReturnType<typeof setTimeout> | null = null
/** 只接受最后一次请求的结果，防止慢响应覆盖新结果 */
let requestToken = 0

watch(keyword, (value) => {
  if (timer) clearTimeout(timer)
  const trimmed = value.trim()
  if (!trimmed) {
    suggestions.value = []
    open.value = false
    return
  }
  timer = setTimeout(async () => {
    const token = ++requestToken
    try {
      const list = await api.searchSuggest(trimmed)
      if (token !== requestToken) return
      suggestions.value = list
      open.value = list.length > 0
      activeIndex.value = -1
    } catch {
      if (token === requestToken) {
        suggestions.value = []
        open.value = false
      }
    }
  }, 220)
})

function go(to: string) {
  open.value = false
  keyword.value = ''
  void router.push(to)
}

function submit() {
  const trimmed = keyword.value.trim()
  if (!trimmed) return
  open.value = false
  keyword.value = ''
  void router.push({ path: '/search', query: { q: trimmed } })
}

function move(delta: number) {
  if (!suggestions.value.length) return
  const next = activeIndex.value + delta
  activeIndex.value = next < 0 ? suggestions.value.length - 1 : next % suggestions.value.length
}

function chooseActive() {
  const item = suggestions.value[activeIndex.value]
  if (item) go(item.to)
  else submit()
}

function onBlur() {
  // 延迟关闭，让点击候选项先触发
  setTimeout(() => {
    open.value = false
  }, 160)
}

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <div class="relative min-w-0 flex-1">
    <div class="flex h-12 items-center gap-2.5 rounded-xl px-4">
      <svg
        class="h-[18px] w-[18px] shrink-0 text-[var(--k-text-faint)]"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" />
      </svg>
      <input
        v-model="keyword"
        class="w-full border-0 bg-transparent text-[15px] text-[var(--k-text)] outline-0 placeholder:text-[var(--k-text-faint)]"
        placeholder="搜索知识点、题目或正文（如 KMP / 银行家算法 / 2026 3）"
        @focus="open = suggestions.length > 0"
        @blur="onBlur"
        @keydown.enter.prevent="chooseActive"
        @keydown.down.prevent="move(1)"
        @keydown.up.prevent="move(-1)"
        @keydown.esc="open = false"
      />
    </div>

    <!-- 联想下拉 -->
    <div
      v-if="open && suggestions.length"
      class="absolute left-0 right-0 top-[calc(100%+6px)] z-30 overflow-hidden rounded-xl border border-[var(--k-border-strong)] bg-[var(--k-surface)] shadow-[0_14px_36px_rgba(15,30,70,0.14)]"
    >
      <button
        v-for="(item, i) in suggestions"
        :key="item.to + item.label"
        class="flex w-full items-center gap-3 px-4 py-2.5 text-left transition"
        :class="i === activeIndex ? 'bg-[var(--k-accent-soft)]' : 'hover:bg-[var(--k-surface-soft)]'"
        @mousedown.prevent="go(item.to)"
        @mouseenter="activeIndex = i"
      >
        <span
          class="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-semibold"
          :class="
            item.type === 'point'
              ? 'bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]'
              : 'bg-[var(--k-chip)] text-[var(--k-text-muted)]'
          "
        >
          {{ item.type === 'point' ? '知识点' : '题目' }}
        </span>
        <span class="min-w-0 flex-1 truncate text-sm text-[var(--k-text)]">{{ item.label }}</span>
        <span class="shrink-0 text-[11px] text-[var(--k-text-faint)]">{{ item.hint }}</span>
      </button>
      <button
        class="flex w-full items-center gap-2 border-t border-[var(--k-border)] px-4 py-2 text-left text-xs text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)]"
        @mousedown.prevent="submit"
      >
        <span>查看「{{ keyword }}」的全部结果</span>
        <span class="ml-auto text-[var(--k-text-faint)]">回车</span>
      </button>
    </div>
  </div>
</template>