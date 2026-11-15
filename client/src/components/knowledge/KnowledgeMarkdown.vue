<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/html'

/**
 * Markdown 渲染 + KaTeX 公式。
 *
 * 消毒与公式逻辑已收口到 @/utils/html：本组件只负责把结果放进模板。
 * 知识正文、题干、背诵卡片从此共用同一条渲染链路，配置只有一个来源。
 */
const props = defineProps<{
  source: string
}>()

const html = computed(() => renderMarkdown(props.source))
</script>

<template>
  <div class="k-markdown" v-html="html"></div>
</template>

<style scoped>
/* 正文排版：全部走语义变量，跟随昼夜主题 */
.k-markdown {
  min-width: 0;
  font-size: 16px;
  line-height: 1.95;
  color: var(--k-text-muted);
  overflow-wrap: break-word;
}

.k-markdown :deep(p) {
  margin: 0.75rem 0;
}
.k-markdown :deep(strong) {
  font-weight: 600;
  color: var(--k-text);
}
.k-markdown :deep(em) {
  font-style: italic;
}
.k-markdown :deep(a) {
  color: var(--k-accent-text);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.k-markdown :deep(h1),
.k-markdown :deep(h2),
.k-markdown :deep(h3),
.k-markdown :deep(h4) {
  margin: 1.4rem 0 0.6rem;
  font-weight: 600;
  line-height: 1.35;
  color: var(--k-text);
}
.k-markdown :deep(h1) {
  font-size: 1.5rem;
}
.k-markdown :deep(h2) {
  font-size: 1.3rem;
}
.k-markdown :deep(h3) {
  font-size: 1.12rem;
}
.k-markdown :deep(ul),
.k-markdown :deep(ol) {
  margin: 0.7rem 0;
  padding-left: 1.5rem;
}
.k-markdown :deep(ul) {
  list-style: disc;
}
.k-markdown :deep(ol) {
  list-style: decimal;
}
.k-markdown :deep(li) {
  margin: 0.3rem 0;
}
.k-markdown :deep(li > p) {
  margin: 0;
}
.k-markdown :deep(code) {
  padding: 0.15rem 0.35rem;
  border-radius: 4px;
  background: var(--k-chip);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.88em;
  color: var(--k-text);
}
.k-markdown :deep(pre) {
  margin: 1rem 0;
  overflow-x: auto;
  border: 1px solid var(--k-border);
  border-radius: 8px;
  background: var(--k-surface-soft);
  padding: 0.9rem 1.1rem;
  line-height: 1.7;
}
.k-markdown :deep(pre > code) {
  padding: 0;
  background: transparent;
  font-size: 0.86rem;
}
.k-markdown :deep(blockquote) {
  margin: 1rem 0;
  border-left: 3px solid var(--k-border-strong);
  background: var(--k-surface-soft);
  padding: 0.6rem 1rem;
  color: var(--k-text-muted);
}
.k-markdown :deep(table) {
  width: 100%;
  margin: 1rem 0;
  border-collapse: collapse;
  font-size: 0.92rem;
}
.k-markdown :deep(th),
.k-markdown :deep(td) {
  border: 1px solid var(--k-border);
  padding: 0.5rem 0.75rem;
  text-align: left;
}
.k-markdown :deep(th) {
  background: var(--k-surface-soft);
  font-weight: 600;
  color: var(--k-text);
}
.k-markdown :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 1rem 0;
}
.k-markdown :deep(hr) {
  margin: 1.5rem 0;
  border: 0;
  border-top: 1px solid var(--k-border);
}
.k-markdown :deep(.katex) {
  color: var(--k-text);
}
.k-markdown :deep(.katex-display) {
  margin: 1rem 0;
  overflow-x: auto;
  overflow-y: hidden;
}
.k-markdown :deep(.k-math-error) {
  color: var(--k-danger);
}
</style>