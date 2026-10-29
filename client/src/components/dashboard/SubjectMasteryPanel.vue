<script setup lang="ts">
import { computed } from 'vue'
import type { SubjectStat } from '@/api/types'
import { subjectMeta } from '@/constants/subjects'

const props = defineProps<{
  subjects: SubjectStat[]
  masteryRate?: number
}>()

const items = computed(() =>
  props.subjects.map((item) => ({
    ...item,
    meta: subjectMeta(item.subject),
  })),
)

/** 分桶配色：掌握 / 不熟 / 不会 / 未做 —— 与 408os 的语义一致。 */
const buckets = [
  { key: 'mastered', label: '掌握', cls: 'bg-[#0f7a4d]/12 text-[#0f7a4d] dark:text-[#4ade80] dark:bg-[#4ade80]/12' },
  { key: 'shaky', label: '不熟', cls: 'bg-[#8a6d1f]/12 text-[#8a6d1f] dark:text-[#fbbf24] dark:bg-[#fbbf24]/12' },
  { key: 'failed', label: '不会', cls: 'bg-[#b23c3c]/12 text-[#b23c3c] dark:text-[#f87171] dark:bg-[#f87171]/12' },
  { key: 'untouched', label: '未做', cls: 'bg-[var(--k-chip)] text-[var(--k-text-faint)]' },
] as const
</script>

<template>
  <section class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
    <div class="flex flex-wrap items-baseline justify-between gap-2">
      <h2 class="text-sm font-bold text-[var(--k-heading)]">四科掌握进度</h2>
      <span class="text-xs text-[var(--k-text-faint)]">
        四科平均掌握率
        <strong class="font-semibold text-[var(--k-accent-text)]">{{ masteryRate ?? 0 }}%</strong>
      </span>
    </div>

    <div class="mt-4 grid gap-x-8 gap-y-5 lg:grid-cols-2">
      <div v-for="item in items" :key="item.subject">
        <!-- 科目名 + 题量 -->
        <div class="flex items-baseline justify-between">
          <span class="flex items-center gap-2 text-sm font-semibold text-[var(--k-text)]">
            <span class="h-2 w-2 shrink-0 rounded-full" :class="item.meta.bar" />
            {{ item.meta.name }}
          </span>
          <span class="text-xs text-[var(--k-text-faint)] tabular-nums">{{ item.total }} 题</span>
        </div>

        <!-- 两个指标：做题进度 / 掌握率 -->
        <div class="mt-3 flex items-stretch gap-4">
          <div class="flex-1">
            <p class="text-xl font-bold tabular-nums text-[var(--k-text)]">{{ item.progress }}%</p>
            <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">做题进度</p>
          </div>
          <div class="w-px shrink-0 bg-[var(--k-border)]" />
          <div class="flex-1">
            <p class="text-xl font-bold tabular-nums text-[var(--k-text)]">{{ item.masteryRate }}%</p>
            <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">掌握率</p>
          </div>
        </div>

        <!-- 进度条 -->
        <div class="mt-2.5 h-1.5 overflow-hidden rounded-full bg-[var(--k-track)]">
          <div
            class="h-full rounded-full transition-all duration-500"
            :class="item.meta.bar"
            :style="{ width: Math.max(item.progress, item.done > 0 ? 2 : 0) + '%' }"
          />
        </div>

        <!-- 四个分桶 -->
        <div class="mt-3 grid grid-cols-4 gap-2">
          <div
            v-for="bucket in buckets"
            :key="bucket.key"
            class="rounded-lg px-2 py-1.5 text-center text-[11px] font-semibold"
            :class="bucket.cls"
          >
            {{ bucket.label }} {{ item[bucket.key] }}
          </div>
        </div>
      </div>
    </div>
  </section>
</template>