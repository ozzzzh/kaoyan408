<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/html'

/**
 * 题干渲染：Markdown + 行内公式。
 *
 * 为什么需要单独一个组件：题干里的 ```代码块``` 和 `行内代码` 是考试内容的一部分
 * （比如让判断某段 C 代码的时间复杂度），原样显示反引号会让人无法读题。
 *
 * 历史变更：这里原先「消毒 → 注入 KaTeX → 再消毒一次」，第二遍为了保住 KaTeX 的
 * class/style，被迫放行 `ADD_ATTR: ['class','style']` 并开启 svg/mathMl profile——
 * 等于把最宽的一份消毒配置用在了最不该放宽的位置。现在与知识正文共用
 * @/utils/html：原始 Markdown 严格消毒，KaTeX 输在消毒之后注入，无需再洗第二遍。
 */
const props = defineProps<{
  source: string
}>()

const html = computed(() => renderMarkdown(props.source))
</script>

<template>
  <div class="stem-md text-sm leading-relaxed text-[var(--k-text)]" v-html="html" />
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