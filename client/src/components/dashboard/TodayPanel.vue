<script setup lang="ts">
import { computed } from 'vue'

/**
 * 今日概览：当天做题情况与待办。
 *
 * 放在左列（较宽）而非右栏：与「四科掌握进度」一起把左列撑到与右列齐平。
 * 因为宽度不受限，这里用横向布局——左侧两个数字、右侧待办，
 * 比纵向堆叠更省高度，也不会在大屏上显得空。
 */
const props = defineProps<{
  doneToday: number
  correctToday: number
  memoryDue: number
  wrongPending: number
  streakDays: number
}>()

const accuracyToday = computed(() =>
  props.doneToday === 0 ? null : Math.round((props.correctToday / props.doneToday) * 100),
)

/** 待办条目：数量为 0 的不显示，避免堆一排无意义的「0」。 */
const todos = computed(() =>
  [
    props.memoryDue > 0
      ? { label: '背诵到期', count: props.memoryDue, to: '/memory', tone: 'accent' as const }
      : null,
    props.wrongPending > 0
      ? {
          label: '错题待订正',
          count: props.wrongPending,
          to: '/practice?scope=wrong-pending',
          tone: 'danger' as const,
        }
      : null,
  ].filter((item): item is NonNullable<typeof item> => item !== null),
)
</script>

<template>
  <section class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
    <div class="flex flex-wrap items-baseline justify-between gap-2">
      <h2 class="text-sm font-bold text-[var(--k-heading)]">今日</h2>
      <span v-if="streakDays > 0" class="text-xs text-[var(--k-text-faint)]">
        已连续
        <strong class="font-semibold text-[var(--k-accent-text)]">{{ streakDays }}</strong> 天
      </span>
    </div>

    <div class="mt-3 flex flex-wrap items-start gap-x-8 gap-y-4">
      <!-- 左：今日两个指标 -->
      <div class="flex items-end gap-5">
        <div>
          <p class="text-2xl font-bold tabular-nums text-[var(--k-text)]">{{ doneToday }}</p>
          <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">今日做题</p>
        </div>
        <div class="h-9 w-px bg-[var(--k-border)]" />
        <div>
          <p class="text-2xl font-bold tabular-nums text-[var(--k-text)]">
            {{ accuracyToday === null ? '—' : accuracyToday + '%' }}
          </p>
          <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">今日正确率</p>
        </div>
      </div>

      <!-- 右：待办；无待办时给正向反馈而不是留空 -->
      <div class="min-w-[200px] flex-1">
        <div v-if="todos.length" class="flex flex-wrap gap-2">
          <RouterLink
            v-for="item in todos"
            :key="item.label"
            :to="item.to"
            class="flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold no-underline transition"
            :class="
              item.tone === 'danger'
                ? 'bg-[#b23c3c]/10 text-[#b23c3c] hover:bg-[#b23c3c]/16'
                : 'bg-[var(--k-accent-soft)] text-[var(--k-accent-text)] hover:bg-[var(--k-accent)]/20'
            "
          >
            <span>{{ item.label }}</span>
            <span class="tabular-nums">{{ item.count }} →</span>
          </RouterLink>
        </div>
        <p
          v-else
          class="rounded-lg bg-[var(--k-surface-soft)] px-3 py-2 text-xs leading-relaxed text-[var(--k-text-faint)]"
        >
          {{ doneToday > 0 ? '今天的待办都清空了。' : '还没有开始 —— 刷几道题热热身。' }}
        </p>
      </div>
    </div>
  </section>
</template>