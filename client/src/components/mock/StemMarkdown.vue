<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { containsMath, renderStemCore } from '@/utils/stem'

/**
 * 题干渲染：轻量 Markdown（代码块 / 行内代码 / 加粗 / 换行）+ 按需公式。
 *
 * 历史变更：这里原本走完整的 marked 链路（marked + DOMPurify + KaTeX 一起打进
 * 一个 328KB 的 chunk），而题干根本用不到完整 GFM。现在：
 *   - 基础渲染由 utils/stem.ts 完成（纯转义 + 固定标签，无第三方依赖）；
 *   - 只有题干里真的出现 $...$ / \(...\) 时，才动态加载 KaTeX（utils/htmlMath.ts）。
 * 绝大多数题目的刷题页因此不再下载那 328KB。
 */
const props = defineProps<{
  source: string
}>()

const html = ref('')
/** 公式模块加载中；此期间先显示基础渲染结果，避免空白 */
let seq = 0

watch(
  () => props.source,
  async (source) => {
    const current = ++seq
    const core = renderStemCore(source)
    html.value = core
    if (!containsMath(source)) return
    try {
      const { renderMathInHtml } = await import('@/utils/htmlMath')
      if (current === seq) html.value = renderMathInHtml(core)
    } catch {
      // 公式模块加载失败就保留基础渲染，不影响读题
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  seq++
})

const rendered = computed(() => html.value)
</script>

<template>
  <div class="stem-md text-sm leading-relaxed text-[var(--k-text)]" v-html="rendered" />
</template>

<style scoped>
.stem-md :deep(p) {
  margin: 0 0 0.5rem;
}

.stem-md :deep(p:last-child) {
  margin-bottom: 0;
}

/* 代码块：题干里的程序片段需要等宽字体与独立背景，否则读不清 */
.stem-md :deep(pre) {
  margin: 0.5rem 0;
  padding: 0.65rem 0.8rem;
  border-radius: 0.5rem;
  background: var(--k-surface-soft);
  border: 1px solid var(--k-border);
  overflow-x: auto;
}

.stem-md :deep(pre code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8rem;
  line-height: 1.6;
  color: var(--k-text);
  background: none;
  padding: 0;
}

/* 行内代码 */
.stem-md :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.85em;
  padding: 0.1em 0.3em;
  border-radius: 0.25rem;
  background: var(--k-chip);
  color: var(--k-text);
}

.stem-md :deep(ul),
.stem-md :deep(ol) {
  margin: 0.4rem 0;
  padding-left: 1.3rem;
}

.stem-md :deep(li) {
  margin: 0.15rem 0;
}

.stem-md :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 0.4rem;
}

.stem-md :deep(table) {
  border-collapse: collapse;
  margin: 0.5rem 0;
  font-size: 0.8rem;
}

.stem-md :deep(th),
.stem-md :deep(td) {
  border: 1px solid var(--k-border);
  padding: 0.25rem 0.5rem;
}
</style>
