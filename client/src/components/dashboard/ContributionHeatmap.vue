<script setup lang="ts">
import { computed } from 'vue'
import type { Heatmap, HeatmapDay } from '@/api/types'

const props = defineProps<{
  heatmap: Heatmap | null
  activeDays?: number
  streakDays?: number
}>()

/** 按周分组：每 7 个格子一列（周一→周日）。 */
const weeks = computed<HeatmapDay[][]>(() => {
  const days = props.heatmap?.days ?? []
  const result: HeatmapDay[][] = []
  for (let i = 0; i < days.length; i += 7) {
    result.push(days.slice(i, i + 7))
  }
  return result
})

const monthLabels = computed(() => {
  const labels: Array<{ index: number; text: string }> = []
  let lastMonth = -1
  weeks.value.forEach((week, index) => {
    const first = week[0]
    if (!first) return
    const month = Number(first.date.slice(5, 7))
    // 同一月份只在第一次出现的那一列标注
    if (month !== lastMonth) {
      labels.push({ index, text: `${month}月` })
      lastMonth = month
    }
  })
  return labels
})

const levelClass = (level: number) => {
  switch (level) {
    case 1:
      return 'bg-[#c6e4c9]'
    case 2:
      return 'bg-[#8ecf96]'
    case 3:
      return 'bg-[#4fae5f]'
    case 4:
      return 'bg-[#1f7a33]'
    default:
      return 'bg-[var(--k-chip)]'
  }
}

const tooltip = (day: HeatmapDay) =>
  `${day.date}：${day.questions} 题${day.correct ? `（答对 ${day.correct}）` : ''}`

/**
 * 星期标签：7 行格子，只在周一/三/五 显示文字，其余留空占位。
 * 必须与格子一一对应，否则标签会和行错位。
 */
const weekdayLabels = ['周一', '', '周三', '', '周五', '', '']
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
</script>

<template>
  <section class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
    <div class="flex flex-wrap items-baseline justify-between gap-2">
      <h2 class="text-sm font-bold text-[var(--k-heading)]">热力图</h2>
      <span class="text-xs text-[var(--k-text-faint)]">
        近 90 天 · 做题天数
        <strong class="font-semibold text-[var(--k-accent-text)]">{{ activeDays ?? 0 }}</strong>
        · 连续 <strong class="font-semibold text-[var(--k-accent-text)]">{{ streakDays ?? 0 }}</strong> 天
      </span>
    </div>

    <div v-if="!heatmap || !heatmap.days.length" class="py-8 text-center text-sm text-[var(--k-text-faint)]">
      暂无做题记录
    </div>

    <div v-else class="mt-4">
      <!--
        用同一套 grid 轨道定义「标签列 + 网格」，两者行高必然一致。
        之前把标签放在独立的 flex 容器里，行高由字体（10px×1.25=12.5px）决定，
        而格子是 aspect-square（约 16px），每行差约 3.5px、七行累计差约 25px，
        标签会逐行往下漂，看起来「浮」在格子缝里。
        现在两边共用 grid-template-rows，由格子尺寸决定轨道高度，不会再错位。
      -->
      <div
        class="grid grid-cols-[auto_minmax(0,1fr)] gap-x-2"
        style="grid-template-rows: 1rem auto"
      >
        <!-- 月份标签行：左上角留空 -->
        <div />
        <div class="relative h-4">
          <span
            v-for="label in monthLabels"
            :key="`${label.index}-${label.text}`"
            class="absolute text-[10px] text-[var(--k-text-faint)]"
            :style="{ left: (label.index / weeks.length) * 100 + '%' }"
            >{{ label.text }}</span
          >
        </div>

        <!-- 左侧星期标签：与右侧同高，靠 grid 行轨道对齐 -->
        <div
          class="grid grid-rows-7 gap-[3px] text-right text-[10px] leading-none text-[var(--k-text-faint)]"
        >
          <span v-for="(label, i) in weekdayLabels" :key="i" class="flex items-center justify-end">
            {{ label }}
          </span>
        </div>

        <!--
          周列 × 7 天。
          用 auto-fit + minmax(10px, 1fr)：13 列在 248px 里若纯 1fr 每格约 16px
          偏大且松散；给 10px 下限、由 1fr 均分，观感接近 GitHub 热力图。
        -->
        <div class="grid grid-cols-[repeat(auto-fit,minmax(10px,1fr))] gap-[3px]">
          <div v-for="(week, wi) in weeks" :key="wi" class="grid min-w-0 grid-rows-7 gap-[3px]">
            <div
              v-for="(day, di) in week"
              :key="day.date"
              class="aspect-square w-full rounded-[3px] transition hover:ring-2 hover:ring-[var(--k-accent)]/40"
              :class="levelClass(day.level)"
              :title="`${tooltip(day)}（周${weekdays[di]}）`"
            />
          </div>
        </div>
      </div>

      <!-- 图例 -->
      <div class="mt-3.5 flex items-center justify-end gap-1.5 text-[10px] text-[var(--k-text-faint)]">
        <span>少</span>
        <span class="h-3.5 w-3.5 rounded-[3px] bg-[var(--k-chip)]" />
        <span class="h-3.5 w-3.5 rounded-[3px] bg-[#c6e4c9]" />
        <span class="h-3.5 w-3.5 rounded-[3px] bg-[#8ecf96]" />
        <span class="h-3.5 w-3.5 rounded-[3px] bg-[#4fae5f]" />
        <span class="h-3.5 w-3.5 rounded-[3px] bg-[#1f7a33]" />
        <span>多</span>
      </div>
    </div>
  </section>
</template>