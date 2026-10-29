<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'
import { api } from '@/api/client'
import type { Dashboard, MemorySummary, WrongBook } from '@/api/types'
import { subjectMeta } from '@/constants/subjects'
import BrandLogo from '@/components/brand/BrandLogo.vue'

const userStore = useUserStore()
const { user, loading } = storeToRefs(userStore)

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const email = ref('')
const password = ref('')
const error = ref<string | null>(null)

/** 注册用的邮箱验证码 */
const code = ref('')
const sendingCode = ref(false)
const countdown = ref(0)
const codeNotice = ref<string | null>(null)
let codeTimer: number | null = null

onBeforeUnmount(() => {
  if (codeTimer != null) window.clearInterval(codeTimer)
})

/* ---------- 个人数据 ---------- */
const dashboard = ref<Dashboard | null>(null)
const memory = ref<MemorySummary | null>(null)
const wrongBook = ref<WrongBook | null>(null)
const dataCounts = ref<{
  attempts: number
  blockProgress: number
  dailyStats: number
  memoryCards: number
} | null>(null)
const busy = ref(false)
/** 操作结果提示 */
const notice = ref<string | null>(null)
/** 需要二次确认的危险操作：'attempts' | 'memory' | 'all' | null */
const confirming = ref<'attempts' | 'memory' | 'all' | null>(null)
/** 二次确认输入的用户名 */
const confirmText = ref('')

const subjects = computed(() => dashboard.value?.subjects ?? [])

/** 近 30 天做题量，用于足迹条形图 */
const recent30 = computed(() => {
  const list = dashboard.value?.recent ?? []
  return list.slice(-30)
})
const recent30Max = computed(() => Math.max(1, ...recent30.value.map((d) => d.questions)))
const recent30Total = computed(() => recent30.value.reduce((sum, d) => sum + d.questions, 0))
const recent30Active = computed(() => recent30.value.filter((d) => d.questions > 0).length)

/**
 * 里程碑。
 * 只放能从现有数据严格判断的项，不做「假成就」——
 * 比如「刷完 100 题」用 doneQuestions 判定，「首次全对一套卷」需要按卷统计，
 * 当前接口不提供，就不写进来。
 */
const milestones = computed(() => {
  const done = dashboard.value?.doneQuestions ?? 0
  const streak = dashboard.value?.streakDays ?? 0
  const mastered = wrongBook.value?.mastered ?? 0
  const cards = memory.value?.total ?? 0
  const solid = memory.value?.solid ?? 0
  return [
    { label: '开始第一道题', done: done >= 1, progress: `${Math.min(done, 1)}/1` },
    { label: '刷完 50 道题', done: done >= 50, progress: `${Math.min(done, 50)}/50` },
    { label: '刷完 200 道题', done: done >= 200, progress: `${Math.min(done, 200)}/200` },
    { label: '连续打卡 7 天', done: streak >= 7, progress: `${Math.min(streak, 7)}/7` },
    { label: '订正 10 道错题', done: mastered >= 10, progress: `${Math.min(mastered, 10)}/10` },
    { label: '加入 50 张背诵卡', done: cards >= 50, progress: `${Math.min(cards, 50)}/50` },
    { label: '10 张进入长期记忆', done: solid >= 10, progress: `${Math.min(solid, 10)}/10` },
  ]
})
const milestoneDone = computed(() => milestones.value.filter((m) => m.done).length)

/** 数据是否为空（用于禁用清空按钮） */
const hasAttemptsData = computed(() => (dataCounts.value?.attempts ?? 0) > 0)
const hasMemoryData = computed(() => (dataCounts.value?.memoryCards ?? 0) > 0)

async function loadPersonal() {
  try {
    dashboard.value = await api.dashboard()
  } catch {
    dashboard.value = null
  }
  try {
    memory.value = await api.memorySummary()
  } catch {
    memory.value = null
  }
  try {
    wrongBook.value = await api.wrongBook()
  } catch {
    wrongBook.value = null
  }
  try {
    dataCounts.value = await api.userDataCounts()
  } catch {
    dataCounts.value = null
  }
}

/** 导出为 JSON 文件下载。 */
async function exportData() {
  busy.value = true
  notice.value = null
  try {
    const data = await api.userDataExport()
    const blob = new Blob([JSON.stringify(data, null, 2)], {
      type: 'application/json;charset=utf-8',
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    const stamp = new Date().toLocaleDateString('sv-SE')
    link.href = url
    link.download = `kaoyan408-${user.value?.username ?? 'user'}-${stamp}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    notice.value = '已导出为 JSON 文件。'
  } catch (e) {
    notice.value = e instanceof Error ? e.message : '导出失败'
  } finally {
    busy.value = false
  }
}

/** 请求清空：进入二次确认态，而不是直接执行。 */
function askClear(scope: 'attempts' | 'memory' | 'all') {
  confirming.value = scope
  confirmText.value = ''
  notice.value = null
}

function cancelClear() {
  confirming.value = null
  confirmText.value = ''
}

/** 二次确认：必须原样输入用户名，避免误点。 */
const canConfirm = computed(
  () => confirmText.value.trim() === (user.value?.username ?? '__none__'),
)

async function doClear() {
  if (!confirming.value || !canConfirm.value || busy.value) return
  busy.value = true
  try {
    const removed = await api.userDataClear(confirming.value)
    const total = Object.values(removed).reduce((sum, n) => sum + n, 0)
    notice.value = `已删除 ${total} 条记录。`
    cancelClear()
    await loadPersonal()
    await userStore.loadDashboard()
  } catch (e) {
    notice.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    busy.value = false
  }
}

const scopeLabel: Record<string, string> = {
  attempts: '做题记录',
  memory: '背诵进度',
  all: '全部个人数据',
}

async function submit() {
  error.value = null
  if (!username.value || !password.value) {
    error.value = '请填写用户名与密码。'
    return
  }
  if (mode.value === 'register') {
    if (!email.value.trim()) {
      error.value = '注册需要填写邮箱。'
      return
    }
    if (!code.value.trim()) {
      error.value = '请填写邮箱验证码。'
      return
    }
  }
  try {
    if (mode.value === 'login') {
      await userStore.login(username.value, password.value)
    } else {
      await userStore.register(username.value, email.value, password.value, code.value)
    }
    password.value = ''
    code.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  }
}

/** 发送验证码并开始倒计时 */
async function sendCode() {
  error.value = null
  codeNotice.value = null
  const mail = email.value.trim()
  if (!mail) {
    error.value = '请先填写邮箱。'
    return
  }
  sendingCode.value = true
  try {
    const res = await api.sendMailCode(mail)
    codeNotice.value = res.message
    countdown.value = res.cooldownSeconds
    if (codeTimer != null) window.clearInterval(codeTimer)
    codeTimer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && codeTimer != null) {
        window.clearInterval(codeTimer)
        codeTimer = null
      }
    }, 1000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '发送失败'
  } finally {
    sendingCode.value = false
  }
}

onMounted(() => {
  if (user.value) void loadPersonal()
})

// 登录 / 退出后重新加载（或清空）个人数据
watch(user, (next) => {
  if (next) {
    void loadPersonal()
  } else {
    dashboard.value = null
    memory.value = null
    wrongBook.value = null
    dataCounts.value = null
    notice.value = null
    cancelClear()
  }
})
</script>

<template>
  <div class="mx-auto max-w-2xl space-y-5">
    <header class="space-y-2 text-center">
      <!-- 未登录时展示品牌与标语：这是用户第一次认真看这个平台，值得交代清楚它是谁 -->
      <BrandLogo v-if="!user" :height="52" tagline class="mx-auto" />
      <h1 v-else class="text-2xl font-bold tracking-tight text-[var(--k-text)]">我的</h1>
      <p class="text-sm text-[var(--k-text-muted)]">
        {{ user ? '学习记录、掌握度与打卡都保存在这个账号下。' : '登录后做题记录、掌握度与打卡才会保存。' }}
      </p>
    </header>

    <!-- 已登录：账号卡 + 个人数据 -->
    <template v-if="user">
      <!-- 账号 -->
      <section
        class="flex items-center gap-4 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5"
      >
        <span
          class="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-[var(--k-accent)] text-lg font-bold text-[var(--k-on-accent)]"
          >{{ user.username.slice(0, 1).toUpperCase() }}</span
        >
        <div class="min-w-0 flex-1">
          <p class="text-lg font-semibold text-[var(--k-text)]">{{ user.username }}</p>
          <p class="truncate text-xs text-[var(--k-text-faint)]">
            {{ user.email || '未绑定邮箱' }}
          </p>
        </div>
        <button
          class="shrink-0 rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-danger)] transition hover:bg-[var(--k-warn-soft)]"
          @click="userStore.logout()"
        >
          退出登录
        </button>
      </section>

      <!-- A. 学习概况 -->
      <section v-if="dashboard" class="space-y-3">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">学习概况</h2>
        <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4">
            <p class="text-2xl font-bold tabular-nums text-[var(--k-text)]">
              {{ dashboard.doneQuestions }}
            </p>
            <p class="mt-1 text-[11px] text-[var(--k-text-faint)]">已刷题数</p>
          </div>
          <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4">
            <p class="text-2xl font-bold tabular-nums text-[var(--k-text)]">
              {{ dashboard.accuracy }}%
            </p>
            <p class="mt-1 text-[11px] text-[var(--k-text-faint)]">正确率</p>
          </div>
          <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4">
            <p class="text-2xl font-bold tabular-nums text-[var(--k-text)]">
              {{ dashboard.masteryRate }}%
            </p>
            <p class="mt-1 text-[11px] text-[var(--k-text-faint)]">掌握率</p>
          </div>
          <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4">
            <p class="text-2xl font-bold tabular-nums text-[var(--k-text)]">
              {{ dashboard.streakDays }}
            </p>
            <p class="mt-1 text-[11px] text-[var(--k-text-faint)]">连续打卡</p>
          </div>
        </div>

        <!-- 四科进度 -->
        <div class="space-y-3 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
          <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">四科进度</p>
          <div v-for="item in subjects" :key="item.subject" class="flex items-center gap-3">
            <span
              class="h-2 w-2 shrink-0 rounded-full"
              :class="subjectMeta(item.subject).bar ?? 'bg-[var(--k-accent)]'"
            />
            <span class="w-28 shrink-0 text-xs text-[var(--k-text-muted)]">
              {{ subjectMeta(item.subject).name ?? item.subject }}
            </span>
            <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-[var(--k-track)]">
              <div
                class="h-full rounded-full transition-[width] duration-300"
                :class="subjectMeta(item.subject).bar ?? 'bg-[var(--k-accent)]'"
                :style="{ width: `${item.total === 0 ? 0 : (item.done / item.total) * 100}%` }"
              />
            </div>
            <span class="shrink-0 text-[11px] tabular-nums text-[var(--k-text-faint)]">
              {{ item.done }}/{{ item.total }}
            </span>
          </div>
        </div>

        <!-- 快捷入口 -->
        <div class="grid gap-3 sm:grid-cols-3">
          <RouterLink
            to="/practice?scope=wrong-pending"
            class="flex items-center justify-between rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[#b23c3c]"
          >
            <span class="text-xs font-semibold text-[var(--k-text)]">错题本</span>
            <span
              class="text-sm font-bold tabular-nums"
              :class="(wrongBook?.pending ?? 0) > 0 ? 'text-[#b23c3c]' : 'text-[var(--k-text-faint)]'"
            >
              {{ wrongBook?.pending ?? 0 }}
            </span>
          </RouterLink>
          <RouterLink
            to="/memory"
            class="flex items-center justify-between rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
          >
            <span class="text-xs font-semibold text-[var(--k-text)]">背诵到期</span>
            <span
              class="text-sm font-bold tabular-nums"
              :class="(memory?.due ?? 0) > 0 ? 'text-[var(--k-accent-text)]' : 'text-[var(--k-text-faint)]'"
            >
              {{ memory?.due ?? 0 }}
            </span>
          </RouterLink>
          <RouterLink
            to="/stats"
            class="flex items-center justify-between rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
          >
            <span class="text-xs font-semibold text-[var(--k-text)]">学习看板</span>
            <span class="text-sm text-[var(--k-text-faint)]">→</span>
          </RouterLink>
        </div>
      </section>

      <!-- D. 学习足迹 -->
      <section v-if="dashboard" class="space-y-3">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">学习足迹</h2>

        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">最近 30 天</p>
            <span class="text-[11px] text-[var(--k-text-faint)]">
              共 <strong class="text-[var(--k-text-muted)]">{{ recent30Total }}</strong> 题 ·
              <strong class="text-[var(--k-text-muted)]">{{ recent30Active }}</strong> 天在复习
            </span>
          </div>
          <div class="mt-3 flex h-16 items-end gap-[3px]">
            <div
              v-for="day in recent30"
              :key="day.date"
              class="flex-1 rounded-t-[2px] transition"
              :class="day.questions > 0 ? 'bg-[var(--k-accent)]/70' : 'bg-[var(--k-chip)]'"
              :style="{
                height:
                  day.questions > 0
                    ? Math.max(4, (day.questions / recent30Max) * 60) + 'px'
                    : '2px',
              }"
              :title="`${day.date}：${day.questions} 题`"
            />
          </div>
        </div>

        <!-- 里程碑 -->
        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
          <div class="flex items-baseline justify-between">
            <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">里程碑</p>
            <span class="text-[11px] tabular-nums text-[var(--k-text-faint)]">
              {{ milestoneDone }} / {{ milestones.length }}
            </span>
          </div>
          <ul class="mt-3 space-y-2">
            <li
              v-for="item in milestones"
              :key="item.label"
              class="flex items-center gap-2.5 text-xs"
            >
              <!--
                未达成的用空圆点而不是「隐藏的对勾」：
                原先写死一个 ✓ 再用 text-transparent 藏起来，
                视觉上看不见，但读屏软件仍会念出「✓」，造成误读。
              -->
              <span
                v-if="item.done"
                class="grid h-4 w-4 shrink-0 place-items-center rounded-full bg-[#0f7a4d] text-[9px] text-white dark:bg-[#4ade80] dark:text-[#0b1a12]"
                aria-label="已达成"
              >
                ✓
              </span>
              <span
                v-else
                class="h-4 w-4 shrink-0 rounded-full border border-[var(--k-border)]"
                aria-label="未达成"
              />
              <span :class="item.done ? 'text-[var(--k-text)]' : 'text-[var(--k-text-faint)]'">
                {{ item.label }}
              </span>
              <span class="ml-auto tabular-nums text-[11px] text-[var(--k-text-faint)]">
                {{ item.progress }}
              </span>
            </li>
          </ul>
        </div>
      </section>

      <!-- C. 数据管理 -->
      <section v-if="dataCounts" class="space-y-3">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">数据管理</h2>

        <p
          v-if="notice"
          class="rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-4 py-2.5 text-xs text-[var(--k-text-muted)]"
        >
          {{ notice }}
        </p>

        <div class="space-y-3 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p class="text-xs font-semibold text-[var(--k-text)]">导出个人数据</p>
              <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">
                作答 {{ dataCounts.attempts }} 条 · 掌握度 {{ dataCounts.blockProgress }} 条 ·
                每日统计 {{ dataCounts.dailyStats }} 条 · 背诵卡 {{ dataCounts.memoryCards }} 张
              </p>
            </div>
            <button
              class="shrink-0 rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)] disabled:opacity-40"
              :disabled="busy"
              @click="exportData"
            >
              导出 JSON
            </button>
          </div>

          <div class="border-t border-[var(--k-border)] pt-3">
            <p class="text-xs font-semibold text-[var(--k-danger)]">清空数据</p>
            <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">
              删除后无法恢复。题库内容不受影响，只清除你自己的记录。
            </p>

            <div class="mt-2.5 flex flex-wrap gap-2">
              <button
                class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:border-[#b23c3c] hover:text-[#b23c3c] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-[var(--k-border)] disabled:hover:text-[var(--k-text-muted)]"
                :disabled="busy || !hasAttemptsData"
                @click="askClear('attempts')"
              >
                清空做题记录
              </button>
              <button
                class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:border-[#b23c3c] hover:text-[#b23c3c] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-[var(--k-border)] disabled:hover:text-[var(--k-text-muted)]"
                :disabled="busy || !hasMemoryData"
                @click="askClear('memory')"
              >
                清空背诵进度
              </button>
              <button
                class="rounded-lg border border-[#b23c3c]/40 px-3 py-1.5 text-xs font-semibold text-[#b23c3c] transition hover:bg-[#b23c3c]/10 disabled:cursor-not-allowed disabled:opacity-40 dark:text-[#f87171]"
                :disabled="busy || (!hasAttemptsData && !hasMemoryData)"
                @click="askClear('all')"
              >
                清空全部
              </button>
            </div>
          </div>
        </div>

        <!-- 二次确认：必须原样输入用户名 -->
        <div
          v-if="confirming"
          class="space-y-3 rounded-xl border border-[#b23c3c]/40 bg-[var(--k-warn-soft)] p-4"
        >
          <p class="text-xs font-semibold text-[var(--k-danger)]">
            确认删除「{{ scopeLabel[confirming] }}」？
          </p>
          <p class="text-[11px] leading-relaxed text-[var(--k-text-muted)]">
            此操作不可恢复。请输入你的用户名
            <strong class="text-[var(--k-text)]">{{ user.username }}</strong> 以确认。
          </p>
          <div class="flex flex-wrap items-center gap-2">
            <input
              v-model="confirmText"
              class="min-w-[180px] flex-1 rounded-lg border border-[var(--k-border)] bg-[var(--k-surface)] px-3 py-1.5 text-xs outline-none focus:border-[var(--k-danger)]"
              :placeholder="user.username"
              @keyup.enter="doClear"
            />
            <button
              class="rounded-lg bg-[#b23c3c] px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-[#9c3232] disabled:opacity-40"
              :disabled="!canConfirm || busy"
              @click="doClear"
            >
              确认删除
            </button>
            <button
              class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface)]"
              @click="cancelClear"
            >
              取消
            </button>
          </div>
        </div>
      </section>
    </template>

    <!-- 未登录：登录 / 注册 -->
    <section v-else class="space-y-4 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-6">
      <div class="flex gap-2 rounded-lg bg-[var(--k-chip)] p-1 text-sm">
        <button
          class="flex-1 rounded-md py-1.5 transition"
          :class="mode === 'login' ? 'bg-[var(--k-surface)] font-semibold text-[var(--k-accent-text)] shadow-sm' : 'text-[var(--k-text-muted)]'"
          @click="mode = 'login'"
        >
          登录
        </button>
        <button
          class="flex-1 rounded-md py-1.5 transition"
          :class="mode === 'register' ? 'bg-[var(--k-surface)] font-semibold text-[var(--k-accent-text)] shadow-sm' : 'text-[var(--k-text-muted)]'"
          @click="mode = 'register'"
        >
          注册
        </button>
      </div>

      <label class="block text-sm">
        <span class="text-[var(--k-text-muted)]">用户名</span>
        <input
          v-model="username"
          class="mt-1 w-full rounded-lg border border-[var(--k-border)] px-3 py-2 outline-none focus:border-[var(--k-accent)]"
          placeholder="2-50 位，可用中文"
        />
      </label>

      <label v-if="mode === 'register'" class="block text-sm">
        <span class="text-[var(--k-text-muted)]">邮箱</span>
        <input
          v-model="email"
          type="email"
          class="mt-1 w-full rounded-lg border border-[var(--k-border)] px-3 py-2 outline-none focus:border-[var(--k-accent)]"
          placeholder="you@example.com"
        />
      </label>

      <label class="block text-sm">
        <span class="text-[var(--k-text-muted)]">密码</span>
        <input
          v-model="password"
          type="password"
          class="mt-1 w-full rounded-lg border border-[var(--k-border)] px-3 py-2 outline-none focus:border-[var(--k-accent)]"
          :placeholder="mode === 'register' ? '至少 8 位' : '请输入密码'"
          @keyup.enter="submit"
        />
      </label>

      <!-- 注册需要邮箱验证码：6 位数字，5 分钟内有效，一次性使用 -->
      <div v-if="mode === 'register'" class="block text-sm">
        <span class="text-[var(--k-text-muted)]">邮箱验证码</span>
        <div class="mt-1 flex gap-2">
          <input
            v-model="code"
            inputmode="numeric"
            maxlength="6"
            class="min-w-0 flex-1 rounded-lg border border-[var(--k-border)] px-3 py-2 tracking-[0.3em] outline-none focus:border-[var(--k-accent)]"
            placeholder="6 位数字"
            @keyup.enter="submit"
          />
          <button
            type="button"
            class="shrink-0 rounded-lg border border-[var(--k-border)] px-3 py-2 text-xs text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)] disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="sendingCode || countdown > 0"
            @click="sendCode"
          >
            {{ countdown > 0 ? `${countdown}s 后重发` : sendingCode ? '发送中…' : '获取验证码' }}
          </button>
        </div>
        <p v-if="codeNotice" class="mt-1.5 text-[11px] leading-relaxed text-[var(--k-text-faint)]">
          {{ codeNotice }}
        </p>
      </div>

      <p v-if="error" class="rounded-lg border border-[var(--k-danger)]/30 bg-[var(--k-warn-soft)] px-3 py-2 text-sm text-[var(--k-danger)]">
        {{ error }}
      </p>

      <button
        class="w-full rounded-lg bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-[var(--k-on-accent)] transition hover:bg-[var(--k-accent-hover)] disabled:opacity-60"
        :disabled="loading"
        @click="submit"
      >
        {{ loading ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>

      <p class="text-center text-xs leading-relaxed text-[var(--k-text-faint)]">
        注册需邮箱验证。当前为演示模式时，验证码打印在服务端日志。
      </p>
    </section>
  </div>
</template>
