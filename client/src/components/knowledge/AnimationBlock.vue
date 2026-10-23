<script setup lang="ts">
import { defineAsyncComponent, onMounted, ref, shallowRef } from 'vue'
import { loadAnimation } from './animationLoader'

/**
 * 单个动画块：进入视口后才加载动画代码与 manim/three 引擎。
 *
 * 这是体积优化的关键——知识正文里动画往往在折叠的下方，
 * 若在页面挂载时就全部加载，打开任何一篇文章都要下载约 1.8MB。
 *
 * 这里用 IntersectionObserver：只有当动画块接近可视区域时才 import，
 * 且同一动画只加载一次（loadAnimation 内部有缓存与并发去重）。
 */
const props = defineProps<{
  animationId: string | null
  ariaLabel?: string | null
}>()

/** 播放器本身也异步（它静态引用了 manim-web）。 */
const ManimCodePlayer = defineAsyncComponent(() => import('./ManimCodePlayer.vue'))

const host = ref<HTMLElement | null>(null)
const animation = shallowRef<unknown | null>(null)
const state = ref<'idle' | 'loading' | 'ready' | 'missing'>('idle')
let observer: IntersectionObserver | null = null

async function load() {
  if (state.value !== 'idle') return
  state.value = 'loading'
  const result = await loadAnimation(props.animationId)
  if (result) {
    animation.value = result
    state.value = 'ready'
  } else {
    state.value = 'missing'
  }
}

onMounted(() => {
  if (!host.value) return
  // 不支持 IntersectionObserver 时直接加载，保证功能可用
  if (typeof IntersectionObserver === 'undefined') {
    void load()
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        observer?.disconnect()
        observer = null
        void load()
      }
    },
    // 提前 300px 预加载，滚动到位时已经就绪
    { rootMargin: '300px 0px' },
  )
  observer.observe(host.value)
})

import { onUnmounted } from 'vue'
onUnmounted(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div ref="host">
    <ManimCodePlayer v-if="state === 'ready' && animation" :animation="animation as never" />

    <div
      v-else-if="state === 'loading'"
      class="flex h-40 items-center justify-center rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] text-[13px] text-[var(--k-text-faint)]"
    >
      正在加载交互动画…
    </div>

    <div
      v-else-if="state === 'missing'"
      class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface-soft)] px-4 py-6 text-center text-[13px] text-[var(--k-text-faint)]"
    >
      动画不可用（{{ props.animationId ?? '未登记 id' }}）
    </div>

    <!-- idle：尚未进入视口，占位保持布局高度，避免滚动跳动 -->
    <div
      v-else
      class="flex h-40 items-center justify-center rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] text-[13px] text-[var(--k-text-faint)]"
      :aria-label="props.ariaLabel ?? '交互动画'"
    >
      交互动画（滚动到此处自动加载）
    </div>
  </div>
</template>