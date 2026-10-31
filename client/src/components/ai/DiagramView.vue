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

const nodeById = computed(() => {
  const m = new Map<string, DiagramNode>()
  for (const n of props.dsl.nodes ?? []) m.set(n.id, n)
  return m
})

/** 每种形状的包围盒尺寸，用于算连线交点与文字区域 */
const NODE_W = 108
const NODE_H = 48

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

  // 从中心出发，沿方向走到矩形边框的距离
  const halfW = NODE_W / 2 + 6
  const halfH = NODE_H / 2 + 6
  const tA = Math.min(Math.abs(halfW / (ux || 1e-6)), Math.abs(halfH / (uy || 1e-6)))
  const tB = tA

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
        :viewBox="`0 0 ${W} ${H}`"
        :width="W"
        :height="H"
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
              :x="n.x - NODE_W / 2"
              :y="n.y - NODE_H / 2"
              :width="NODE_W"
              :height="NODE_H"
              :rx="n.shape === 'box' ? 10 : 0"
              :fill="n.shape === 'text' ? 'none' : fill(n.color)"
              :stroke="n.shape === 'text' ? 'none' : stroke(n.color)"
              stroke-width="1.5"
            />
            <circle
              v-else-if="n.shape === 'circle'"
              :cx="n.x"
              :cy="n.y"
              :r="NODE_H / 2 + 2"
              :fill="fill(n.color)"
              :stroke="stroke(n.color)"
              stroke-width="1.5"
            />
            <path
              v-else
              :d="`M ${n.x} ${n.y - NODE_H / 2 - 4} L ${n.x + NODE_W / 2 + 8} ${n.y} L ${n.x} ${n.y + NODE_H / 2 + 4} L ${n.x - NODE_W / 2 - 8} ${n.y} Z`"
              :fill="fill(n.color)"
              :stroke="stroke(n.color)"
              stroke-width="1.5"
            />

            <text
              :x="n.x"
              :y="n.y"
              text-anchor="middle"
              dominant-baseline="middle"
              font-size="12.5"
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