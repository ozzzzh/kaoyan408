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
/*
 * 图示皮肤：内容里内嵌 SVG 的 <style> 会被 htmlSanitize 的 FORBID_TAGS 整块剥掉
 * （见 utils/htmlSanitize.ts 的 HTML_BLOCK_CONFIG）。剥掉之后类样式全部失效：
 *   - <rect> 失去 fill   → 默认纯黑，暗色主题下变成一条黑带
 *   - <line> 失去 stroke → 时间轴直接消失，只剩箭头
 *   - <text> 失去 fill / font-size / text-anchor → 继承外层 16px、纯黑、左对齐，
 *     原本居中的长句会从坐标点往右冲，超出 viewBox 后被裁成半截
 *
 * 约定改成「内容只写语义 class，样式由应用提供」，这里统一兜底：
 *   1. 只兜底没写显式属性的元素（:not([fill]) / :not([stroke])），
 *      内容里已经写死的语义色保持不动；
 *   2. 颜色一律走主题变量，亮色/暗色自动适配（原来的 #dbeafe/#1e293b 写死了浅色主题）。
 */
.k-html-block :deep(svg) {
  display: block;
  margin: 0.75rem auto;
  max-width: 100%;
  height: auto;
  color: var(--k-text-muted);
  font-family: inherit;
}

/* 文字兜底：没写 fill 的跟随主题文字色；字号压回图示档位（外层 16px 太大） */
.k-html-block :deep(svg text:where(:not([fill]))) {
  fill: currentColor;
}
.k-html-block :deep(svg text:where(:not([font-size]))) {
  font-size: 12.5px;
}

/* 标题类：加粗提亮一档 */
.k-html-block :deep(svg .t),
.k-html-block :deep(svg .title),
.k-html-block :deep(svg .hdr) {
  font-size: 13px;
  font-weight: 700;
}
.k-html-block :deep(svg .t:not([fill])),
.k-html-block :deep(svg .title:not([fill])),
.k-html-block :deep(svg .hdr:not([fill])) {
  fill: var(--k-text);
}

/* 说明类：字号小一档、颜色淡一档 */
.k-html-block :deep(svg .b),
.k-html-block :deep(svg .lbl),
.k-html-block :deep(svg .sub),
.k-html-block :deep(svg .note),
.k-html-block :deep(svg .cap),
.k-html-block :deep(svg .dim) {
  font-size: 12px;
}

/*
 * 居中：全库 648 条图示样式规则里 389 条写了 text-anchor: middle、只有 6 条是 start，
 * 说明「居中」才是这些标签的默认意图。补回 middle，长句就不会再往右溢出被裁。
 * 只对作者惯用的这些类生效，没写 class 的裸 <text> 仍保持默认左对齐。
 */
.k-html-block :deep(svg .t),
.k-html-block :deep(svg .title),
.k-html-block :deep(svg .b),
.k-html-block :deep(svg .lbl) {
  text-anchor: middle;
}

/*
 * 图形兜底：默认黑色填充 + 默认无描边是「整张图变黑块」的直接原因。
 * 这里所有兜底都套了 :where()——它把选择器特异性归零，
 * 否则 `svg rect:not([fill])`（0,3,2）会盖掉下面 `.d-read`（0,3,1）的语义色。
 */
.k-html-block :deep(svg rect:where(:not([fill]))),
.k-html-block :deep(svg circle:where(:not([fill]))),
.k-html-block :deep(svg ellipse:where(:not([fill]))) {
  fill: var(--k-surface-soft);
}
.k-html-block :deep(svg rect:where(:not([stroke]))),
.k-html-block :deep(svg circle:where(:not([stroke]))),
.k-html-block :deep(svg ellipse:where(:not([stroke]))) {
  stroke: var(--k-border-strong);
  stroke-width: 1;
}

/* 时间轴线：默认 stroke:none，不补就整条看不见 */
.k-html-block :deep(svg line:where(:not([stroke]))) {
  stroke: var(--k-border-strong);
  stroke-width: 1.5;
}

/*
 * 语义色块：颜色兜底只能救「读得清」，救不了「分得清」。
 * 内容侧给有语义的段落挂 class，配色由这里统一给，暗色/亮色各一套。
 * 命名对内容作者只需要记四类：读写 / 刷新或强调 / 轴线 / 标注。
 */
.k-html-block :deep(svg .d-read) {
  fill: var(--k-accent-soft);
  stroke: var(--k-accent);
  stroke-width: 1.2;
}
.k-html-block :deep(svg .d-refresh) {
  fill: var(--k-warn-soft);
  stroke: var(--k-warn);
  stroke-width: 1.2;
}
.k-html-block :deep(svg .d-axis) {
  stroke: var(--k-border-strong);
  stroke-width: 1.5;
}
.k-html-block :deep(svg .d-title) {
  font-size: 13px;
  font-weight: 700;
  fill: var(--k-text);
  text-anchor: middle;
}
.k-html-block :deep(svg .d-label) {
  font-size: 11.5px;
  fill: var(--k-text);
  text-anchor: middle;
}
.k-html-block :deep(svg .d-note) {
  font-size: 11.5px;
  fill: var(--k-text-muted);
  text-anchor: middle;
}
.k-html-block :deep(svg .d-legend) {
  font-size: 11.5px;
  fill: var(--k-text-muted);
  text-anchor: start;
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