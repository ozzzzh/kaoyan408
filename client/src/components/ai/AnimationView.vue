<script setup lang="ts">
/**
 * 分步动画播放器（基于 SVG，不依赖 WebGL）。
 *
 * 为什么不用 manim-web：那套 API 在模型训练数据里几乎不存在，
 * 让模型写调用代码基本跑不起来。这里的 DSL 只描述「这一步谁高亮、谁是新的、动到哪」，
 * 播放就是把每步的状态叠加后重画一遍，配 CSS 过渡。
 *
 * 状态是**累加**的：第 n 步看到的是「初始布局 + 前 n 步所有 addEdges/moveNodes」，
 * 而不是每步重画整张图。这样模型不必重复输出全部节点，前后也不会不一致。
 */
import { computed, ref, watch } from 'vue'

interface AnimNode {
  id: string
  label: string
  shape: 'box' | 'circle' | 'diamond' | 'text'
  x: number
  y: number
  color: 'default' | 'accent' | 'warn' | 'danger' | 'muted'
}

interface AnimEdge {
  from: string
  to: string
  label: string
  style: 'solid' | 'dashed' | 'arrow'
}

interface AnimStep {
  caption: string
  highlight?: string[]
  dim?: string[]
  addEdges?: AnimEdge[]
  moveNodes?: Array<{ id: string; x: number; y: number }>
}

export interface AnimationDsl {
  title: string
  width: number
  height: number
  nodes: AnimNode[]
  edges: AnimEdge[]
  steps: AnimStep[]
}

const props = defineProps<{ dsl: AnimationDsl }>()

/** 当前步：0 表示只看初始布局，1..n 表示看到第 n 步之后。 */
const current = ref(0)
const playing = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

const W = computed(() => Math.max(320, Math.min(900, props.dsl.width || 720)))
const H = computed(() => Math.max(240, Math.min(700, props.dsl.height || 420)))

const stepCount = computed(() => props.dsl.steps?.length ?? 0)

/* ---------- 按当前步算出这一帧的样子 ---------- */

/** 节点位置：初始坐标 + 前 current 步的移动 */
const frameNodes = computed(() => {
  const pos = new Map<string, { x: number; y: number }>()
  for (const n of props.dsl.nodes) pos.set(n.id, { x: n.x, y: n.y })
  for (let i = 0; i < current.value; i++) {
    for (const mv of props.dsl.steps[i]?.moveNodes ?? []) {
      if (pos.has(mv.id)) pos.set(mv.id, { x: mv.x, y: mv.y })
    }
  }
  return props.dsl.nodes.map((n) => ({ ...n, ...(pos.get(n.id) ?? { x: n.x, y: n.y }) }))
})

/** 连线：初始连线 + 前 current 步新增的线，按 from-to-label 去重 */
const frameEdges = computed(() => {
  const seen = new Set<string>()
  const out: AnimEdge[] = []
  const push = (e: AnimEdge) => {
    const k = `${e.from}->${e.to}|${e.label ?? ''}`
    if (seen.has(k)) return
    seen.add(k)
    out.push(e)
  }
  for (const e of props.dsl.edges ?? []) push(e)
  for (let i = 0; i < current.value; i++) {
    for (const e of props.dsl.steps[i]?.addEdges ?? []) push(e)
  }
  return out
})

/** 当前步要高亮 / 淡化的节点 */
const highlight = computed(() => new Set(props.dsl.steps[current.value - 1]?.highlight ?? []))
const dim = computed(() => new Set(props.dsl.steps[current.value - 1]?.dim ?? []))

const caption = computed(() =>
  current.value === 0 ? '准备开始 · 点「下一步」逐步展开' : props.dsl.steps[current.value - 1]?.caption ?? '',
)

/* ---------- 尺寸（与静态图解同一套估算） ---------- */

const FONT_SIZE = 12.5
const LINE_HEIGHT = 15
const PAD_X = 16
const PAD_Y = 14

function textWidth(s: string): number {
  let w = 0
  for (const ch of s) {
    w += /[\u3000-\u9fff\uff00-\uffef]/.test(ch) ? FONT_SIZE : FONT_SIZE * 0.58
  }
  return w
}

function lines(label: string): string[] {
  return String(label ?? '').split('\\n').flatMap((s) => s.split('\n')).slice(0, 3)
}

function nodeSize(n: { label: string }): { w: number; h: number } {
  const ls = lines(n.label)
  const widest = Math.max(...ls.map(textWidth), 0)
  return {
    w: Math.max(72, Math.round(widest + PAD_X * 2)),
    h: Math.max(40, Math.round(ls.length * LINE_HEIGHT + PAD_Y * 2)),
  }
}

function edgeGeometry(a: { x: number; y: number; label: string }, b: { x: number; y: number; label: string }) {
  const dx = b.x - a.x
  const dy = b.y - a.y
  const dist = Math.hypot(dx, dy) || 1
  const ux = dx / dist
  const uy = dy / dist
  const sa = nodeSize(a)
  const sb = nodeSize(b)
  const tA = Math.min(Math.abs((sa.w / 2 + 5) / (ux || 1e-6)), Math.abs((sa.h / 2 + 5) / (uy || 1e-6)))
  const tB = Math.min(Math.abs((sb.w / 2 + 5) / (ux || 1e-6)), Math.abs((sb.h / 2 + 5) / (uy || 1e-6)))
  return {
    x1: a.x + ux * tA,
    y1: a.y + uy * tA,
    x2: b.x - ux * tB,
    y2: b.y - uy * tB,
    mx: (a.x + b.x) / 2,
    my: (a.y + b.y) / 2,
  }
}

const nodeById = computed(() => {
  const m = new Map<string, AnimNode & { x: number; y: number }>()
  for (const n of frameNodes.value) m.set(n.id, n)
  return m
})

const fill = (color: string) =>
  ({
    default: 'var(--k-surface)',
    accent: 'var(--k-accent-soft)',
    warn: 'var(--k-warn-soft)',
    danger: 'color-mix(in srgb, var(--k-danger) 12%, transparent)',
    muted: 'var(--k-surface-soft)',
  })[color] ?? 'var(--k-surface)'

const stroke = (color: string) =>
  ({
    default: 'var(--k-border-strong)',
    accent: 'var(--k-accent)',
    warn: 'var(--k-warn)',
    danger: 'var(--k-danger)',
    muted: 'var(--k-border)',
  })[color] ?? 'var(--k-border-strong)'

/* ---------- 播放控制 ---------- */

function stopTimer() {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

function next() {
  if (current.value < stepCount.value) current.value++
}

function prev() {
  if (current.value > 0) current.value--
}

function reset() {
  stopTimer()
  playing.value = false
  current.value = 0
}

function togglePlay() {
  if (playing.value) {
    playing.value = false
    stopTimer()
    return
  }
  playing.value = true
  if (current.value >= stepCount.value) current.value = 0
  const tick = () => {
    if (!playing.value) return
    if (current.value < stepCount.value) {
      current.value++
      timer = setTimeout(tick, 2200)
    } else {
      playing.value = false
    }
  }
  timer = setTimeout(tick, 600)
}

// 手动拖动进度时停掉自动播放，否则会打架
watch(current, () => {
  if (playing.value && current.value >= stepCount.value) {
    playing.value = false
    stopTimer()
  }
})
</script>

<template>
  <figure class="m-0">
    <figcaption v-if="dsl.title" class="mb-2 flex items-center gap-2 text-[13px] font-semibold text-[var(--k-heading)]">
      {{ dsl.title }}
      <span class="rounded-md bg-[var(--k-accent-soft)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--k-accent-text)]">
        分步动画
      </span>
    </figcaption>

    <div class="overflow-x-auto rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)]">
      <svg
        :viewBox="`0 0 ${W} ${H}`"
        :width="W"
        :height="H"
        class="block h-auto w-full max-w-full"
        role="img"
        :aria-label="dsl.title || '分步动画'"
      >
        <defs>
          <marker
            id="anim-arrow"
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M0 0 L10 5 L0 10 z" fill="var(--k-text-muted)" />
          </marker>
        </defs>

        <!-- 连线 -->
        <g>
          <template v-for="(e, i) in frameEdges" :key="`e${i}`">
            <template v-if="nodeById.get(e.from) && nodeById.get(e.to)">
              <line
                :x1="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).x1"
                :y1="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).y1"
                :x2="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).x2"
                :y2="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).y2"
                stroke="var(--k-text-muted)"
                stroke-width="1.6"
                :stroke-dasharray="e.style === 'dashed' ? '6 4' : undefined"
                :marker-end="e.style === 'arrow' || !e.style ? 'url(#anim-arrow)' : undefined"
                opacity="0.75"
                class="anim-edge"
              />
              <text
                v-if="e.label"
                :x="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).mx"
                :y="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).my - 6"
                text-anchor="middle"
                font-size="11"
                fill="var(--k-text-faint)"
                stroke="var(--k-surface)"
                stroke-width="3.5"
                paint-order="stroke"
              >
                {{ e.label }}
              </text>
            </template>
          </template>
        </g>

        <!-- 节点 -->
        <g>
          <template v-for="n in frameNodes" :key="n.id">
            <g
              class="anim-node"
              :class="{ 'is-highlight': highlight.has(n.id), 'is-dim': dim.has(n.id) }"
            >
              <!-- 高亮时的外发光 -->
              <rect
                v-if="highlight.has(n.id)"
                :x="n.x - nodeSize(n).w / 2 - 4"
                :y="n.y - nodeSize(n).h / 2 - 4"
                :width="nodeSize(n).w + 8"
                :height="nodeSize(n).h + 8"
                rx="13"
                fill="none"
                stroke="var(--k-accent)"
                stroke-width="2"
                opacity="0.55"
              />
              <rect
                v-if="n.shape === 'box' || n.shape === 'text'"
                :x="n.x - nodeSize(n).w / 2"
                :y="n.y - nodeSize(n).h / 2"
                :width="nodeSize(n).w"
                :height="nodeSize(n).h"
                :rx="n.shape === 'box' ? 10 : 0"
                :fill="n.shape === 'text' ? 'none' : fill(n.color)"
                :stroke="stroke(n.color)"
                stroke-width="1.5"
              />
              <circle
                v-else-if="n.shape === 'circle'"
                :cx="n.x"
                :cy="n.y"
                :r="nodeSize(n).h / 2 + 2"
                :fill="fill(n.color)"
                :stroke="stroke(n.color)"
                stroke-width="1.5"
              />
              <path
                v-else
                :d="`M ${n.x} ${n.y - nodeSize(n).h / 2 - 4} L ${n.x + nodeSize(n).w / 2 + 6} ${n.y} L ${n.x} ${n.y + nodeSize(n).h / 2 + 4} L ${n.x - nodeSize(n).w / 2 - 6} ${n.y} Z`"
                :fill="fill(n.color)"
                :stroke="stroke(n.color)"
                stroke-width="1.5"
              />
              <text
                :x="n.x"
                :y="n.y"
                text-anchor="middle"
                dominant-baseline="middle"
                :font-size="FONT_SIZE"
                font-weight="600"
                fill="var(--k-text)"
              >
                <tspan
                  v-for="(ln, li) in lines(n.label)"
                  :key="li"
                  :x="n.x"
                  :dy="li === 0 ? (lines(n.label).length > 1 ? -7 : 0) : 15"
                >
                  {{ ln }}
                </tspan>
              </text>
            </g>
          </template>
        </g>
      </svg>
    </div>

    <!-- 旁白 -->
    <div
      class="mt-2 flex min-h-[38px] items-start gap-2 rounded-lg bg-[var(--k-surface-soft)] px-3 py-2 text-xs leading-relaxed text-[var(--k-text)]"
    >
      <span class="mt-0.5 shrink-0 font-mono text-[10px] text-[var(--k-text-faint)]">
        {{ current }}/{{ stepCount }}
      </span>
      <span>{{ caption }}</span>
    </div>

    <!-- 控制条 -->
    <div class="mt-2 flex items-center gap-2">
      <button
        type="button"
        class="grid h-7 w-7 place-items-center rounded-lg border border-[var(--k-border)] text-[var(--k-text-muted)] transition-colors hover:border-[var(--k-border-strong)] disabled:opacity-40"
        :disabled="current === 0"
        aria-label="上一步"
        @click="prev"
      >
        <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 6l-6 6 6 6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>

      <button
        type="button"
        class="inline-flex h-7 items-center gap-1.5 rounded-lg bg-[var(--k-accent)] px-3 text-[11px] font-semibold text-[var(--k-on-accent)] transition-colors hover:bg-[var(--k-accent-hover)]"
        @click="togglePlay"
      >
        <svg v-if="!playing" viewBox="0 0 24 24" class="h-3 w-3" fill="currentColor">
          <path d="M8 5v14l11-7z" />
        </svg>
        <svg v-else viewBox="0 0 24 24" class="h-3 w-3" fill="currentColor">
          <path d="M6 5h4v14H6zM14 5h4v14h-4z" />
        </svg>
        {{ playing ? '暂停' : '播放' }}
      </button>

      <button
        type="button"
        class="grid h-7 w-7 place-items-center rounded-lg border border-[var(--k-border)] text-[var(--k-text-muted)] transition-colors hover:border-[var(--k-border-strong)] disabled:opacity-40"
        :disabled="current >= stepCount"
        aria-label="下一步"
        @click="next"
      >
        <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>

      <button
        type="button"
        class="ml-auto text-[11px] text-[var(--k-text-faint)] hover:text-[var(--k-text)]"
        @click="reset"
      >
        重置
      </button>

      <!-- 进度点 -->
      <div class="flex items-center gap-1">
        <button
          v-for="i in stepCount"
          :key="i"
          type="button"
          class="h-1.5 rounded-full transition-all"
          :class="i <= current ? 'w-4 bg-[var(--k-accent)]' : 'w-1.5 bg-[var(--k-track)]'"
          :aria-label="`跳到第 ${i} 步`"
          @click="current = i"
        />
      </div>
    </div>
  </figure>
</template>

<style>
/* 节点位置变化用过渡，移动时看起来是「滑」过去而不是瞬移 */
.anim-node {
  transition: opacity 0.35s ease;
}

.anim-node rect,
.anim-node circle,
.anim-node path {
  transition:
    x 0.5s ease,
    y 0.5s ease,
    cx 0.5s ease,
    cy 0.5s ease,
    d 0.5s ease,
    stroke 0.35s ease,
    fill 0.35s ease;
}

.anim-node text {
  transition:
    x 0.5s ease,
    y 0.5s ease;
}

/* 本步重点：保持不透明并轻微放大 */
.anim-node.is-highlight {
  opacity: 1;
}

/* 本步背景信息：淡出但仍可见 */
.anim-node.is-dim {
  opacity: 0.32;
}

.anim-edge {
  transition: opacity 0.4s ease;
}

/* 尊重系统的「减少动态效果」偏好 */
@media (prefers-reduced-motion: reduce) {
  .anim-node,
  .anim-node rect,
  .anim-node circle,
  .anim-node path,
  .anim-node text,
  .anim-edge {
    transition: none;
  }
}
</style>