<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import mark from '@/assets/brand/brand-mark.svg'
import markLight from '@/assets/brand/brand-mark-light.svg'

/**
 * 品牌标记（三环图形，不含文字）。
 *
 * 提供两份：深色环版用于浅色底，白环版用于深色底。
 * 之前用 CSS filter 强制反色来凑，颜色会偏灰、青绿也被打白；
 * 有了品牌方给的白环稿就不再需要那种取巧做法。
 *
 * 主题切换与 BrandLogo 一致：监听 html 的 class 变化。
 * 起因见 BrandLogo 的注释——scoped 里用 :global(.dark) 组合会被编译拆散。
 */
withDefaults(
  defineProps<{
    size?: number
  }>(),
  { size: 32 },
)

const isDark = ref(false)
let observer: MutationObserver | null = null

function sync() {
  isDark.value = document.documentElement.classList.contains('dark')
}

onMounted(() => {
  sync()
  observer = new MutationObserver(sync)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

onUnmounted(() => {
  observer?.disconnect()
  observer = null
})

const src = computed(() => (isDark.value ? markLight : mark))
</script>

<template>
  <img
    :src="src"
    :style="{ width: `${size}px`, height: `${size}px` }"
    class="brand-mark shrink-0"
    alt="题知链"
  />
</template>