<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api } from '@/api/client'
import { useUserStore } from '@/store/user'

/**
 * 背诵卡管理抽屉。
 *
 * 为什么是抽屉而不是页面里的一块面板：背诵页的核心是「一张卡 + 两个标记按钮」，
 * 管理是旁路操作。摊在页面上会把头图撑高、打断纵向节奏（用户反馈过），
 * 放页尾又要滚到底才能找到。抽屉只在需要时占用视线，关掉就完全消失。
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const userStore = useUserStore()

const SUBJECTS = [
  { code: '', label: '全部' },
  { code: 'ds', label: '数据结构' },
  { code: 'os', label: '操作系统' },
  { code: 'co', label: '组成原理' },
  { code: 'cn', label: '计算机网络' },
]

type Card = {
  cardId: number
  blockId: string
  box: number
  reviewCount: number
  correctCount: number
  nextReviewDate: string | null
  subject: string
  pointTitle: string
  subpointTitle: string | null
  type: string
}

const subjects = SUBJECTS
const subject = ref('')
const cards = ref<Card[]>([])
const selected = ref<Set<number>>(new Set())
const loading = ref(false)
const busy = ref(false)
const message = ref<string | null>(null)

const count = computed(() => cards.value.length)
const allSelected = computed(() => count.value > 0 && selected.value.size === count.value)

function fmtDate(value: string | null): string {
  if (!value) return '—'
  return value.slice(5).replace('-', '/')
}

async function load() {
  if (!userStore.user) return
  loading.value = true
  message.value = null
  try {
    const result = await api.memoryCards(subject.value || undefined)
    cards.value = result.items
    selected.value = new Set()
  } catch (e) {
    message.value = e instanceof Error ? e.message : '加载失败'
    cards.value = []
  } finally {
    loading.value = false
  }
}

function toggleOne(cardId: number) {
  const next = new Set(selected.value)
  if (next.has(cardId)) next.delete(cardId)
  else next.add(cardId)
  selected.value = next
}

function toggleAll() {
  selected.value = allSelected.value ? new Set() : new Set(cards.value.map((c) => c.cardId))
}

async function removeSelected() {
  if (!selected.value.size || busy.value) return
  if (!window.confirm(`移出选中的 ${selected.value.size} 张卡片？\n\n已产生的复习记录不会删除。`)) return
  busy.value = true
  try {
    const result = await api.memoryRemoveBatch({ cardIds: [...selected.value] })
    message.value = `已移出 ${result.deleted} 张卡片`
    await load()
    emit('close')
  } catch (e) {
    message.value = e instanceof Error ? e.message : '移出失败'
  } finally {
    busy.value = false
  }
}

async function clearSubject() {
  const label = subjects.find((s) => s.code === subject.value)?.label ?? '当前科目'
  if (busy.value) return
  if (!window.confirm(`清空「${label}」的全部背诵卡？\n\n已产生的复习记录不会删除，之后可以重新加入。`)) return
  busy.value = true
  try {
    const result = await api.memoryRemoveBatch({ subject: subject.value })
    message.value = `已清空 ${result.deleted} 张卡片`
    await load()
  } catch (e) {
    message.value = e instanceof Error ? e.message : '清空失败'
  } finally {
    busy.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) emit('close')
}

watch(
  () => props.open,
  (open) => {
    if (open) void load()
  },
)

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-40">
      <!-- 遮罩：点一下关掉 -->
      <div class="absolute inset-0 bg-black/40" @click="emit('close')" />

      <aside
        class="absolute right-0 top-0 flex h-full w-full max-w-[420px] flex-col border-l border-[var(--k-border)] bg-[var(--k-surface)] shadow-2xl"
        role="dialog"
        aria-label="管理背诵卡"
      >
        <header class="flex shrink-0 items-center justify-between gap-2 border-b border-[var(--k-border)] px-4 py-3">
          <div>
            <p class="text-sm font-bold text-[var(--k-heading)]">管理卡片</p>
            <p class="text-[11px] text-[var(--k-text-faint)]">移出队列不会删除复习记录</p>
          </div>
          <button
            type="button"
            class="rounded-lg px-2 py-1 text-xs text-[var(--k-text-faint)] transition hover:bg-[var(--k-surface-soft)] hover:text-[var(--k-text)]"
            @click="emit('close')"
          >
            关闭 ✕
          </button>
        </header>

        <div class="flex shrink-0 flex-wrap gap-1.5 border-b border-[var(--k-border)] px-4 py-2.5">
          <button
            v-for="item in subjects"
            :key="item.code"
            type="button"
            class="rounded-lg border px-2.5 py-1 text-[11px] transition"
            :class="
              subject === item.code
                ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]'
                : 'border-[var(--k-border)] text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
            "
            @click="subject = item.code; load()"
          >
            {{ item.label }}
          </button>
          <span class="ml-auto self-center text-[11px] text-[var(--k-text-faint)]">共 {{ count }} 张</span>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto px-4 py-3">
          <p v-if="message" class="mb-2 rounded-lg bg-[var(--k-surface-soft)] px-3 py-2 text-[11px] text-[var(--k-text-muted)]">
            {{ message }}
          </p>
          <p v-if="loading" class="py-8 text-center text-xs text-[var(--k-text-faint)]">加载中…</p>
          <p v-else-if="!cards.length" class="py-8 text-center text-xs text-[var(--k-text-faint)]">
            这个范围里还没有卡片
          </p>

          <div v-else class="divide-y divide-[var(--k-border)]">
            <label
              v-for="card in cards"
              :key="card.cardId"
              class="flex cursor-pointer items-start gap-2 py-2 text-xs"
            >
              <input
                type="checkbox"
                class="mt-0.5 h-3.5 w-3.5 shrink-0 accent-[var(--k-accent)]"
                :checked="selected.has(card.cardId)"
                @change="toggleOne(card.cardId)"
              />
              <span class="min-w-0 flex-1">
                <span class="block truncate font-semibold text-[var(--k-text)]">
                  {{ card.subpointTitle || card.pointTitle }}
                </span>
                <span class="block truncate text-[11px] text-[var(--k-text-faint)]">
                  {{ card.pointTitle }} · 复习 {{ card.reviewCount }} 次 · 到期 {{ fmtDate(card.nextReviewDate) }}
                </span>
              </span>
            </label>
          </div>
        </div>

        <footer class="flex shrink-0 flex-wrap items-center gap-2 border-t border-[var(--k-border)] px-4 py-3">
          <button
            type="button"
            class="rounded-lg border border-[var(--k-border)] px-2.5 py-1.5 text-[11px] text-[var(--k-text-muted)]"
            @click="toggleAll"
          >
            {{ allSelected ? '取消全选' : '全选' }}
          </button>
          <button
            type="button"
            class="rounded-lg border border-[var(--k-danger)]/40 px-2.5 py-1.5 text-[11px] font-semibold text-[var(--k-danger)] disabled:opacity-40"
            :disabled="!selected.size || busy"
            @click="removeSelected"
          >
            移出选中（{{ selected.size }}）
          </button>
          <button
            v-if="subject"
            type="button"
            class="ml-auto rounded-lg border border-[var(--k-border)] px-2.5 py-1.5 text-[11px] text-[var(--k-danger)] disabled:opacity-40"
            :disabled="busy"
            @click="clearSubject"
          >
            清空该科目
          </button>
        </footer>
      </aside>
    </div>
  </Teleport>
</template>
