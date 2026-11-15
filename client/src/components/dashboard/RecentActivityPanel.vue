<script setup lang="ts">
import { computed } from 'vue'
import type { DailyPoint } from '@/api/types'

/**
 * 近 14 天做题量迷你柱状图。
 * 放在热力图下方填充右栏空白：热力图看 90 天的「有没有坚持」，
 * 这里看最近两周的「节奏如何」，两者粒度互补。
 */
const props = defineProps<{
  recent: DailyPoint[]
}>()

/** 柱高按区间最大值归一化；全为 0 时给一个下限，避免除零。 */
const maxQuestions = computed(() => Math.max(1, ...props.recent.map((d) => d.questions)))

const totalQuestions = computed(() => props.recent.reduce((sum, d) => sum + d.questions, 0))
const totalCorrect = computed(() => props.recent.reduce((sum, d) => sum + d.correct, 0))
const activeDays = computed(() => props.recent.filter((d) => d.questions > 0).length)

const accuracy = computed(() =>
  totalQuestions.value === 0 ? null : Math.round((totalCorrect.value / totalQuestions.value) * 100),
)

/** 只显示 月-日 中的日，避免拥挤 */
const dayLabel = (date: string) => date.slice(8)
</script>

<template>
  <section class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
    <div class="flex flex-wrap items-baseline justify-between gap-2">
      <h2 class="text-sm font-bold text-[var(--k-heading)]">近 14 天</h2>
      <span class="text-xs text-[var(--k-text-faint)]">
        共 <strong class="font-semibold text-[var(--k-accent-text)]">{{ totalQuestions }}</strong> 题
        <template v-if="accuracy !== null">
          · 正确率
          <strong class="font-semibold text-[var(--k-accent-text)]">{{ accuracy }}%</strong>
        </template>
      </span>
    </div>

    <div v-if="totalQuestions === 0" class="mt-3 flex items-center gap-2.5 text-xs text-[var(--k-text-faint)]">
      <!-- 空态用一排虚线占位，示意这里将来会出现柱状图，而不是留一大块空白 -->
      <span class="flex h-8 flex-1 items-end gap-1">
        <i v-for="i in 14" :key="i" class="h-1.5 flex-1 rounded-full bg-[var(--k-chip)]" />
      </span>
    </div>
    <p v-if="totalQuestions === 0" class="mt-2 text-[11px] text-[var(--k-text-faint)]">
      最近两周还没有做题记录
    </p>

    <template v-else>
      <!-- 柱状图：每天一根，高度按最大题量归一 -->
      <div class="mt-4 flex h-20 items-end gap-1">
        <div
          v-for="day in recent"
          :key="day.date"
          class="group flex h-full flex-1 flex-col justify-end"
          :title="`${day.date}：${day.questions} 题（答对 ${day.correct}）`"
        >
          <div
            v-if="day.questions > 0"
            class="w-full rounded-t-[3px] bg-[var(--k-accent)]/70 transition group-hover:bg-[var(--k-accent)]"
            :style="{ height: Math.max(6, (day.questions / maxQuestions) * 76) + 'px' }"
          />
          <div v-else class="h-[3px] w-full rounded-full bg-[var(--k-chip)]" />
        </div>
      </div>

      <!-- 日期刻度：只标首、中、末，避免 14 个数字挤在一起 -->
      <div class="mt-1.5 flex text-[10px] text-[var(--k-text-faint)]">
        <span class="flex-1 text-left">{{ dayLabel(recent[0]?.date ?? '') }}日</span>
        <span class="flex-1 text-center">{{ dayLabel(recent[Math.floor(recent.length / 2)]?.date ?? '') }}日</span>
        <span class="flex-1 text-right">{{ dayLabel(recent[recent.length - 1]?.date ?? '') }}日</span>
      </div>

      <p class="mt-2 text-[11px] text-[var(--k-text-faint)]">
        14 天里有 <strong class="text-[var(--k-text-muted)]">{{ activeDays }}</strong> 天在复习
      </p>
    </template>
  </section>
</template>