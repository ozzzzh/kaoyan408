<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import logoLight from '@/assets/brand/brand-logo.svg'
import logoDark from '@/assets/brand/brand-logo-light.svg'
import logoTagiine from '@/assets/brand/brand-logo-tagline.svg'

/**
 * 品牌锁定版（三环 + 题知链 408）。
 *
 * 主题切换用 JS 监听 html 的 class 变化来切换图片源。
 *
 * 为什么不用纯 CSS：在 scoped 样式里用 `:global(.dark) .foo` 组合，
 * Vue 编译后会把选择器拆成 `.foo[data-v-x], .dark { ... }`，
 * `.dark` 变成独立规则，导致整个组件被隐藏（实测踩到，夜间模式 Logo 直接消失）。
 * 监听 class 变化虽然多几行代码，但行为确定、不受编译细节影响。
 *
 * 图形与文字都已转曲（不依赖字体），任何设备渲染一致。
 */
const props = withDefaults(
  defineProps<{
    /** 高度，宽度按原比例自适应。32-36 适合侧边栏，56+ 适合登录页。 */
    height?: number
    /** 是否带标语「题知互链 · 数据驱动 · 学练测规划闭环」。 */
    tagline?: boolean
  }>(),
  { height: 32, tagline: false },
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

const src = computed(() => {
  if (isDark.value) return logoDark
  return props.tagline ? logoTagiine : logoLight
})

const alt = computed(() =>
  props.tagline ? '题知链 408 · 题知互链 · 数据驱动 · 学练测规划闭环' : '题知链 408',
)
</script>

<template>
  <img :src="src" :style="{ height: `${height}px` }" class="brand-lockup w-auto" :alt="alt" />
</template>