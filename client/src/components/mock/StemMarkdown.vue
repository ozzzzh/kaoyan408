<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import katex from 'katex'
import { marked } from 'marked'

/**
 * 题干渲染：Markdown + 行内公式。
 *
 * 为什么需要单独一个组件：题干里的 ```代码块``` 和 `行内代码` 是考试内容的一部分
 * （比如让判断某段 C 代码的时间复杂度），原样显示反引号会让人无法读题。
 *
 * 与 KnowledgeMarkdown 的区别：这里保留 class，因为代码块与行内代码要靠 class
 * 上样式；同时禁掉 script/iframe 等危险标签，文本内容仍然经过 DOMPurify 清洗。
 */
const props = defineProps<{
  source: string
}>()

const html = computed(() => {
  if (!props.source) return ''

  const rendered = marked.parse(props.source, {
    async: false,
    breaks: true,
    gfm: true,
  })

  // 第一步：清洗原始 Markdown 输出，只保留自己的代码样式类
  const sanitized = DOMPurify.sanitize(rendered as string, {
    FORBID_TAGS: ['button', 'embed', 'form', 'iframe', 'input', 'object', 'script', 'style'],
    ADD_ATTR: ['class'],
    USE_PROFILES: { html: true },
  })

  // 第二步：把 $...$ / $$...$$ 替换成 KaTeX HTML
  const withMath = renderMath(sanitized as string)

  // 第三步：再清洗一次。KaTeX 依赖 class 才能正确排版（分子分母、上下标等），
  // 这里放行 class 与 style —— style 是 KaTeX 用来控制间距与位置的，去掉会错版。
  // 数学内容本身来自题库，且已经过上面两步的标签白名单，风险限于样式而非脚本执行。
  return DOMPurify.sanitize(withMath, {
    FORBID_TAGS: ['button', 'embed', 'form', 'iframe', 'input', 'object', 'script'],
    ADD_ATTR: ['class', 'style', 'aria-hidden', 'encoding'],
    USE_PROFILES: { html: true, mathMl: true, svg: true },
  }) as string
})

/** KaTeX 渲染。失败时保留原文，避免一道题渲染异常导致整页空白。 */
function renderMath(source: string): string {
  const mathPattern =
    /\$\$([\s\S]*?)\$\$|\\\[([\s\S]*?)\\\]|\$([^$\n]+?)\$|\\\(([\s\S]*?)\\\)/g

  return source.replace(
    mathPattern,
    (
      match: string,
      displayDollar?: string,
      displayBracket?: string,
      inlineDollar?: string,
      inlineBracket?: string,
    ) => {
      const formula = displayDollar ?? displayBracket ?? inlineDollar ?? inlineBracket ?? ''
      const display = displayDollar != null || displayBracket != null
      try {
        return katex.renderToString(formula.trim(), {
          displayMode: display,
          throwOnError: false,
        })
      } catch {
        return match
      }
    },
  )
}
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