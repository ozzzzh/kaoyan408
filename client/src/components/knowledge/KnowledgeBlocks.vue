<script setup lang="ts">
import { computed } from 'vue'
import KnowledgeMarkdown from './KnowledgeMarkdown.vue'
import AnimationBlock from './AnimationBlock.vue'
import { hasAnimation } from './animationLoader'
// 只引公式与消毒，不引 marked：知识正文里的公式块/callout 不需要完整 Markdown 解析。
import { renderFormulaBlock, renderTextWithMath } from '@/utils/htmlMath'
import { sanitizeHtmlBlock } from '@/utils/htmlSanitize'
import type { KnowledgeArticleData } from './types'

const props = defineProps<{
  article: KnowledgeArticleData
}>()

/**
 * 公式块直接渲染（公式本身就是完整内容，不需要先解析 Markdown）。
 * 公式里的尖括号等字符由 KaTeX 自己转义，配合 trust:false 没有再拼 HTML 的入口。
 */
function renderFormula(latex: string): string {
  return renderFormulaBlock(latex)
}

/**
 * callout 文本：**先转义再渲染公式**。
 *
 * 原来直接把 block.text 喂给公式渲染后 v-html，文本里的裸 `<` 会被当成标签吞掉
 * （正文里确实有 `cwnd < ssthresh`、`low <= high` 这类写法）。这里是纯文本字段，
 * 不做 Markdown 解析，所以转义是正确的处理方式，而不是去放宽消毒器。
 */
function renderInlineMath(text: string): string {
  return renderTextWithMath(text)
}

/**
 * HTML 型知识块：渲染前过消毒器。
 *
 * 之前这里 `v-html="block.html"` 完全没消毒——当前内容是第一方种子所以没出事，
 * 但只要这条路径将来接上 AI 生成或用户投稿，就是现成的存储型 XSS 入口。
 */
function renderHtmlBlock(html: string): string {
  return sanitizeHtmlBlock(html)
}

const subpointCount = computed(() => props.article.subpoints.length)
</script>

<template>
  <article class="space-y-2">
    <p v-if="subpointCount === 0" class="text-sm text-[var(--k-text-faint)]">
      该知识点暂无正文内容。
    </p>

    <section
      v-for="(subpoint, index) in article.subpoints"
      :id="subpoint.id"
      :key="subpoint.id"
      class="scroll-mt-24 border-t border-[var(--k-border)] pb-2 pt-8 first:border-t-0 first:pt-0"
    >
      <h2
        class="mb-5 flex items-baseline gap-3 text-[1.35rem] font-bold leading-snug tracking-tight text-[var(--k-text)]"
      >
        <span class="font-mono text-[0.72em] font-bold text-[var(--k-accent-text)]">
          {{ String(index + 1).padStart(2, '0') }}
        </span>
        <span>{{ subpoint.title }}</span>
      </h2>

      <div class="space-y-1">
        <section
          v-for="block in subpoint.blocks"
          :id="block.id"
          :key="block.id"
          class="scroll-mt-24"
        >
          <KnowledgeMarkdown v-if="block.type === 'paragraph'" :source="block.text" />

          <div
            v-else-if="block.type === 'html'"
            class="k-html-block text-[16px] leading-[1.95] text-[var(--k-text-muted)]"
            v-html="renderHtmlBlock(block.html)"
          ></div>

          <figure
            v-else-if="block.type === 'image'"
            class="my-5 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-4 py-6"
          >
            <img
              :src="block.src"
              :alt="block.alt"
              class="mx-auto block h-auto max-h-[60vh] w-auto max-w-full"
              loading="lazy"
            />
            <figcaption
              v-if="block.caption"
              class="mt-3 text-center text-[13px] leading-6 text-[var(--k-text-faint)]"
            >
              图 · {{ block.caption }}
            </figcaption>
          </figure>

          <figure
            v-else-if="block.type === 'formula'"
            class="my-5 overflow-x-auto rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-5 py-5"
          >
            <div v-html="renderFormula(block.formula)"></div>
            <figcaption
              v-if="block.caption"
              class="mt-1 text-center text-[13px] text-[var(--k-text-faint)]"
            >
              {{ block.caption }}
            </figcaption>
          </figure>

          <aside
            v-else-if="block.type === 'callout'"
            class="my-5 rounded-xl border px-5 py-4"
            :class="
              block.tone === 'blue'
                ? 'border-[var(--k-border)] bg-[var(--k-accent-soft)]'
                : 'border-[var(--k-warn-border)] bg-[var(--k-warn-soft)]'
            "
          >
            <div class="flex items-center gap-2.5">
              <svg class="h-[18px] w-[18px] shrink-0" viewBox="0 0 20 20" aria-hidden="true">
                <circle
                  cx="10"
                  cy="10"
                  r="10"
                  :fill="block.tone === 'blue' ? 'var(--k-accent)' : 'var(--k-warn)'"
                />
                <path d="M10 5.5v5.5" stroke="#fff" stroke-width="1.9" stroke-linecap="round" />
                <circle cx="10" cy="14.2" r="1.35" fill="#fff" />
              </svg>
              <strong class="text-[15px] font-bold leading-7 text-[var(--k-text)]">
                {{ block.title }}
              </strong>
            </div>
            <p
              class="mb-0 mt-1 text-[15px] leading-7 text-[var(--k-text-muted)]"
              v-html="renderInlineMath(block.text)"
            ></p>
          </aside>

          <figure v-else-if="block.type === 'animation'" class="my-5">
            <AnimationBlock
              v-if="hasAnimation(block.animationId)"
              :animation-id="block.animationId"
              :aria-label="block.ariaLabel"
            />
            <div
              v-else
              class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface-soft)] px-4 py-6 text-center text-[13px] text-[var(--k-text-faint)]"
            >
              动画不可用（{{ block.animationId ?? '未登记 id' }}）
            </div>
          </figure>
        </section>
      </div>
    </section>
  </article>
</template>

<style scoped>
.k-html-block :deep(svg) {
  max-width: 100%;
  height: auto;
}
.k-html-block :deep(h2) {
  margin: 1.2rem 0 0.5rem;
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--k-text);
}
.k-html-block :deep(p) {
  margin: 0.7rem 0;
}
.k-html-block :deep(strong) {
  color: var(--k-text);
}
.k-html-block :deep(.k-math-error) {
  color: var(--k-danger);
}
</style>