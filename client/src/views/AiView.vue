<script lang="ts">
/**
 * 知识点标记的解析缓存。
 *
 * 放在 <script setup> 之外：Map 若声明在 setup 内会被 Vue 包成响应式，
 * 每次读写都走 Proxy，流式输出时反而比不缓存更慢。
 */
const segmentCache = new Map<string, unknown[]>()
</script>

<script setup lang="ts">
/**
 * AI 学习助手页面。
 *
 * 三个和普通聊天页不一样的地方：
 *
 * 1. 回答里会出现 [[kp:id|标题]] 标记，渲染成可点击的知识点卡片。
 *    这是这个功能存在的理由——让答疑能落到站内的知识点正文和动画上，
 *    而不是聊完就散。
 * 2. 首字之前有一段推理时间（3-4 秒），期间显示「思考中」而不是空白。
 * 3. 游客只给 3 次，用完提示登录——所以状态里要一直能看到剩余次数。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api, ApiError, streamAiChat } from '@/api/client'
import type { AiChatMessage, AiConversationSummary, AiStatus } from '@/api/types'
import { useUserStore } from '@/store/user'
import StemMarkdown from '@/components/mock/StemMarkdown.vue'
import DiagramView from '@/components/ai/DiagramView.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const status = ref<AiStatus | null>(null)
const conversations = ref<AiConversationSummary[]>([])
const activeId = ref<number | null>(null)
const messages = ref<AiChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const loadingConv = ref(false)
const errorText = ref<string | null>(null)

const scroller = ref<HTMLElement | null>(null)
const inputEl = ref<HTMLTextAreaElement | null>(null)

/** 取消函数：切会话或离开页面时主动断开，避免后端空跑 */
let cancelStream: (() => void) | null = null

const isGuest = computed(() => status.value?.guest === true)
const remaining = computed(() => status.value?.guestRemaining ?? 0)
const configured = computed(() => status.value?.configured !== false)
const canSend = computed(() => !sending.value && configured.value && input.value.trim().length > 0)

/** 快捷提问：空态引导用 */
const starters = [
  { title: '聊聊复习安排', desc: '结合基础、进度与可用时间' },
  { title: '讲清一个知识点', desc: '理解原理，串联前后关系' },
  { title: '分析我的薄弱项', desc: '看错题与掌握度，给下一步' },
]

onMounted(async () => {
  try {
    status.value = await api.aiStatus()
  } catch (e) {
    errorText.value = e instanceof ApiError ? e.message : '无法获取 AI 服务状态'
  }
  await loadConversations()
  await applyRouteContext()
  inputEl.value?.focus()
})

onBeforeUnmount(() => cancelStream?.())

/** 支持从知识点页带上下文进入：?context=kp:os-3-1 */
async function applyRouteContext() {
  const ctx = route.query.context
  if (typeof ctx !== 'string' || !ctx.startsWith('kp:')) return
  const pointId = ctx.slice(3)
  try {
    const points = await api.knowledgePoints()
    const hit = points.find((p) => p.pointId === pointId)
    if (hit) {
      input.value = `请讲讲「${hit.title}」，重点讲清楚原理和常见考点。`
      await nextTick()
      inputEl.value?.focus()
    }
  } catch {
    /* 查不到就当作普通进入 */
  }
}

async function loadConversations() {
  if (!userStore.user) {
    conversations.value = []
    return
  }
  try {
    conversations.value = await api.aiConversations()
  } catch {
    conversations.value = []
  }
}

async function openConversation(id: number) {
  if (sending.value) return
  cancelStream?.()
  activeId.value = id
  loadingConv.value = true
  errorText.value = null
  try {
    const detail = await api.aiConversation(id)
    messages.value = detail.messages.map((m) => ({ role: m.role, content: m.content }))
    await scrollToBottom()
  } catch (e) {
    errorText.value = e instanceof ApiError ? e.message : '会话加载失败'
  } finally {
    loadingConv.value = false
  }
}

function newConversation() {
  cancelStream?.()
  activeId.value = null
  messages.value = []
  errorText.value = null
  input.value = ''
  inputEl.value?.focus()
}

async function removeConversation(id: number, ev: Event) {
  ev.stopPropagation()
  try {
    await api.aiDeleteConversation(id)
    conversations.value = conversations.value.filter((c) => c.id !== id)
    if (activeId.value === id) newConversation()
  } catch (e) {
    errorText.value = e instanceof ApiError ? e.message : '删除失败'
  }
}

async function send(preset?: string) {
  const question = (preset ?? input.value).trim()
  if (!question || sending.value) return
  if (isGuest.value && remaining.value <= 0) {
    errorText.value = '游客试用次数已用完，登录后可继续使用。'
    return
  }

  errorText.value = null
  input.value = ''
  sending.value = true
  messages.value.push({ role: 'user', content: question })

  // 必须用 reactive 包一层再 push。
  // 直接 push 一个普通对象时，Vue 只在数组本身变化时重渲染，
  // 后续对 reply.content 的累加不会进响应式追踪——实测表现为
  // 「等全部输出完才一次性蹦出来」，流式完全失效。
  const reply = reactive<AiChatMessage>({
    role: 'assistant',
    content: '',
    streaming: true,
    thinking: true,
  })
  messages.value.push(reply)
  await scrollToBottom()

  cancelStream = streamAiChat(question, activeId.value, {
    onThinking: () => {
      reply.thinking = true
    },
    onDelta: (text) => {
      // 正文一到就把「思考中」换掉
      reply.thinking = false
      reply.content += text
      void scrollToBottom()
    },
    onDone: async (evt) => {
      reply.streaming = false
      reply.thinking = false
      sending.value = false
      if (evt.conversationId) {
        activeId.value = evt.conversationId
      }
      // 游客的剩余次数会变，重新拉一次状态
      if (isGuest.value) {
        try {
          status.value = await api.aiStatus()
        } catch {
          /* 忽略 */
        }
      } else {
        await loadConversations()
      }
      await scrollToBottom()
    },
    onError: (msg) => {
      reply.streaming = false
      reply.thinking = false
      reply.error = msg
      // 正文全空时把气泡去掉，避免留一个空壳
      if (!reply.content) {
        messages.value = messages.value.filter((m) => m !== reply)
      }
      sending.value = false
      errorText.value = msg
    },
  })
}

/** Enter 发送，Shift+Enter 换行 */
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void send()
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 输入框随内容增高，最多 6 行 */
watch(input, async () => {
  await nextTick()
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
})

/* ---------- 知识点标记解析 ---------- */

interface Segment {
  kind: 'text' | 'kp'
  text?: string
  kpId?: string
  kpTitle?: string
}

/**
 * 把 [[kp:id|标题]] 从正文里拆出来。
 *
 * 用 splice + 手动扫描而不是正则 replace + dangerouslySetInnerHTML：
 * 后者会把模型输出当 HTML 解析，模型一旦输出 <script> 就是 XSS。
 * 这里只产出纯文本与卡片两种节点，正文交给 StemMarkdown 渲染。
 *
 * 带缓存：流式输出时每一帧都会重跑模板，400 个分片就是 400 次全文扫描。
 * 按内容字符串做键，内容一变自然失效，不需要手动清理。
 */
function segmentsOf(content: string): Segment[] {
  const cached = segmentCache.get(content) as Segment[] | undefined
  if (cached) return cached

  const out: Segment[] = []
  const re = /\[\[kp:([^|\]]+)\|([^\]]+)\]\]/g
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(content)) !== null) {
    if (m.index > last) {
      out.push({ kind: 'text', text: content.slice(last, m.index) })
    }
    out.push({ kind: 'kp', kpId: m[1], kpTitle: m[2] })
    last = m.index + m[0].length
  }
  if (last < content.length) {
    out.push({ kind: 'text', text: content.slice(last) })
  }
  const result = out.length ? out : [{ kind: 'text', text: content }]

  // 只留最近若干条，避免整段会话把每条中间态都攒在内存里
  if (segmentCache.size > 40) segmentCache.clear()
  segmentCache.set(content, result)
  return result
}

/**
 * 找这条回答对应的提问。
 * 消息数组是 [user, assistant, user, assistant ...]，
 * 助手消息的前一条就是问题；找不到就退回第一条用户消息。
 */
function previousQuestion(index: number): string {
  for (let i = index - 1; i >= 0; i--) {
    if (messages.value[i]?.role === 'user') return messages.value[i].content
  }
  return messages.value.find((x) => x.role === 'user')?.content ?? ''
}

/**
 * 生成图解。
 *
 * 用回答正文的前 400 字作为上下文，让模型知道该画什么；
 * 整段塞进去没必要——图只要画关系，不需要复述全文。
 */
async function generateDiagram(target: AiChatMessage, question: string) {
  if (target.diagramLoading) return
  target.diagramLoading = true
  target.diagramError = undefined
  try {
    const result = await api.aiDiagram(
      question || '这个知识点',
      (target.content || '').slice(0, 400),
      '',
    )
    target.diagram = result.dsl
    if (isGuest.value) {
      try {
        status.value = await api.aiStatus()
      } catch {
        /* 忽略 */
      }
    }
  } catch (e) {
    target.diagramError = e instanceof ApiError ? e.message : '图解生成失败，换个问法试试。'
  } finally {
    target.diagramLoading = false
  }
}

function goToPoint(id: string) {
  router.push(`/learn/${id}`)
}
</script>

<template>
  <div class="flex h-[calc(100vh-2rem)] gap-4 md:h-[calc(100vh-4rem)]">
    <!-- 左侧：会话列表（游客不显示） -->
    <aside
      v-if="!isGuest"
      class="hidden w-[220px] shrink-0 flex-col overflow-hidden rounded-2xl border border-[var(--k-border)] bg-[var(--k-surface)] lg:flex"
    >
      <div class="p-3">
        <button
          type="button"
          class="flex w-full items-center justify-center gap-1.5 rounded-xl bg-[var(--k-accent)] px-3 py-2.5 text-sm font-semibold text-[var(--k-on-accent)] transition-colors hover:bg-[var(--k-accent-hover)]"
          @click="newConversation"
        >
          <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14" stroke-linecap="round" />
          </svg>
          新建对话
        </button>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        <p v-if="!conversations.length" class="px-2 py-3 text-xs text-[var(--k-text-faint)]">
          暂无历史会话
        </p>
        <button
          v-for="c in conversations"
          :key="c.id"
          type="button"
          class="group mb-0.5 flex w-full items-center gap-1.5 rounded-lg px-2.5 py-2 text-left text-xs transition-colors"
          :class="
            activeId === c.id
              ? 'bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]'
              : 'text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
          "
          @click="openConversation(c.id)"
        >
          <span class="min-w-0 flex-1 truncate">{{ c.title }}</span>
          <span
            class="shrink-0 text-[var(--k-text-faint)] opacity-0 transition-opacity hover:text-[var(--k-danger)] group-hover:opacity-100"
            title="删除会话"
            @click="removeConversation(c.id, $event)"
          >
            <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" />
            </svg>
          </span>
        </button>
      </div>
    </aside>

    <!-- 右侧：对话区 -->
    <section class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-[var(--k-border)] bg-[var(--k-surface)]">
      <!-- 顶栏 -->
      <header class="flex items-center gap-2.5 border-b border-[var(--k-border)] px-4 py-3">
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-sm font-bold text-[var(--k-heading)]">
            {{ activeId ? conversations.find((c) => c.id === activeId)?.title ?? '对话' : '新对话' }}
          </h1>
          <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">
            408 学习助手
            <span v-if="status?.model" class="ml-1.5 opacity-70">· {{ status.model }}</span>
          </p>
        </div>

        <!-- 游客剩余次数 -->
        <span
          v-if="isGuest"
          class="shrink-0 rounded-lg px-2 py-1 text-[11px] font-semibold"
          :class="
            remaining > 0
              ? 'bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]'
              : 'bg-[var(--k-warn-soft)] text-[var(--k-warn)]'
          "
        >
          游客剩余 {{ remaining }} 次
        </span>
        <RouterLink
          v-if="isGuest"
          to="/me"
          class="shrink-0 rounded-lg border border-[var(--k-border-strong)] px-2.5 py-1 text-[11px] font-semibold text-[var(--k-text)] no-underline hover:bg-[var(--k-surface-soft)]"
        >
          登录
        </RouterLink>
      </header>

      <!-- 未配置提示 -->
      <div
        v-if="!configured"
        class="m-4 rounded-xl border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-xs text-[var(--k-text-muted)]"
      >
        AI 服务尚未配置，暂时无法对话。管理员配置 <code class="font-mono">KAOYAN_AI_API_KEY</code> 后即可使用。
      </div>

      <!-- 消息区 -->
      <div ref="scroller" class="min-h-0 flex-1 overflow-y-auto px-4 py-5">
        <!-- 空态 -->
        <div v-if="!messages.length" class="mx-auto flex h-full max-w-[560px] flex-col justify-center">
          <div class="text-center">
            <div class="mx-auto grid h-11 w-11 place-items-center rounded-2xl bg-[var(--k-accent-soft)]">
              <svg viewBox="0 0 24 24" class="h-5 w-5 text-[var(--k-accent-text)]" fill="currentColor">
                <path d="M12 3l1.9 5.6L19.5 10l-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.4L12 3z" />
              </svg>
            </div>
            <h2 class="mt-3.5 text-lg font-bold text-[var(--k-heading)]">今天想聊什么？</h2>
            <p class="mt-1.5 text-xs text-[var(--k-text-muted)]">
              {{
                isGuest
                  ? '游客可试用 3 次。登录后能结合你的错题与掌握度来回答。'
                  : '聊聊你的问题，或者让我看看最近的复习情况。'
              }}
            </p>
          </div>

          <div class="mt-6 grid gap-2 sm:grid-cols-2">
            <button
              v-for="s in starters"
              :key="s.title"
              type="button"
              class="group rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-3.5 py-3 text-left transition-colors hover:border-[var(--k-border-strong)]"
              :disabled="!configured"
              @click="send(s.title)"
            >
              <p class="flex items-center justify-between text-xs font-semibold text-[var(--k-text)]">
                {{ s.title }}
                <span class="text-[var(--k-text-faint)] transition-colors group-hover:text-[var(--k-accent)]">↗</span>
              </p>
              <p class="mt-1 text-[11px] text-[var(--k-text-faint)]">{{ s.desc }}</p>
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="mx-auto max-w-[720px] space-y-4">
          <template v-for="(m, i) in messages" :key="i">
            <!-- 用户 -->
            <div v-if="m.role === 'user'" class="flex justify-end">
              <div
                class="max-w-[85%] whitespace-pre-wrap rounded-2xl rounded-br-md bg-[var(--k-accent)] px-3.5 py-2.5 text-sm leading-relaxed text-[var(--k-on-accent)]"
              >
                {{ m.content }}
              </div>
            </div>

            <!-- 助手 -->
            <div v-else class="flex gap-2.5">
              <span
                class="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]"
              >
                <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="currentColor">
                  <path d="M12 3l1.9 5.6L19.5 10l-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.4L12 3z" />
                </svg>
              </span>

              <div class="min-w-0 flex-1">
                <!-- 思考中 -->
                <div
                  v-if="m.thinking && !m.content"
                  class="inline-flex items-center gap-2 rounded-xl bg-[var(--k-surface-soft)] px-3 py-2 text-xs text-[var(--k-text-muted)]"
                >
                  <span class="flex gap-1">
                    <span class="ai-dot" />
                    <span class="ai-dot" style="animation-delay: 0.15s" />
                    <span class="ai-dot" style="animation-delay: 0.3s" />
                  </span>
                  思考中…
                </div>

                <!-- 正文：按知识点标记切段 -->
                <template v-else>
                  <div class="space-y-2">
                    <template v-for="(seg, si) in segmentsOf(m.content)" :key="si">
                      <!-- 正文走 Markdown 渲染（marked + KaTeX + DOMPurify），
                           模型输出的表格、公式、加粗才能正常显示。
                           不能整段 v-html：那样知识点标记会被当成普通文本。 -->
                      <StemMarkdown
                        v-if="seg.kind === 'text' && seg.text && seg.text.trim()"
                        :source="seg.text"
                      />

                      <!-- 知识点卡片：点击进入知识点页 -->
                      <button
                        v-else-if="seg.kind === 'kp'"
                        type="button"
                        class="flex w-full items-center gap-2.5 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-3 py-2.5 text-left transition-colors hover:border-[var(--k-accent)] hover:bg-[var(--k-accent-soft)]"
                        @click="goToPoint(seg.kpId!)"
                      >
                        <span class="grid h-6 w-6 shrink-0 place-items-center rounded-lg bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]">
                          <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="1.8">
                            <path d="M4 5a2 2 0 0 1 2-2h12v18H6a2 2 0 0 1-2-2V5z" stroke-linejoin="round" />
                            <path d="M8 7h6M8 11h5" stroke-linecap="round" />
                          </svg>
                        </span>
                        <span class="min-w-0 flex-1">
                          <span class="block truncate text-xs font-semibold text-[var(--k-heading)]">
                            {{ seg.kpTitle }}
                          </span>
                          <span class="block text-[10px] text-[var(--k-text-faint)]">查看知识点正文</span>
                        </span>
                        <span class="shrink-0 text-[var(--k-accent)]">→</span>
                      </button>
                    </template>
                  </div>

                  <!-- 流式光标 -->
                  <span v-if="m.streaming" class="ai-caret" />

                  <!--
                    图解：用户点了才生成。
                    不自动生成的原因：一次要 5 秒左右且消耗 token，
                    而多数问题并不需要图；让用户点，也是让「失败」有明确归属。
                  -->
                  <div v-if="!m.streaming" class="mt-2.5">
                    <DiagramView v-if="m.diagram" :dsl="m.diagram" />

                    <template v-else>
                      <button
                        v-if="!m.diagramLoading"
                        type="button"
                        class="inline-flex items-center gap-1.5 rounded-lg border border-[var(--k-border)] px-2.5 py-1.5 text-[11px] font-semibold text-[var(--k-accent-text)] transition-colors hover:border-[var(--k-accent)] hover:bg-[var(--k-accent-soft)]"
                        @click="generateDiagram(m, previousQuestion(i))"
                      >
                        <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="1.8">
                          <rect x="3" y="3" width="7" height="7" rx="1.5" />
                          <rect x="14" y="14" width="7" height="7" rx="1.5" />
                          <path d="M10 6.5h4a3 3 0 0 1 3 3V14" stroke-linecap="round" />
                        </svg>
                        生成图解
                      </button>
                      <p
                        v-else
                        class="inline-flex items-center gap-2 rounded-lg bg-[var(--k-surface-soft)] px-2.5 py-1.5 text-[11px] text-[var(--k-text-muted)]"
                      >
                        <span class="flex gap-1">
                          <span class="ai-dot" />
                          <span class="ai-dot" style="animation-delay: 0.15s" />
                          <span class="ai-dot" style="animation-delay: 0.3s" />
                        </span>
                        正在画图…
                      </p>
                      <p v-if="m.diagramError" class="mt-1.5 text-[11px] text-[var(--k-danger)]">
                        {{ m.diagramError }}
                      </p>
                    </template>
                  </div>

                  <p v-if="m.error" class="mt-1.5 text-[11px] text-[var(--k-danger)]">{{ m.error }}</p>
                </template>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 全局错误 -->
      <p v-if="errorText" class="mx-4 mb-1 rounded-lg bg-[var(--k-warn-soft)] px-3 py-2 text-[11px] text-[var(--k-warn)]">
        {{ errorText }}
        <RouterLink v-if="isGuest && remaining <= 0" to="/me" class="ml-1 font-semibold underline">去登录</RouterLink>
      </p>

      <!-- 输入区 -->
      <div class="border-t border-[var(--k-border)] p-3">
        <div class="rounded-xl border border-[var(--k-border-strong)] bg-[var(--k-surface)] px-3 py-2 focus-within:border-[var(--k-accent)]">
          <textarea
            ref="inputEl"
            v-model="input"
            rows="1"
            :disabled="!configured || sending"
            placeholder="问我任何问题…"
            class="max-h-[160px] w-full resize-none bg-transparent text-sm leading-relaxed text-[var(--k-text)] outline-none placeholder:text-[var(--k-text-faint)] disabled:opacity-60"
            @keydown="onKeydown"
          />
          <div class="mt-1.5 flex items-center justify-between gap-2">
            <p class="truncate text-[10px] text-[var(--k-text-faint)]">
              Enter 发送 · Shift + Enter 换行 · 内容仅供参考
            </p>
            <button
              type="button"
              class="grid h-8 w-8 shrink-0 place-items-center rounded-lg transition-colors"
              :class="
                canSend
                  ? 'bg-[var(--k-accent)] text-[var(--k-on-accent)] hover:bg-[var(--k-accent-hover)]'
                  : 'bg-[var(--k-track)] text-[var(--k-text-faint)]'
              "
              :disabled="!canSend"
              aria-label="发送"
              @click="send()"
            >
              <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h13M12 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style>
/* 思考中的三个点 */
.ai-dot {
  display: inline-block;
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.4;
  animation: ai-blink 1.2s infinite ease-in-out;
}

@keyframes ai-blink {
  0%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

/* 流式输出时的光标 */
.ai-caret {
  display: inline-block;
  width: 6px;
  height: 14px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--k-accent);
  border-radius: 1px;
  animation: ai-caret-blink 1s steps(2, start) infinite;
}

@keyframes ai-caret-blink {
  0%,
  50% {
    opacity: 1;
  }
  50.01%,
  100% {
    opacity: 0;
  }
}
</style>