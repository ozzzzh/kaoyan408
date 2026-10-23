<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { MemoryCardItem, MemorySummary } from '@/api/types'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const cards = ref<MemoryCardItem[]>([])
const summary = ref<MemorySummary | null>(null)
const index = ref(0)
/** 是否已翻面（先回忆再看答案） */
const revealed = ref(false)
const loading = ref(true)
const busy = ref(false)
const message = ref<string | null>(null)
/** 本次会话的复习计数 */
const sessionDone = ref(0)
const sessionCorrect = ref(0)

const current = computed(() => cards.value[index.value] ?? null)
const finished = computed(() => !loading.value && cards.value.length > 0 && index.value >= cards.value.length)
const empty = computed(() => !loading.value && cards.value.length === 0)

const progress = computed(() =>
  cards.value.length ? `${Math.min(index.value + 1, cards.value.length)} / ${cards.value.length}` : '—',
)

/** 记忆盒 → 熟练度标签 */
function boxLabel(box: number): { text: string; cls: string } {
  if (box >= 6) return { text: '很熟', cls: 'bg-[#0f7a4d]/12 text-[#0f7a4d]' }
  if (box >= 4) return { text: '较熟', cls: 'bg-[#0f7a4d]/12 text-[#0f7a4d]' }
  if (box >= 2) return { text: '一般', cls: 'bg-[#8a6d1f]/12 text-[#8a6d1f]' }
  return { text: '生疏', cls: 'bg-[#b23c3c]/12 text-[#b23c3c]' }
}

/** 把块的 content_json 解析成可读的卡片正面/背面。 */
function parseCard(card: MemoryCardItem): { title: string; body: string; hint: string } {
  try {
    const parsed = JSON.parse(card.content) as Record<string, unknown>
    if (parsed.type === 'formula') {
      return {
        title: card.subpointTitle ?? card.pointTitle,
        body: String(parsed.formula ?? ''),
        hint: parsed.caption ? String(parsed.caption) : '公式',
      }
    }
    if (parsed.type === 'callout') {
      return {
        title: String(parsed.title ?? card.subpointTitle ?? '要点'),
        body: String(parsed.text ?? ''),
        hint: '要点',
      }
    }
    return {
      title: card.subpointTitle ?? card.pointTitle,
      body: String(parsed.text ?? ''),
      hint: card.type,
    }
  } catch {
    return { title: card.subpointTitle ?? card.pointTitle, body: card.content, hint: card.type }
  }
}

/** 卡片背面里的 $...$ 行内公式与 **加粗** 做轻量处理（不引入完整 Markdown）。 */
function renderBody(text: string): string {
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br />')
}

async function loadSummary() {
  try {
    summary.value = await api.memorySummary()
  } catch {
    summary.value = null
  }
}

async function load() {
  loading.value = true
  message.value = null
  try {
    const due = await api.memoryDue()
    cards.value = due.items
    index.value = 0
    revealed.value = false
    sessionDone.value = 0
    sessionCorrect.value = 0
    await loadSummary()
  } catch (e) {
    message.value = e instanceof Error ? e.message : '加载失败'
    cards.value = []
  } finally {
    loading.value = false
  }
}

async function review(remembered: boolean) {
  const card = current.value
  if (!card || busy.value) return
  busy.value = true
  try {
    await api.memoryReview(card.cardId, remembered)
    sessionDone.value += 1
    if (remembered) sessionCorrect.value += 1
    index.value += 1
    revealed.value = false
  } catch (e) {
    message.value = e instanceof Error ? e.message : '提交失败'
  } finally {
    busy.value = false
  }
}

/** 一键把某些内容的要点加入背诵 */
async function addCards(scope: { subject?: string }) {
  busy.value = true
  message.value = null
  try {
    const result = await api.memoryAdd({ ...scope, limit: 20 })
    message.value =
      result.added > 0
        ? `已加入 ${result.added} 张卡片`
        : '没有可加入的新卡片（该范围内的要点都已加入）'
    await load()
  } catch (e) {
    message.value = e instanceof Error ? e.message : '添加失败'
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-5">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">背诵</h1>
        <p class="text-sm text-[var(--k-text-muted)]">
          按记忆曲线安排复习：记住就拉长间隔，忘记就回到明天
          <span v-if="summary" class="text-[var(--k-text-faint)]">
            · 共 {{ summary.total }} 张 · 到期 {{ summary.due }} 张 · 牢固 {{ summary.solid }} 张
          </span>
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-50"
          :disabled="busy || !userStore.user"
          @click="addCards({ subject: 'ds' })"
        >
          + 数据结构要点
        </button>
        <button
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-50"
          :disabled="busy || !userStore.user"
          @click="addCards({ subject: 'os' })"
        >
          + 操作系统要点
        </button>
        <button
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-50"
          :disabled="busy || !userStore.user"
          @click="addCards({ subject: 'co' })"
        >
          + 组成原理要点
        </button>
        <button
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-50"
          :disabled="busy || !userStore.user"
          @click="addCards({ subject: 'cn' })"
        >
          + 计网要点
        </button>
      </div>
    </header>

    <!-- 未登录 -->
    <div
      v-if="!userStore.user"
      class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] px-6 py-12 text-center"
    >
      <p class="text-sm text-[var(--k-text-muted)]">背诵记录需要保存到账号，请先登录。</p>
      <RouterLink
        to="/me"
        class="mt-4 inline-block rounded-lg bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-white no-underline hover:bg-[var(--k-accent-hover)]"
      >
        去登录
      </RouterLink>
    </div>

    <template v-else>
      <p
        v-if="message"
        class="rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-4 py-3 text-sm text-[var(--k-text-muted)]"
      >
        {{ message }}
      </p>

      <p v-if="loading" class="py-10 text-center text-sm text-[var(--k-text-faint)]">加载中…</p>

      <!-- 没有到期卡片 -->
      <div
        v-else-if="empty"
        class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] px-6 py-12 text-center"
      >
        <p class="text-sm text-[var(--k-text-muted)]">
          {{ summary && summary.total > 0 ? '今天该复习的都复习完了。' : '还没有背诵卡片。' }}
        </p>
        <p class="mt-2 text-xs text-[var(--k-text-faint)]">
          {{
            summary && summary.total > 0
              ? `共 ${summary.total} 张卡片，按记忆曲线会在到期日再次出现。`
              : '点右上角按钮，把各科的「要点」与「公式」加入背诵。'
          }}
        </p>
      </div>

      <!-- 复习完成 -->
      <div
        v-else-if="finished"
        class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-6 py-12 text-center"
      >
        <p class="text-lg font-bold text-[var(--k-text)]">本轮复习完成</p>
        <p class="mt-2 text-sm text-[var(--k-text-muted)]">
          共复习 {{ sessionDone }} 张，记住 {{ sessionCorrect }} 张
          <span v-if="sessionDone" class="text-[var(--k-text-faint)]">
            （{{ Math.round((sessionCorrect / sessionDone) * 100) }}%）
          </span>
        </p>
        <button
          class="mt-5 rounded-lg bg-[var(--k-accent)] px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-[var(--k-accent-hover)]"
          @click="load"
        >
          再检查一次
        </button>
      </div>

      <!-- 卡片 -->
      <div v-else-if="current" class="mx-auto max-w-2xl space-y-4">
        <div class="flex items-center justify-between text-xs text-[var(--k-text-faint)]">
          <span class="tabular-nums">{{ progress }}</span>
          <span class="flex items-center gap-2">
            <span class="rounded px-2 py-0.5 font-semibold" :class="boxLabel(current.box).cls">
              {{ boxLabel(current.box).text }}
            </span>
            <span>复习 {{ current.reviewCount }} 次</span>
          </span>
        </div>

        <article
          class="rounded-2xl border border-[var(--k-border)] bg-[var(--k-surface)] px-6 py-8 md:px-8 md:py-10"
        >
          <p class="mb-3 flex flex-wrap items-center gap-2 text-xs">
            <span class="rounded bg-[var(--k-chip)] px-2 py-0.5 font-medium text-[var(--k-text-muted)]">
              {{ parseCard(current).hint }}
            </span>
            <RouterLink
              :to="`/learn/${current.pointId}`"
              class="text-[var(--k-accent-text)] no-underline hover:underline"
            >
              {{ current.pointTitle }} →
            </RouterLink>
          </p>

          <h2 class="text-lg font-bold leading-snug text-[var(--k-text)]">
            {{ parseCard(current).title }}
          </h2>

          <!-- 先回忆，再翻面 -->
          <div v-if="!revealed" class="mt-8 text-center">
            <p class="text-sm text-[var(--k-text-faint)]">先在心里回想一遍，再翻面核对</p>
            <button
              class="mt-4 rounded-xl bg-[var(--k-accent)] px-7 py-3 text-sm font-semibold text-white transition hover:bg-[var(--k-accent-hover)]"
              @click="revealed = true"
            >
              显示答案
            </button>
          </div>

          <div v-else class="mt-5">
            <p
              class="text-[16px] leading-[1.9] text-[var(--k-text)] [&_code]:rounded [&_code]:bg-[var(--k-chip)] [&_code]:px-1 [&_code]:py-0.5 [&_code]:font-mono [&_code]:text-[.9em]"
              v-html="renderBody(parseCard(current).body)"
            ></p>
          </div>
        </article>

        <div v-if="revealed" class="flex items-center justify-center gap-3">
          <button
            class="flex-1 rounded-xl border border-[#b23c3c]/40 bg-[#b23c3c]/8 px-6 py-3 text-sm font-semibold text-[#b23c3c] transition hover:bg-[#b23c3c]/14 disabled:opacity-50 sm:flex-none"
            :disabled="busy"
            @click="review(false)"
          >
            没记住 · 明天再来
          </button>
          <button
            class="flex-1 rounded-xl border border-[#0f7a4d]/40 bg-[#0f7a4d]/8 px-6 py-3 text-sm font-semibold text-[#0f7a4d] transition hover:bg-[#0f7a4d]/14 disabled:opacity-50 sm:flex-none"
            :disabled="busy"
            @click="review(true)"
          >
            记住了 · 拉长间隔
          </button>
        </div>
      </div>
    </template>
  </div>
</template>