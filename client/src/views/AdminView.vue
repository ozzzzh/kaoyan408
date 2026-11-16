<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError } from '@/api/client'
import type { AdminOverview, AdminUser } from '@/api/types'
import { useUserStore } from '@/store/user'

/**
 * 管理页：站点概览 + 账号管理。
 *
 * 权限说明：本页只在 isAdmin 时可见（侧栏入口与路由守卫都判断了），
 * 但真正的门禁在服务端 —— /api/v1/admin/** 要求 ROLE_ADMIN，
 * 前端隐藏只是「别打扰普通用户」，不是安全边界。
 */
const userStore = useUserStore()

const overview = ref<AdminOverview | null>(null)
const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const loading = ref(false)
const busy = ref<number | null>(null)
const message = ref<string | null>(null)
const error = ref<string | null>(null)

const meId = computed(() => userStore.user?.id ?? null)
/** 只读的演示管理员：能看，不能改（服务端也会拒）。 */
const readOnly = computed(() => overview.value?.readOnly === true)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function loadOverview() {
  try {
    overview.value = await api.adminOverview()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载概览失败'
  }
}

async function loadUsers() {
  loading.value = true
  error.value = null
  try {
    const data = await api.adminUsers({ keyword: keyword.value.trim(), page: page.value, size })
    users.value = data.items
    total.value = data.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载账号列表失败'
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await Promise.all([loadOverview(), loadUsers()])
}

function note(text: string, isError = false) {
  if (isError) {
    error.value = text
    message.value = null
  } else {
    message.value = text
    error.value = null
  }
}

/** 所有写操作共用：同一时间只允许一个，避免连点造成重复提交。 */
async function act(id: number, fn: () => Promise<{ message: string; generatedPassword?: string }>) {
  busy.value = id
  error.value = null
  message.value = null
  try {
    const result = await fn()
    // 生成的临时密码只出现一次，必须让管理员看见
    note(result.generatedPassword ? `${result.message} 新密码：${result.generatedPassword}` : result.message)
    await refresh()
  } catch (e) {
    note(e instanceof ApiError ? e.message : '操作失败', true)
  } finally {
    busy.value = null
  }
}

function toggleActive(user: AdminUser) {
  void act(user.id, () => api.adminSetActive(user.id, !user.active))
}

function toggleAdmin(user: AdminUser) {
  void act(user.id, () => api.adminSetAdmin(user.id, !user.admin))
}

function resetPassword(user: AdminUser) {
  if (!window.confirm(`确认重置「${user.username}」的密码？该用户所有登录态会立即失效。`)) return
  void act(user.id, () => api.adminResetPassword(user.id))
}

function clearData(user: AdminUser) {
  if (!window.confirm(`确认清空「${user.username}」的练习数据？做题记录、掌握度、背诵卡都会删除，不可恢复。`)) return
  void act(user.id, () => api.adminClearData(user.id))
}

function search() {
  page.value = 1
  void loadUsers()
}

function go(delta: number) {
  const next = page.value + delta
  if (next < 1 || next > totalPages.value) return
  page.value = next
  void loadUsers()
}

function fmtDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

onMounted(refresh)
</script>

<template>
  <div class="mx-auto max-w-5xl space-y-5">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">管理</h1>
        <p class="text-sm text-[var(--k-text-muted)]">
          站点概览与账号管理。这里的操作会影响真实用户，请谨慎。
        </p>
      </div>
      <button
        type="button"
        class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs font-semibold text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)]"
        @click="refresh"
      >
        刷新
      </button>
    </header>

    <!-- 只读演示管理员提示：让访客知道「这是演示，按钮点不动是设计如此」 -->
    <div
      v-if="readOnly"
      class="rounded-lg border border-[#8a6d1f]/40 bg-[var(--k-warn-soft)] px-4 py-2.5 text-xs leading-6 text-[var(--k-warn)]"
    >
      你现在用的是<b>只读的演示管理员</b>账号：可以查看站点概览、浏览账号列表，
      但禁用/授权/重置密码/清空数据这些操作会被服务端拒绝。
      需要真正管理请用你自己的管理员账号登录。
    </div>

    <!-- 提示条 -->
    <div
      v-if="message || error"
      class="flex flex-wrap items-center justify-between gap-2 rounded-lg border px-4 py-2.5 text-sm"
      :class="
        error
          ? 'border-[#8a6d1f]/40 bg-[var(--k-warn-soft)] text-[var(--k-warn)]'
          : 'border-[var(--k-accent)]/40 bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]'
      "
    >
      <span class="break-all">{{ error ?? message }}</span>
      <button
        type="button"
        class="shrink-0 text-xs font-semibold underline"
        @click="message = null; error = null"
      >
        知道了
      </button>
    </div>

    <!-- 概览 -->
    <section v-if="overview" class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
      <div
        v-for="card in [
          { label: '账号', value: overview.users, hint: `启用 ${overview.activeUsers} · 管理员 ${overview.admins}` },
          { label: '近 7 天新增', value: overview.newUsers7d, hint: '注册量' },
          { label: '累计作答', value: overview.attempts, hint: `今日 ${overview.attemptsToday}` },
          { label: '背诵卡片', value: overview.memoryCards, hint: '用户卡片总数' },
          { label: '题库', value: overview.questions, hint: `${overview.knowledgePoints} 知识点 / ${overview.knowledgeBlocks} 知识块` },
          { label: '模拟考', value: overview.mockSessions, hint: '已开考场次' },
          { label: 'AI 对话', value: overview.aiConversations, hint: `${overview.aiMessages} 条消息` },
        ]"
        :key="card.label"
        class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3"
      >
        <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">{{ card.label }}</p>
        <p class="mt-1 text-xl font-bold tabular-nums text-[var(--k-text)]">{{ card.value }}</p>
        <p class="mt-0.5 truncate text-[11px] text-[var(--k-text-faint)]">{{ card.hint }}</p>
      </div>
    </section>

    <!-- 账号管理 -->
    <section class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)]">
      <div class="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--k-border)] px-4 py-3">
        <h2 class="text-sm font-bold text-[var(--k-text)]">账号（{{ total }}）</h2>
        <div class="flex items-center gap-2">
          <input
            v-model="keyword"
            class="w-44 rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-2.5 py-1.5 text-xs text-[var(--k-text)] outline-0 placeholder:text-[var(--k-text-faint)]"
            placeholder="用户名 / 邮箱"
            @keyup.enter="search"
          />
          <button
            type="button"
            class="rounded-lg bg-[var(--k-accent)] px-3 py-1.5 text-xs font-bold text-[var(--k-on-accent)]"
            @click="search"
          >
            搜索
          </button>
        </div>
      </div>

      <p v-if="loading" class="px-4 py-6 text-center text-sm text-[var(--k-text-faint)]">加载中…</p>
      <p v-else-if="!users.length" class="px-4 py-6 text-center text-sm text-[var(--k-text-faint)]">
        没有匹配的账号。
      </p>

      <ul v-else class="divide-y divide-[var(--k-border)]">
        <li v-for="item in users" :key="item.id" class="flex flex-wrap items-center gap-3 px-4 py-3">
          <div class="min-w-0 flex-1">
            <p class="flex items-center gap-2 text-sm font-semibold text-[var(--k-text)]">
              <span class="truncate">{{ item.username }}</span>
              <span
                v-if="item.admin"
                class="rounded bg-[var(--k-accent-soft)] px-1.5 py-0.5 text-[10px] font-bold text-[var(--k-accent-text)]"
              >管理员</span>
              <span
                v-if="!item.active"
                class="rounded bg-[var(--k-warn-soft)] px-1.5 py-0.5 text-[10px] font-bold text-[var(--k-warn)]"
              >已禁用</span>
              <span v-if="item.id === meId" class="text-[10px] text-[var(--k-text-faint)]">（你）</span>
            </p>
            <p class="mt-0.5 truncate text-[11px] text-[var(--k-text-faint)]">
              {{ item.email ?? '无邮箱' }} · 注册 {{ fmtDate(item.createdAt) }} · 作答 {{ item.attempts }} · 卡片 {{ item.cards }}
              <template v-if="item.lastActiveAt"> · 最近 {{ fmtDate(item.lastActiveAt) }}</template>
            </p>
          </div>

          <div class="flex shrink-0 flex-wrap items-center gap-1.5">
            <button
              type="button"
              class="rounded-lg border border-[var(--k-border)] px-2.5 py-1 text-[11px] font-semibold text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-50"
              :disabled="readOnly || busy === item.id || item.id === meId"
              @click="toggleActive(item)"
            >
              {{ item.active ? '禁用' : '启用' }}
            </button>
            <button
              type="button"
              class="rounded-lg border border-[var(--k-border)] px-2.5 py-1 text-[11px] font-semibold text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-50"
              :disabled="readOnly || busy === item.id || item.id === meId"
              @click="toggleAdmin(item)"
            >
              {{ item.admin ? '撤销管理员' : '设为管理员' }}
            </button>
            <button
              type="button"
              class="rounded-lg border border-[var(--k-border)] px-2.5 py-1 text-[11px] font-semibold text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-50"
              :disabled="readOnly || busy === item.id"
              @click="resetPassword(item)"
            >
              重置密码
            </button>
            <button
              type="button"
              class="rounded-lg border border-[#8a6d1f]/40 px-2.5 py-1 text-[11px] font-semibold text-[var(--k-warn)] transition hover:bg-[var(--k-warn-soft)] disabled:opacity-50"
              :disabled="readOnly || busy === item.id || item.id === meId"
              @click="clearData(item)"
            >
              清空数据
            </button>
          </div>
        </li>
      </ul>

      <div
        v-if="totalPages > 1"
        class="flex items-center justify-between border-t border-[var(--k-border)] px-4 py-2.5 text-xs text-[var(--k-text-faint)]"
      >
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <div class="flex gap-1.5">
          <button
            type="button"
            class="rounded-lg border border-[var(--k-border)] px-2.5 py-1 font-semibold disabled:opacity-40"
            :disabled="page <= 1"
            @click="go(-1)"
          >
            上一页
          </button>
          <button
            type="button"
            class="rounded-lg border border-[var(--k-border)] px-2.5 py-1 font-semibold disabled:opacity-40"
            :disabled="page >= totalPages"
            @click="go(1)"
          >
            下一页
          </button>
        </div>
      </div>
    </section>

    <p class="text-[11px] leading-5 text-[var(--k-text-faint)]">
      说明：「禁用」会让该用户立即掉线并无法再登录；「重置密码」会生成一次性新密码并让旧登录态失效；
      「清空数据」只删练习记录，不删账号本身。你不能禁用或降权自己，也不能撤销最后一个管理员。
    </p>
  </div>
</template>
