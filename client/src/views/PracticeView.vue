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
      <h1 class="text-2xl font-semibold tracking-tight">刷题</h1>
      <p class="text-sm text-ink-600">
        M0 提供逐题作答闭环；题目队列、错题本与综合题评分在 M1 实现。
      </p>
    </header>

    <div class="flex flex-wrap items-end gap-3">
      <label class="text-sm">
        <span class="text-ink-600">题目 id</span>
        <input
          v-model="examRef"
          class="ml-2 w-48 rounded-lg border border-ink-200 px-3 py-1.5 text-sm outline-none focus:border-salt-400"
          placeholder="exam-2026-1"
          @keyup.enter="load"
        />
      </label>
      <button class="rounded-lg bg-ink-800 px-4 py-1.5 text-sm text-white hover:bg-ink-900" @click="load">
        载入
      </button>
      <span class="text-xs text-ink-400">示例：exam-2026-1、exam-2024-41（综合题）</span>
    </div>

    <p v-if="loading" class="text-sm text-ink-400">加载中…</p>
    <p v-if="message" class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      {{ message }}
    </p>

    <article v-if="question" class="space-y-4 rounded-xl border border-ink-200 bg-white p-6">
      <div class="flex flex-wrap items-center gap-2 text-xs text-ink-400">
        <span class="rounded bg-ink-100 px-2 py-0.5">{{ question.id }}</span>
        <span>第 {{ question.number }} 题</span>
        <span>{{ question.chapterName }}</span>
        <span>{{ question.score }} 分</span>
        <span v-if="question.difficulty">难度 {{ question.difficulty }}</span>
      </div>

      <pre class="whitespace-pre-wrap font-sans text-sm leading-relaxed text-ink-900">{{ question.stem }}</pre>

      <img
        v-for="path in question.imagePaths"
        :key="path"
        :src="path"
        :alt="`题目配图 ${question.number}`"
        class="max-w-full rounded-lg border border-ink-200"
      />

      <div v-if="question.options.length" class="space-y-2">
        <label
          v-for="option in question.options"
          :key="option.key"
          class="flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-2.5 text-sm transition"
          :class="
            selected === option.key
              ? 'border-salt-500 bg-salt-500/5'
              : 'border-ink-200 hover:border-ink-400'
          "
        >
          <input v-model="selected" type="radio" :value="option.key" class="mt-1" />
          <span><strong class="mr-1">{{ option.key }}.</strong>{{ option.text }}</span>
        </label>
      </div>

      <div v-else class="space-y-2">
        <p class="text-xs text-ink-400">综合题：M0 仅记录完成状态，评分在 M1 实现。</p>
        <div v-for="sub in question.subQuestions" :key="sub.number" class="rounded-lg bg-ink-50 px-4 py-2 text-sm">
          <span class="font-medium">({{ sub.number }})</span> {{ sub.description }}
          <span class="text-xs text-ink-400">（{{ sub.score }} 分）</span>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <button
          class="rounded-lg bg-salt-500 px-4 py-2 text-sm font-medium text-white hover:bg-salt-600"
          @click="submit"
        >
          提交答案
        </button>
        <span v-if="result" class="text-sm" :class="result.correct ? 'text-emerald-600' : 'text-rose-600'">
          {{ result.correct ? '回答正确' : '回答错误' }}
          <span v-if="result.standardAnswer" class="text-ink-400">（正确答案 {{ result.standardAnswer }}）</span>
        </span>
      </div>

      <div v-if="result?.explanation" class="rounded-lg border border-ink-200 bg-ink-50 p-4">
        <p class="text-xs font-medium text-ink-400">解析</p>
        <pre class="prose-code mt-2 whitespace-pre-wrap font-sans text-sm leading-relaxed text-ink-800">{{ result.explanation }}</pre>
      </div>

      <div v-if="question.knowledgeBlockIds.length" class="text-xs text-ink-400">
        关联知识块：{{ question.knowledgeBlockIds.join('、') }}
      </div>
    </article>
  </div>
</template>