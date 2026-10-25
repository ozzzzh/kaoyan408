<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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
/** 上一次操作的结果提示，如「3 天后再见」 */
const lastInterval = ref<number | null>(null)
/** 上一次标记是否答对，撤回时据此回退正确的计数 */
const lastResult = ref<boolean | null>(null)
/** 键盘是否可用：输入焦点在别处时不应拦截按键 */
const keyboardHint = ref(true)
/** 本轮各盒的初始分布，用于完成页对比「卡片往右移了多少」 */
const boxesAtStart = ref<number[]>([])

const current = computed(() => cards.value[index.value] ?? null)
const finished = computed(
  () => !loading.value && cards.value.length > 0 && index.value >= cards.value.length,
)
const empty = computed(() => !loading.value && cards.value.length === 0)

const progress = computed(() =>
  cards.value.length
    ? `${Math.min(index.value + 1, cards.value.length)} / ${cards.value.length}`
    : '—',
)

/** 本轮进度百分比，用于顶部进度条 */
const progressPercent = computed(() =>
  cards.value.length === 0
    ? 0
    : Math.round((Math.min(index.value, cards.value.length) / cards.value.length) * 100),
)

/** 本轮还剩多少张没看 */
const remaining = computed(() => Math.max(0, cards.value.length - index.value))

/**
 * 抽认卡难度（Leitner 盒 → 中文标签）。
 * 盒号越大间隔越长，这里同时给出「下次间隔」，让用户知道这张卡的去向。
 */
function boxLabel(box: number): { text: string; cls: string } {
  if (box >= 6) return { text: '很熟', cls: 'bg-[#0f7a4d]/12 text-[#0f7a4d] dark:text-[#4ade80]' }
  if (box >= 4) return { text: '较熟', cls: 'bg-[#0f7a4d]/12 text-[#0f7a4d] dark:text-[#4ade80]' }
  if (box >= 2) return { text: '一般', cls: 'bg-[#8a6d1f]/12 text-[#8a6d1f] dark:text-[#fbbf24]' }
  return { text: '生疏', cls: 'bg-[#b23c3c]/12 text-[#b23c3c] dark:text-[#f87171]' }
}

/** 盒号 → 间隔天数，与后端 BOX_INTERVALS 一致 */
const BOX_INTERVALS = [0, 1, 2, 4, 7, 15, 30, 60]

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
  const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return escaped
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br />')
}

/** 科目短名，用于左栏统计与按钮 */
const SUBJECT_SHORT: Record<string, string> = {
  ds: '数据结构',
  os: '操作系统',
  co: '组成原理',
  cn: '计网',
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
  lastInterval.value = null
  lastResult.value = null
  try {
    const due = await api.memoryDue()
    cards.value = due.items
    index.value = 0
    revealed.value = false
    sessionDone.value = 0
    sessionCorrect.value = 0
    await loadSummary()
    boxesAtStart.value = [...(summary.value?.boxes ?? [])]
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
    const result = await api.memoryReview(card.cardId, remembered)
    sessionDone.value += 1
    if (remembered) sessionCorrect.value += 1
    // 后端返回本次安排的间隔天数，据此提示「几天后再见」
    lastInterval.value = typeof result?.intervalDays === 'number' ? result.intervalDays : null
    lastResult.value = remembered
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

/**
 * 撤回到上一张：手滑点错时用。只回退本地游标，不改动已提交的记录。
 *
 * 注意：必须同时回退对应的计数。原先只减 sessionDone，
 * 撤回一张「答对」的卡片后会得到「已记住 1 / 没记住 -1」这种负数。
 * 因此用 lastResult 记录上一次标记结果，撤回时按类型回退。
 */
function undo() {
  if (index.value === 0 || busy.value) return
  index.value -= 1
  revealed.value = false
  lastInterval.value = null
  if (sessionDone.value > 0) sessionDone.value -= 1
  if (lastResult.value && sessionCorrect.value > 0) sessionCorrect.value -= 1
  lastResult.value = null
}

/**
 * 键盘操作。
 * 背卡片是高频重复动作，一天可能点几百次，鼠标效率太低。
 * 空格/回车翻面，1/← 没记住，2/→ 记住了，Backspace 撤回。
 * 在输入框里打字时不拦截，避免影响正常输入。
 */
function onKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null
  const tag = target?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || target?.isContentEditable) {
    keyboardHint.value = false
    return
  }
  keyboardHint.value = true
  if (!current.value) return

  const key = event.key

  // 撤回不受翻面状态限制：标记完会立刻切到下一张（未翻面），
  // 此时按退格应当能退回上一张，否则「手滑点错」根本来不及撤。
  if (key === 'Backspace') {
    event.preventDefault()
    undo()
    return
  }

  if (key === ' ' || key === 'Enter') {
    event.preventDefault()
    if (!revealed.value) revealed.value = true
    return
  }

  // 标记只在翻面后可操作，避免没看答案就盲选
  if (!revealed.value) return
  if (key === '1' || key === 'ArrowLeft') {
    event.preventDefault()
    void review(false)
  } else if (key === '2' || key === 'ArrowRight') {
    event.preventDefault()
    void review(true)
  }
}

onMounted(() => {
  void load()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="space-y-5">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">背诵</h1>
        <p class="text-sm text-[var(--k-text-muted)]">
          按记忆曲线安排复习：记住就拉长间隔，忘记就回到明天
          <span v-if="summary" class="text-[var(--k-text-faint)]">
            · 共 {{ summary.total }} 张 · 到期 {{ summary.due }} 张
          </span>
        </p>
      </div>

      <!--
        各科添加按钮带上「还能加多少张」，用户才知道点了会发生什么。
        数字为 0 时按钮置灰，避免点了没反应。
      -->
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-for="item in summary?.subjects ?? []"
          :key="item.subject"
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-[var(--k-border)] disabled:hover:text-[var(--k-text-muted)]"
          :disabled="busy || !userStore.user || item.addable === 0"
          :title="item.addable === 0 ? '该科目可加入的要点都已加入' : `把「${item.name}」的 ${item.addable} 个要点加入背诵`"
          @click="addCards({ subject: item.subject })"
        >
          + {{ SUBJECT_SHORT[item.subject] ?? item.name }}
          <span class="tabular-nums text-[var(--k-text-faint)]">({{ item.addable }})</span>
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
        class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-6 py-10"
      >
        <div class="text-center">
          <p class="text-lg font-bold text-[var(--k-text)]">本轮复习完成</p>
          <p class="mt-2 text-sm text-[var(--k-text-muted)]">
            共复习 {{ sessionDone }} 张，记住 {{ sessionCorrect }} 张
            <span v-if="sessionDone" class="text-[var(--k-text-faint)]">
              （{{ Math.round((sessionCorrect / sessionDone) * 100) }}%）
            </span>
          </p>
        </div>

        <!--
          各盒分布：把「记忆牢固程度」画出来，比一行字更有推进感。
          卡片从左边盒子往右移动 = 记忆在变牢，这是 Leitner 的核心反馈。
        -->
        <div v-if="summary" class="mx-auto mt-6 max-w-md">
          <p class="mb-2 text-[11px] font-semibold text-[var(--k-text-faint)]">
            记忆分布（盒号越大、间隔越长）
          </p>
          <div class="flex items-end gap-1.5">
            <div
              v-for="(count, box) in summary.boxes"
              :key="box"
              class="flex flex-1 flex-col items-center gap-1"
              :title="`${box} 盒 · 间隔 ${BOX_INTERVALS[box]} 天 · ${count} 张`"
            >
              <div class="flex h-16 w-full items-end">
                <div
                  class="w-full rounded-t transition-[height] duration-300"
                  :class="box >= 4 ? 'bg-[#0f7a4d]' : box >= 2 ? 'bg-[#8a6d1f]' : 'bg-[var(--k-accent)]'"
                  :style="{
                    height:
                      (summary.total === 0
                        ? 0
                        : Math.max(count > 0 ? 8 : 2, (count / Math.max(1, summary.total)) * 64)) + 'px',
                  }"
                />
              </div>
              <span class="text-[10px] tabular-nums text-[var(--k-text-faint)]">{{ count }}</span>
              <span class="text-[9px] text-[var(--k-text-faint)]">{{ BOX_INTERVALS[box] }}d</span>
            </div>
          </div>
          <p class="mt-2 text-[11px] text-[var(--k-text-faint)]">
            本轮完成后，记住的卡片会往右侧移动。
          </p>
        </div>

        <div class="mt-6 text-center">
          <button
            class="rounded-lg bg-[var(--k-accent)] px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-[var(--k-accent-hover)]"
            @click="load"
          >
            再检查一次
          </button>
        </div>
      </div>

      <!--
        复习区：三栏布局。
        左右栏由 200px 收到 170px，把省下的宽度给卡片（720 → 840px）：
        背卡片是「扫一眼」的场景，卡片与字号都大一点更舒服。
      -->
      <div
        v-else-if="current"
        class="grid items-start gap-5 xl:grid-cols-[170px_minmax(0,1fr)_170px]"
      >
        <!-- 左栏：本轮进度（窄屏隐藏，避免挤压卡片） -->
        <aside class="hidden space-y-3 xl:block">
          <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4">
            <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">本轮进度</p>
            <p class="mt-2 text-2xl font-bold tabular-nums text-[var(--k-text)]">{{ progress }}</p>
            <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-[var(--k-track)]">
              <div
                class="h-full rounded-full bg-[var(--k-accent)] transition-[width] duration-300"
                :style="{ width: progressPercent + '%' }"
              />
            </div>
            <dl class="mt-3 space-y-1.5 text-[11px]">
              <div class="flex justify-between">
                <dt class="text-[var(--k-text-faint)]">已记住</dt>
                <dd class="font-semibold tabular-nums text-[#0f7a4d] dark:text-[#4ade80]">
                  {{ sessionCorrect }}
                </dd>
              </div>
              <div class="flex justify-between">
                <dt class="text-[var(--k-text-faint)]">没记住</dt>
                <dd class="font-semibold tabular-nums text-[#b23c3c] dark:text-[#f87171]">
                  {{ sessionDone - sessionCorrect }}
                </dd>
              </div>
              <div class="flex justify-between">
                <dt class="text-[var(--k-text-faint)]">还剩</dt>
                <dd class="font-semibold tabular-nums text-[var(--k-text-muted)]">{{ remaining }}</dd>
              </div>
            </dl>
          </div>

          <div
            v-if="summary"
            class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4 text-[11px]"
          >
            <p class="font-semibold text-[var(--k-text-faint)]">累计</p>
            <p class="mt-2 text-[var(--k-text-muted)]">
              复习 <strong class="tabular-nums text-[var(--k-text)]">{{ summary.reviews }}</strong> 次
            </p>
            <p class="mt-1 text-[var(--k-text-muted)]">
              正确率
              <strong class="tabular-nums text-[var(--k-text)]">{{ summary.accuracy }}%</strong>
            </p>
          </div>
        </aside>

        <!-- 中间：卡片 -->
        <div class="mx-auto w-full max-w-[840px] space-y-4">
          <div class="flex items-center justify-between text-xs text-[var(--k-text-faint)]">
            <span class="tabular-nums xl:hidden">{{ progress }}</span>
            <span class="hidden xl:inline">{{ current.pointTitle }}</span>
            <span class="flex items-center gap-2">
              <span class="rounded px-2 py-0.5 font-semibold" :class="boxLabel(current.box).cls">
                {{ boxLabel(current.box).text }}
              </span>
              <span>复习 {{ current.reviewCount }} 次</span>
            </span>
          </div>

          <!--
            卡片改为「翻卡片」的观感：
            - 阴影 + 较明显的边框，让卡片从背景里浮起来
            - 顶部一条进度线，融入卡片上沿（不额外占高度）
            - 高度不再写死：有 min-h 兜底（防抖动），内容多时自然撑高；
              正文区若超出则内部滚动，不会把页面拉得很长。
            - 翻面用轻微过渡，不是硬切。
          -->
          <article
            class="relative flex min-h-[340px] flex-col overflow-hidden rounded-2xl border border-[var(--k-border-strong)] bg-[var(--k-surface)] shadow-[0_8px_28px_rgba(20,32,60,0.10)] transition-shadow duration-200 dark:shadow-[0_8px_28px_rgba(0,0,0,0.35)]"
          >
            <!-- 顶部进度线：贴在卡片上沿 -->
            <div class="h-1 w-full shrink-0 bg-[var(--k-track)]">
              <div
                class="h-full bg-[var(--k-accent)] transition-[width] duration-300"
                :style="{ width: progressPercent + '%' }"
              />
            </div>

            <div class="flex flex-1 flex-col px-7 py-7 md:px-10 md:py-8">
              <p class="mb-4 flex flex-wrap items-center gap-2 text-xs">
                <span
                  class="rounded bg-[var(--k-chip)] px-2 py-0.5 font-medium text-[var(--k-text-muted)]"
                >
                  {{ parseCard(current).hint }}
                </span>
                <span class="text-[var(--k-text-faint)]">
                  {{ SUBJECT_SHORT[current.subject] ?? current.subject }}
                </span>
                <RouterLink
                  :to="`/learn/${current.pointId}`"
                  class="text-[var(--k-accent-text)] no-underline hover:underline"
                >
                  {{ current.pointTitle }} →
                </RouterLink>
              </p>

              <!-- 标题加大到 22px：扫一眼就能抓住要点 -->
              <h2 class="text-[22px] font-bold leading-snug text-[var(--k-text)]">
                {{ parseCard(current).title }}
              </h2>

            <!-- 先回忆，再翻面 -->
            <!-- 未翻面：提示与按钮整体垂直居中，短卡片不会显得上重下空 -->
            <div v-if="!revealed" class="flex flex-1 flex-col items-center justify-center py-6 text-center">
              <p class="text-[15px] text-[var(--k-text-faint)]">先在心里回想一遍，再翻面核对</p>
              <button
                class="mt-5 rounded-xl bg-[var(--k-accent)] px-8 py-3.5 text-[15px] font-semibold text-white transition hover:bg-[var(--k-accent-hover)]"
                @click="revealed = true"
              >
                显示答案
              </button>
              <p class="mt-4 text-[11px] text-[var(--k-text-faint)]">
                或按 <kbd class="rounded border border-[var(--k-border)] px-1.5 py-0.5">空格</kbd> 翻面
              </p>
            </div>

            <!--
              已翻面：正文垂直居中（短内容不贴上、长内容内部滚动）。
              字号 16 → 18px，行距 1.9：背卡片是扫读场景，字大一点更省力。
            -->
            <div v-else class="flex flex-1 flex-col justify-center py-2">
              <div class="max-h-[46vh] overflow-y-auto pr-1">
                <p
                  class="text-[18px] leading-[1.85] text-[var(--k-text)] [&_code]:rounded [&_code]:bg-[var(--k-chip)] [&_code]:px-1 [&_code]:py-0.5 [&_code]:font-mono [&_code]:text-[.9em]"
                  v-html="renderBody(parseCard(current).body)"
                ></p>
              </div>
            </div>

            <!--
              标记按钮移进卡片底部：卡片成为完整闭环，视线不必在卡片与下方按钮间来回。
              与正文之间加一条分隔线，明确区分「读」与「评」。
            -->
            <div
              v-if="revealed"
              class="mt-6 flex shrink-0 flex-wrap items-center justify-center gap-3 border-t border-[var(--k-border)] pt-5"
            >
              <button
                class="flex-1 rounded-xl border border-[#b23c3c]/40 bg-[#b23c3c]/8 px-6 py-3 text-[15px] font-semibold text-[#b23c3c] transition hover:bg-[#b23c3c]/14 disabled:opacity-50 sm:flex-none dark:text-[#f87171]"
                :disabled="busy"
                @click="review(false)"
              >
                没记住 · 明天再来
                <kbd class="ml-1.5 rounded border border-current/30 px-1 text-[10px] opacity-60">1</kbd>
              </button>
              <button
                class="flex-1 rounded-xl border border-[#0f7a4d]/40 bg-[#0f7a4d]/8 px-6 py-3 text-[15px] font-semibold text-[#0f7a4d] transition hover:bg-[#0f7a4d]/14 disabled:opacity-50 sm:flex-none dark:text-[#4ade80]"
                :disabled="busy"
                @click="review(true)"
              >
                记住了 · 拉长间隔
                <kbd class="ml-1.5 rounded border border-current/30 px-1 text-[10px] opacity-60">2</kbd>
              </button>
            </div>
            </div>
          </article>

          <!-- 上次操作结果 + 撤回 -->
          <div
            v-if="lastInterval !== null"
            class="flex items-center justify-center gap-3 text-[11px] text-[var(--k-text-faint)]"
          >
            <span>
              上一张已安排
              <strong class="text-[var(--k-text-muted)]">
                {{ lastInterval === 0 ? '明天' : lastInterval + ' 天后' }}
              </strong>
              再复习
            </span>
            <button
              class="rounded border border-[var(--k-border)] px-2 py-0.5 transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
              :disabled="busy"
              @click="undo"
            >
              撤回
            </button>
          </div>
        </div>

        <!-- 右栏：记忆分布 -->
        <aside v-if="summary" class="hidden space-y-3 xl:block">
          <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4">
            <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">记忆分布</p>
            <div class="mt-3 space-y-1.5">
              <div
                v-for="(count, box) in summary.boxes"
                :key="box"
                class="flex items-center gap-2 text-[10px]"
                :title="`${box} 盒 · 间隔 ${BOX_INTERVALS[box]} 天 · ${count} 张`"
              >
                <span class="w-7 shrink-0 tabular-nums text-[var(--k-text-faint)]">
                  {{ BOX_INTERVALS[box] }}d
                </span>
                <div class="h-2 flex-1 overflow-hidden rounded-full bg-[var(--k-track)]">
                  <div
                    class="h-full rounded-full transition-[width] duration-300"
                    :class="box >= 4 ? 'bg-[#0f7a4d]' : box >= 2 ? 'bg-[#8a6d1f]' : 'bg-[var(--k-accent)]'"
                    :style="{
                      width:
                        (summary.total === 0 ? 0 : (count / Math.max(1, summary.total)) * 100) + '%',
                    }"
                  />
                </div>
                <span class="w-6 shrink-0 text-right tabular-nums text-[var(--k-text-muted)]">
                  {{ count }}
                </span>
              </div>
            </div>
            <p class="mt-3 border-t border-[var(--k-border)] pt-2 text-[10px] leading-relaxed text-[var(--k-text-faint)]">
              盒号越大间隔越长。<br />记住一张就往右移一格。
            </p>
          </div>

          <div
            class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4 text-[10px] leading-relaxed text-[var(--k-text-faint)]"
          >
            <p class="mb-1.5 font-semibold">快捷键</p>
            <p><kbd class="rounded border border-[var(--k-border)] px-1">空格</kbd> 翻面</p>
            <p class="mt-1"><kbd class="rounded border border-[var(--k-border)] px-1">1</kbd> 没记住</p>
            <p class="mt-1"><kbd class="rounded border border-[var(--k-border)] px-1">2</kbd> 记住了</p>
            <p class="mt-1"><kbd class="rounded border border-[var(--k-border)] px-1">退格</kbd> 撤回</p>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>
