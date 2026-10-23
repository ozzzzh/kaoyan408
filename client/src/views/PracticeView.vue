<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { QuestionView } from '@/api/types'

/**
 * M0 的最小可用练习页：按真题 id 取单题、作答、看判定结果与解析。
 * 完整的刷题流（队列、错题本、综合题评分）在 M1 实现。
 */
const examRef = ref('exam-2026-1')
const question = ref<QuestionView | null>(null)
const selected = ref('')
const result = ref<{ correct: boolean; explanation: string | null; standardAnswer: string | null } | null>(null)
const message = ref<string | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  message.value = null
  result.value = null
  selected.value = ''
  try {
    question.value = await api.question(examRef.value)
  } catch (e) {
    question.value = null
    message.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!question.value) return
  if (!selected.value) {
    message.value = '请先选择一个选项。'
    return
  }
  try {
    const answer = await api.submitAnswer(question.value.id, selected.value)
    result.value = {
      correct: answer.correct,
      explanation: answer.explanation,
      standardAnswer: answer.standardAnswer,
    }
    message.value = null
  } catch (e) {
    message.value = e instanceof Error ? e.message : '提交失败'
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <header class="space-y-1">
      <h1 class="text-2xl font-bold tracking-tight text-[#0b1f45]">刷题</h1>
      <p class="text-sm text-[#5c6f94]">
        M0 提供逐题作答闭环；题目队列、错题本与综合题评分在 M1 实现。
      </p>
    </header>

    <div class="flex flex-wrap items-end gap-3">
      <label class="text-sm">
        <span class="text-[#5c6f94]">题目 id</span>
        <input
          v-model="examRef"
          class="ml-2 w-48 rounded-lg border border-[#e2e9f5] px-3 py-1.5 text-sm outline-none focus:border-[#1f49ba]"
          placeholder="exam-2026-1"
          @keyup.enter="load"
        />
      </label>
      <button
        class="rounded-lg bg-[#1f49ba] px-4 py-1.5 text-sm font-semibold text-white hover:bg-[#1a3fa0]"
        @click="load"
      >
        载入
      </button>
      <span class="text-xs text-[#9aa9c3]">示例：exam-2026-1、exam-2024-41（综合题）</span>
    </div>

    <p v-if="loading" class="text-sm text-[#9aa9c3]">加载中…</p>
    <p
      v-if="message"
      class="rounded-lg border border-[#ffd9a0] bg-[#fff9ef] px-4 py-3 text-sm text-[#b8730a]"
    >
      {{ message }}
    </p>

    <article v-if="question" class="space-y-4 rounded-xl border border-[#e2e9f5] bg-white p-6">
      <div class="flex flex-wrap items-center gap-2 text-xs text-[#9aa9c3]">
        <span class="rounded bg-[#eef2fa] px-2 py-0.5 font-medium text-[#5c6f94]">{{ question.id }}</span>
        <span>第 {{ question.number }} 题</span>
        <span>{{ question.chapterName }}</span>
        <span class="font-semibold text-[#1845ad]">{{ question.score }} 分</span>
        <span v-if="question.difficulty">难度 {{ question.difficulty }}</span>
      </div>

      <pre class="whitespace-pre-wrap font-sans text-sm leading-relaxed text-[#0b1f45]">{{ question.stem }}</pre>

      <img
        v-for="path in question.imagePaths"
        :key="path"
        :src="path"
        :alt="`题目配图 ${question.number}`"
        class="max-w-full rounded-lg border border-[#e2e9f5]"
      />

      <div v-if="question.options.length" class="space-y-2">
        <label
          v-for="option in question.options"
          :key="option.key"
          class="flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-2.5 text-sm transition"
          :class="
            selected === option.key
              ? 'border-[#1f49ba] bg-[#f2f7ff]'
              : 'border-[#e2e9f5] hover:border-[#bcd3ff]'
          "
        >
          <input v-model="selected" type="radio" :value="option.key" class="mt-1" />
          <span class="text-[#0b1f45]"><strong class="mr-1">{{ option.key }}.</strong>{{ option.text }}</span>
        </label>
      </div>

      <div v-else class="space-y-2">
        <p class="text-xs text-[#9aa9c3]">综合题：M0 仅记录完成状态，评分在 M1 实现。</p>
        <div
          v-for="sub in question.subQuestions"
          :key="sub.number"
          class="rounded-lg bg-[#f8fbff] px-4 py-2 text-sm text-[#0b1f45]"
        >
          <span class="font-semibold">({{ sub.number }})</span> {{ sub.description }}
          <span class="text-xs text-[#9aa9c3]">（{{ sub.score }} 分）</span>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <button
          class="rounded-lg bg-[#1f49ba] px-4 py-2 text-sm font-semibold text-white hover:bg-[#1a3fa0]"
          @click="submit"
        >
          提交答案
        </button>
        <span
          v-if="result"
          class="text-sm font-semibold"
          :class="result.correct ? 'text-[#0b7d70]' : 'text-[#c0392b]'"
        >
          {{ result.correct ? '回答正确' : '回答错误' }}
          <span v-if="result.standardAnswer" class="font-normal text-[#9aa9c3]">
            （正确答案 {{ result.standardAnswer }}）
          </span>
        </span>
      </div>

      <div v-if="result?.explanation" class="rounded-lg border border-[#e2e9f5] bg-[#f8fbff] p-4">
        <p class="text-xs font-semibold text-[#9aa9c3]">解析</p>
        <pre
          class="prose-code mt-2 whitespace-pre-wrap font-sans text-sm leading-relaxed text-[#3d4d6b]"
          >{{ result.explanation }}</pre
        >
      </div>

      <div v-if="question.knowledgeBlockIds.length" class="text-xs text-[#9aa9c3]">
        关联知识块：{{ question.knowledgeBlockIds.join('、') }}
      </div>
    </article>
  </div>
</template>