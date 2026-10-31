<script setup lang="ts">
/**
 * 把 AI 生成的图解 DSL 画成 SVG。
 *
 * 为什么是 SVG 而不是 canvas：图里有文字，SVG 的文字可选、可搜索、缩放不糊，
 * 还能直接套用主题色变量（深色主题自动跟着变）。
 *
 * 安全：这里不渲染任何 HTML。节点标签一律走文本节点插值，
 * 整个组件没有 v-html —— 模型输出再离谱也只是一个字符串。
 * 坐标与尺寸后端已经校验过范围，这里再做一次兜底夹取，
 * 避免以后改动后端校验时把节点画到画布外。
 */
import { computed } from 'vue'

interface DiagramNode {
  id: string
  label: string
  shape: 'box' | 'circle' | 'diamond' | 'text'
  x: number
  y: number
  color: 'default' | 'accent' | 'warn' | 'danger' | 'muted'
}

interface DiagramEdge {
  from: string
  to: string
  label: string
  style: 'solid' | 'dashed' | 'arrow'
}

interface DiagramNote {
  text: string
  x: number
  y: number
}

export interface DiagramDsl {
  title: string
  width: number
  height: number
  nodes: DiagramNode[]
  edges: DiagramEdge[]
  notes?: DiagramNote[]
}

const props = defineProps<{ dsl: DiagramDsl }>()

const W = computed(() => Math.max(320, Math.min(900, props.dsl.width || 720)))
const H = computed(() => Math.max(240, Math.min(700, props.dsl.height || 420)))

/**
 * 自动扩边：按节点实际占位重算可视区域。
 *
 * 节点宽高随文字变化，后端无从预知，所以坐标钳制只能做粗筛；
 * 真正的「不被裁掉」靠这里——把每个节点的外接矩形并起来，
 * 再把 viewBox 扩到能装下全部内容。画布只变大不变小。
 */
const fit = computed(() => {
  const W0 = W.value
  const H0 = H.value
  let minX = 0
  let minY = 0
  let maxX = W0
  let maxY = H0
  for (const n of props.dsl.nodes ?? []) {
    const s = nodeSize(n)
    minX = Math.min(minX, n.x - s.w / 2 - 10)
    maxX = Math.max(maxX, n.x + s.w / 2 + 10)
    minY = Math.min(minY, n.y - s.h / 2 - 10)
    maxY = Math.max(maxY, n.y + s.h / 2 + 14)
  }
  return {
    minX,
    minY,
    w: Math.max(1, maxX - minX),
    h: Math.max(1, maxY - minY),
  }
})

const nodeById = computed(() => {
  const m = new Map<string, DiagramNode>()
  for (const n of props.dsl.nodes ?? []) m.set(n.id, n)
  return m
})

/**
 * 节点尺寸按文字长度算出来，而不是写死。
 *
 * 之前写死 108×48：模型把公式（如 LOC(ai)=LOC(a1)+(i-1)L）塞进 label 时，
 * 框不会跟着变宽，文字直接溢出到框外。
 * 这里按最宽一行的**估算宽度**决定框宽，中文按全角算。
 */
const FONT_SIZE = 12.5
const LINE_HEIGHT = 15
const PAD_X = 16
const PAD_Y = 14

/** 估算一行文字占多少像素：中日韩字符算 1 个字号，其余算 0.58 个。 */
function textWidth(s: string): number {
  let w = 0
  for (const ch of s) {
    // 全角字符（CJK、全角标点）按 1 个字宽，ASCII 按 0.58
    w += /[　-鿿＀-￯]/.test(ch) ? FONT_SIZE : FONT_SIZE * 0.58
  }
  return w
}

/** 一个节点实际占的宽高（含内边距）。 */
function nodeSize(n: { label: string }): { w: number; h: number } {
  const ls = lines(n.label)
  const widest = Math.max(...ls.map(textWidth), 0)
  return {
    w: Math.max(72, Math.round(widest + PAD_X * 2)),
    h: Math.max(40, Math.round(ls.length * LINE_HEIGHT + PAD_Y * 2)),
  }
}

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

const textColor = (color: string) =>
  ({
    warn: 'var(--k-warn)',
    danger: 'var(--k-danger)',
    muted: 'var(--k-text-muted)',
  })[color] ?? 'var(--k-text)'

/**
 * 算连线端点：从节点中心连到边框交点。
 *
 * 直接从中心连到中心会让箭头压在节点上，看起来像穿过去了。
 * 这里按方向把端点收到矩形边缘（圆形节点近似按外接框处理，
 * 视觉误差在可接受范围内）。
 */
function edgeGeometry(a: DiagramNode, b: DiagramNode) {
  const dx = b.x - a.x
  const dy = b.y - a.y
  const dist = Math.hypot(dx, dy) || 1
  const ux = dx / dist
  const uy = dy / dist

  // 从中心出发，沿方向走到矩形边框的距离。
  // 两端各用自己的尺寸，宽窄不同的节点相连时端点才不会压进框里。
  const sa = nodeSize(a)
  const sb = nodeSize(b)
  const tA = Math.min(Math.abs((sa.w / 2 + 5) / (ux || 1e-6)), Math.abs((sa.h / 2 + 5) / (uy || 1e-6)))
  const tB = Math.min(Math.abs((sb.w / 2 + 5) / (ux || 1e-6)), Math.abs((sb.h / 2 + 5) / (uy || 1e-6)))

  return {
    x1: a.x + ux * tA,
    y1: a.y + uy * tA,
    x2: b.x - ux * tB,
    y2: b.y - uy * tB,
    // 标签放在中点附近
    mx: (a.x + b.x) / 2,
    my: (a.y + b.y) / 2,
  }
}

/** 标签可能含 \n，拆成多行居中显示 */
function lines(label: string): string[] {
  return String(label ?? '').split('\\n').flatMap((s) => s.split('\n')).slice(0, 3)
}
</script>

<template>
  <figure class="m-0">
    <figcaption
      v-if="dsl.title"
      class="mb-2 text-[13px] font-semibold text-[var(--k-heading)]"
    >
      {{ dsl.title }}
    </figcaption>

    <div class="overflow-x-auto rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)]">
      <svg
        :viewBox="`${fit.minX} ${fit.minY} ${fit.w} ${fit.h}`"
        :width="fit.w"
        :height="fit.h"
        class="block h-auto w-full max-w-full"
        role="img"
        :aria-label="dsl.title || '原理图解'"
      >
        <defs>
          <marker
            id="dg-arrow"
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

        <!-- 连线先画，压在节点下面 -->
        <g>
          <template v-for="(e, i) in dsl.edges" :key="`e${i}`">
            <template v-if="nodeById.get(e.from) && nodeById.get(e.to)">
              <line
                :x1="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).x1"
                :y1="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).y1"
                :x2="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).x2"
                :y2="edgeGeometry(nodeById.get(e.from)!, nodeById.get(e.to)!).y2"
                stroke="var(--k-text-muted)"
                stroke-width="1.6"
                :stroke-dasharray="e.style === 'dashed' ? '6 4' : undefined"
                :marker-end="e.style === 'arrow' || !e.style ? 'url(#dg-arrow)' : undefined"
                opacity="0.75"
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
          <template v-for="n in dsl.nodes" :key="n.id">
            <rect
              v-if="n.shape === 'box' || n.shape === 'text'"
              :x="n.x - nodeSize(n).w / 2"
              :y="n.y - nodeSize(n).h / 2"
              :width="nodeSize(n).w"
              :height="nodeSize(n).h"
              :rx="n.shape === 'box' ? 10 : 0"
              :fill="n.shape === 'text' ? 'none' : fill(n.color)"
              :stroke="n.shape === 'text' ? 'none' : stroke(n.color)"
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
              :fill="textColor(n.color)"
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
          </template>
        </g>

        <!-- 备注 -->
        <g v-if="dsl.notes?.length">
          <text
            v-for="(note, i) in dsl.notes"
            :key="`n${i}`"
            :x="note.x"
            :y="note.y"
            font-size="11"
            fill="var(--k-text-faint)"
          >
            {{ note.text }}
          </text>
        </g>
      </svg>
    </div>
  </figure>
</template>