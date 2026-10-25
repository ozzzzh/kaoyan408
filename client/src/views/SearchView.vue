<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { SearchResult } from '@/api/types'

const route = useRoute()
const router = useRouter()

const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const result = ref<SearchResult | null>(null)
const loading = ref(false)
const searched = ref(false)
/** 当前选中的分组，用于 tab 切换；'' 表示全部 */
const tab = ref<'' | 'point' | 'question' | 'block'>('')

const tabs = computed(() => [
  { key: '' as const, label: '全部', count: result.value?.total ?? 0 },
  { key: 'point' as const, label: '知识点', count: result.value?.points.length ?? 0 },
  { key: 'question' as const, label: '题目', count: result.value?.questions.length ?? 0 },
  { key: 'block' as const, label: '正文片段', count: result.value?.blocks.length ?? 0 },
])

const showPoints = computed(() => tab.value === '' || tab.value === 'point')
const showQuestions = computed(() => tab.value === '' || tab.value === 'question')
const showBlocks = computed(() => tab.value === '' || tab.value === 'block')

const subjectName = (code: string) =>
  ({ ds: '数据结构', os: '操作系统', co: '计算机组成原理', cn: '计算机网络' })[code] ?? code

/** 把关键词在文本中高亮：先转义 HTML，再包 mark，避免 XSS。 */
function highlight(text: string | null | undefined): string {
  if (!text) return ''
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
  const k = keyword.value.trim()
  if (!k) return escaped
  const pattern = new RegExp(`(${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return escaped.replace(pattern, '<mark class="k-hit">$1</mark>')
}

/**
 * 已经执行过搜索的关键词。
 *
 * 不能用 `q !== keyword.value` 做去重判断：输入框是 v-model 双向绑定，
 * 用户点「搜索」时 keyword 已经等于要搜的词，那个判断恒为 false，
 * 于是 runSearch 根本不会被调用——URL 变了、结果却没出来，只能刷新页面
 * （刷新走 onMounted 那条路径）才看到结果。这里单独记录「上次实际搜过什么」。
 */
const lastQuery = ref('')

async function runSearch(q: string) {
  const trimmed = q.trim()
  if (!trimmed) {
    result.value = null
    searched.value = false
    lastQuery.value = ''
    return
  }
  // 同一个词已经搜过就不重复请求（例如输入框失焦、路由回退等触发的重复调用）
  if (trimmed === lastQuery.value && result.value) {
    return
  }
  loading.value = true
  searched.value = true
  try {
    result.value = await api.search(trimmed)
    lastQuery.value = trimmed
  } catch {
    result.value = null
    lastQuery.value = ''
  } finally {
    loading.value = false
  }
}

/** 提交：立刻发起搜索，不等 watch 回调（避免依赖路由变化的时序）。 */
function submit() {
  const q = keyword.value.trim()
  if (!q) return
  // 关键词没变时也要重新搜——用户可能就是想「再搜一次」
  if (q === lastQuery.value) {
    void runSearch(q)
    return
  }
  router.replace({ path: '/search', query: { q } })
  void runSearch(q)
}

/** 示例词，空状态时给用户一个起点。 */
const examples = ['KMP', '银行家算法', 'CRC', '子网掩码', '2026 3', '死锁']

/** 点示例词：填入并直接搜索，不经过路由（少一次时序依赖）。 */
function pickExample(word: string) {
  keyword.value = word
  submit()
}

onMounted(() => runSearch(keyword.value))

watch(
  () => route.query.q,
  (next) => {
    const q = typeof next === 'string' ? next : ''
    // 只同步输入框；搜索由 submit() 或 onMounted 负责。
    // 这里再调一次 runSearch 会在「点搜索」时产生重复请求。
    keyword.value = q
    if (q && q !== lastQuery.value) {
      void runSearch(q)
    }
  },
)
</script>

<template>
  <div class="space-y-5">
    <header class="space-y-3">
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">搜索</h1>

      <div
        class="flex items-center gap-2 rounded-xl border border-[var(--k-border-strong)] bg-[var(--k-surface)] px-4 py-2.5"
      >
        <svg
          class="h-4 w-4 shrink-0 text-[var(--k-text-faint)]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        </svg>
        <input
          v-model="keyword"
          class="w-full border-0 bg-transparent text-[15px] text-[var(--k-text)] outline-0 placeholder:text-[var(--k-text-faint)]"
          placeholder="搜知识点、题目、正文；也可直接输「2026 3」定位题号"
          @keydown.enter="submit"
        />
        <button
          class="shrink-0 rounded-lg bg-[var(--k-accent)] px-4 py-1.5 text-sm font-semibold text-white transition hover:bg-[var(--k-accent-hover)]"
          @click="submit"
        >
          搜索
        </button>
      </div>

      <!-- 示例 -->
      <div v-if="!searched" class="flex flex-wrap items-center gap-2 text-xs">
        <span class="text-[var(--k-text-faint)]">试试：</span>
        <button
          v-for="word in examples"
          :key="word"
          class="rounded-full border border-[var(--k-border)] px-3 py-1 text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
          @click="pickExample(word)"
        >
          {{ word }}
        </button>
      </div>
    </header>

    <p v-if="loading" class="py-10 text-center text-sm text-[var(--k-text-faint)]">搜索中…</p>

    <template v-else-if="result">
      <!-- 结果统计 + 分组切换 -->
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-[var(--k-text-muted)]">
          「<span class="font-semibold text-[var(--k-text)]">{{ result.query }}</span
          >」共 {{ result.total }} 条结果
        </p>
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="item in tabs"
            :key="item.key"
            class="rounded-lg px-3 py-1 text-xs transition"
            :class="
              tab === item.key
                ? 'bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]'
                : 'text-[var(--k-text-faint)] hover:bg-[var(--k-surface-soft)]'
            "
            @click="tab = item.key"
          >
            {{ item.label }} {{ item.count }}
          </button>
        </div>
      </div>

      <p
        v-if="result.total === 0"
        class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] px-6 py-12 text-center text-sm text-[var(--k-text-muted)]"
      >
        没有找到匹配的内容。试试更短的关键词，或用「2026 3」这类题号查询。
      </p>

      <div v-else class="space-y-6">
        <!-- 知识点 -->
        <section v-if="showPoints && result.points.length">
          <h2 class="mb-2 text-xs font-bold text-[var(--k-text-faint)]">
            知识点 · {{ result.points.length }}
          </h2>
          <div class="space-y-2">
            <RouterLink
              v-for="hit in result.points"
              :key="hit.pointId"
              :to="`/learn/${hit.pointId}`"
              class="block rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
            >
              <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span
                  class="text-sm font-semibold text-[var(--k-text)]"
                  v-html="highlight(hit.title)"
                ></span>
                <span class="text-[11px] text-[var(--k-text-faint)]">
                  {{ subjectName(hit.subject) }} · {{ hit.blockCount }} 块
                </span>
                <span
                  v-if="hit.bodyHits > 0"
                  class="rounded bg-[var(--k-chip)] px-1.5 py-0.5 text-[10px] text-[var(--k-text-faint)]"
                >
                  正文命中 {{ hit.bodyHits }} 处
                </span>
              </div>
            </RouterLink>
          </div>
        </section>

        <!-- 题目 -->
        <section v-if="showQuestions && result.questions.length">
          <h2 class="mb-2 text-xs font-bold text-[var(--k-text-faint)]">
            题目 · {{ result.questions.length }}
          </h2>
          <div class="space-y-2">
            <RouterLink
              v-for="hit in result.questions"
              :key="hit.examRef"
              :to="{ path: '/practice', query: { examRef: hit.examRef } }"
              class="block rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
            >
              <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span
                  class="text-sm font-semibold text-[var(--k-text)]"
                  v-html="highlight(hit.title)"
                ></span>
                <span class="text-[11px] text-[var(--k-text-faint)]">
                  {{ hit.hint }} · {{ hit.score }} 分
                </span>
              </div>
              <p
                v-if="hit.snippet"
                class="mt-1 text-xs leading-relaxed text-[var(--k-text-muted)]"
                v-html="highlight(hit.snippet)"
              ></p>
            </RouterLink>
          </div>
        </section>

        <!-- 正文片段 -->
        <section v-if="showBlocks && result.blocks.length">
          <h2 class="mb-2 text-xs font-bold text-[var(--k-text-faint)]">
            正文片段 · {{ result.blocks.length }}
          </h2>
          <div class="space-y-2">
            <RouterLink
              v-for="hit in result.blocks"
              :key="hit.blockId"
              :to="`/learn/${hit.pointId}`"
              class="block rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
            >
              <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span
                  class="text-[13px] font-semibold text-[var(--k-text)]"
                  v-html="highlight(hit.pointTitle)"
                ></span>
                <span v-if="hit.subpointTitle" class="text-[11px] text-[var(--k-text-faint)]">
                  {{ hit.subpointTitle }}
                </span>
              </div>
              <p
                class="mt-1 text-xs leading-relaxed text-[var(--k-text-muted)]"
                v-html="highlight(hit.snippet)"
              ></p>
            </RouterLink>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
:deep(.k-hit) {
  border-radius: 2px;
  background: color-mix(in srgb, var(--k-accent) 22%, transparent);
  color: var(--k-text);
  padding: 0 2px;
  font-weight: 600;
}
</style>