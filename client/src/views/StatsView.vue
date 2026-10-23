<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const { dashboard } = storeToRefs(userStore)

const maxDaily = computed(() => {
  const recent = dashboard.value?.recent ?? []
  return Math.max(1, ...recent.map((day) => day.questions))
})

const subjectMeta: Record<string, string> = {
  ds: '数据结构',
  os: '操作系统',
  co: '计算机组成原理',
  cn: '计算机网络',
}
</script>

<template>
  <div class="space-y-8">
    <header class="space-y-1">
      <h1 class="text-2xl font-semibold tracking-tight">学习看板</h1>
      <p class="text-sm text-ink-600">按科目与知识块查看掌握度，找出该补的章节。</p>
    </header>

    <div v-if="!dashboard" class="rounded-xl border border-dashed border-ink-200 bg-white p-6 text-sm text-ink-400">
      暂无数据。请先启动后端并导入内容，然后刷新页面。
    </div>

    <template v-else>
      <section class="grid gap-4 sm:grid-cols-3">
        <div class="rounded-xl border border-ink-200 bg-white p-5">
          <p class="text-xs text-ink-400">总题量</p>
          <p class="mt-2 text-2xl font-semibold">{{ dashboard.totalQuestions }}</p>
        </div>
        <div class="rounded-xl border border-ink-200 bg-white p-5">
          <p class="text-xs text-ink-400">已作答</p>
          <p class="mt-2 text-2xl font-semibold">{{ dashboard.doneQuestions }}</p>
        </div>
        <div class="rounded-xl border border-ink-200 bg-white p-5">
          <p class="text-xs text-ink-400">正确率</p>
          <p class="mt-2 text-2xl font-semibold">{{ dashboard.accuracy }}%</p>
        </div>
      </section>

      <section class="space-y-3">
        <h2 class="text-sm font-medium text-ink-600">近 14 天做题量</h2>
        <div class="rounded-xl border border-ink-200 bg-white p-6">
          <div class="flex h-40 items-end gap-2">
            <div v-for="day in dashboard.recent" :key="day.date" class="flex flex-1 flex-col items-center gap-2">
              <div
                class="w-full rounded-t bg-salt-500/70"
                :style="{ height: Math.max(2, (day.questions / maxDaily) * 130) + 'px' }"
                :title="`${day.date}：${day.questions} 题`"
              />
              <span class="text-[10px] text-ink-400">{{ day.date.slice(5) }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="space-y-3">
        <h2 class="text-sm font-medium text-ink-600">分科目掌握度</h2>
        <div class="overflow-hidden rounded-xl border border-ink-200 bg-white">
          <table class="w-full text-sm">
            <thead class="bg-ink-50 text-xs text-ink-400">
              <tr>
                <th class="px-5 py-3 text-left font-medium">科目</th>
                <th class="px-5 py-3 text-right font-medium">已做 / 总量</th>
                <th class="px-5 py-3 text-right font-medium">正确率</th>
                <th class="px-5 py-3 text-right font-medium">完成度</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in dashboard.subjects" :key="item.subject" class="border-t border-ink-100">
                <td class="px-5 py-3">{{ subjectMeta[item.subject] ?? item.subject }}</td>
                <td class="px-5 py-3 text-right tabular-nums">{{ item.done }} / {{ item.total }}</td>
                <td class="px-5 py-3 text-right tabular-nums">{{ item.accuracy }}%</td>
                <td class="px-5 py-3 text-right tabular-nums">{{ item.progress }}%</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>