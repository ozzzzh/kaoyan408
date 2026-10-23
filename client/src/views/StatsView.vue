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

const subjectMeta: Record<string, { name: string; bar: string; soft: string }> = {
  ds: { name: '数据结构', bar: 'bg-[#2d68ef]', soft: 'text-[#1845ad]' },
  os: { name: '操作系统', bar: 'bg-[#7c5cf0]', soft: 'text-[#5b3fd1]' },
  co: { name: '计算机组成原理', bar: 'bg-[#f0a020]', soft: 'text-[#b8730a]' },
  cn: { name: '计算机网络', bar: 'bg-[#12a594]', soft: 'text-[#0b7d70]' },
}
</script>

<template>
  <div class="space-y-8">
    <header class="space-y-1">
      <h1 class="text-2xl font-bold tracking-tight text-[#0b1f45]">学习看板</h1>
      <p class="text-sm text-[#5c6f94]">按科目与知识块查看掌握度，找出该补的章节。</p>
    </header>

    <div
      v-if="!dashboard"
      class="rounded-xl border border-dashed border-[#dbe4ef] bg-white p-6 text-sm text-[#9aa9c3]"
    >
      暂无数据。请先启动后端并导入内容，然后刷新页面。
    </div>

    <template v-else>
      <section class="grid gap-4 sm:grid-cols-3">
        <div class="rounded-xl border border-[#e2e9f5] bg-white p-5">
          <p class="text-xs text-[#9aa9c3]">总题量</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-[#0b1f45]">
            {{ dashboard.totalQuestions }}
          </p>
        </div>
        <div class="rounded-xl border border-[#e2e9f5] bg-white p-5">
          <p class="text-xs text-[#9aa9c3]">已作答</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-[#0b1f45]">
            {{ dashboard.doneQuestions }}
          </p>
        </div>
        <div class="rounded-xl border border-[#e2e9f5] bg-white p-5">
          <p class="text-xs text-[#9aa9c3]">正确率</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-[#0b1f45]">
            {{ dashboard.accuracy }}%
          </p>
        </div>
      </section>

      <section class="space-y-3">
        <h2 class="text-sm font-bold text-[#3d4d6b]">近 14 天做题量</h2>
        <div class="rounded-xl border border-[#e2e9f5] bg-white p-6">
          <div class="flex h-40 items-end gap-2">
            <div
              v-for="day in dashboard.recent"
              :key="day.date"
              class="group flex flex-1 flex-col items-center gap-2"
            >
              <div
                class="w-full rounded-t bg-[#2d68ef]/70 transition group-hover:bg-[#2d68ef]"
                :style="{ height: Math.max(2, (day.questions / maxDaily) * 130) + 'px' }"
                :title="`${day.date}：${day.questions} 题`"
              />
              <span class="text-[10px] text-[#9aa9c3]">{{ day.date.slice(5) }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="space-y-3">
        <h2 class="text-sm font-bold text-[#3d4d6b]">分科目掌握度</h2>
        <div class="overflow-hidden rounded-xl border border-[#e2e9f5] bg-white">
          <table class="w-full text-sm">
            <thead class="bg-[#f8fbff] text-xs text-[#9aa9c3]">
              <tr>
                <th class="px-5 py-3 text-left font-medium">科目</th>
                <th class="px-5 py-3 text-right font-medium">已做 / 总量</th>
                <th class="px-5 py-3 text-right font-medium">正确率</th>
                <th class="px-5 py-3 text-right font-medium">完成度</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in dashboard.subjects"
                :key="item.subject"
                class="border-t border-[#f0f4fa]"
              >
                <td class="px-5 py-3">
                  <span class="inline-flex items-center gap-2">
                    <span
                      class="h-2 w-2 rounded-full"
                      :class="subjectMeta[item.subject]?.bar ?? 'bg-[#9aa9c3]'"
                    />
                    <span class="font-medium text-[#0b1f45]">
                      {{ subjectMeta[item.subject]?.name ?? item.subject }}
                    </span>
                  </span>
                </td>
                <td class="px-5 py-3 text-right tabular-nums text-[#5c6f94]">
                  {{ item.done }} / {{ item.total }}
                </td>
                <td class="px-5 py-3 text-right tabular-nums text-[#5c6f94]">{{ item.accuracy }}%</td>
                <td class="px-5 py-3 text-right tabular-nums text-[#5c6f94]">{{ item.progress }}%</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>