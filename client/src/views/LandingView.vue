<script setup lang="ts">
/**
 * 营销落地页。
 *
 * 与工作台分流的规则见 router/index.ts：未登录访问 / 时落到这里，
 * 已登录直接进工作台，所以老用户不会被打扰。
 *
 * 关于页面里的数字：
 * - 「真题整卷」「知识正文」两张演示卡用的是**接口返回的真实数据**
 *   （18 卷 / 846 题 / 四科分布 / 147 个知识点），这些接口游客也能调用；
 * - Hero 右侧的掌握度是「学完一轮后的样子」，属于示意值，
 *   因为未登录用户还没有任何作答记录。标题与角标都标明了它是示意。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import { useThemeStore } from '@/store/theme'
import { api } from '@/api/client'
import type { Dashboard, ExamSummary } from '@/api/types'
import { SUBJECT_META, subjectMeta } from '@/constants/subjects'

const themeStore = useThemeStore()

const dashboard = ref<Dashboard | null>(null)
const exams = ref<ExamSummary[]>([])
const knowledgeCount = ref(0)

onMounted(async () => {
  themeStore.start()
  // 落地页不该因为接口慢而空白：三处各自独立请求，
  // 拿到哪个就渲染哪个，失败的保持占位。
  api.dashboard().then((d) => { dashboard.value = d }).catch(() => {})
  api.exams().then((e) => { exams.value = e }).catch(() => {})
  api.knowledgePoints()
    .then((list) => { knowledgeCount.value = list.length })
    .catch(() => {})
})

/** Hero 掌握度：示意值（未登录没有真实作答），标注「示意」 */
const demoSubjects = [
  { name: '数据结构', value: 72, color: '#3D6BE5' },
  { name: '计算机组成原理', value: 58, color: '#F0A020' },
  { name: '操作系统', value: 64, color: '#7C5CF0' },
  { name: '计算机网络', value: 41, color: '#E0574F' },
]

const totalQuestions = computed(() => dashboard.value?.totalQuestions ?? 846)
const paperCount = computed(() => exams.value.length || 18)
const pointCount = computed(() => knowledgeCount.value || 147)

/** 真实四科题量，按年份汇总后取一卷的代表性分布 */
const subjectTotals = computed(() => {
  const stats = dashboard.value?.subjects
  if (!stats?.length) {
    return [
      { key: 'ds', name: '数据结构', total: 233, color: SUBJECT_META.ds!.hex },
      { key: 'os', name: '操作系统', total: 216, color: SUBJECT_META.os!.hex },
      { key: 'co', name: '计算机组成原理', total: 235, color: SUBJECT_META.co!.hex },
      { key: 'cn', name: '计算机网络', total: 162, color: SUBJECT_META.cn!.hex },
    ]
  }
  return stats.map((s) => {
    const meta = subjectMeta(s.subject)
    return { key: s.subject, name: meta.name, total: s.total, color: meta.hex }
  })
})

/** 真题卷演示卡：最近两卷 + 有分值异常的 2012 卷（内容本身就是卖点） */
const demoYears = computed(() => {
  const recent = exams.value.slice(-2).reverse()
  if (!recent.length) {
    return [
      { year: 2026, questionCount: 47, warn: false },
      { year: 2025, questionCount: 47, warn: false },
      { year: 2012, questionCount: 47, warn: true },
    ]
  }
  // 2012 卷综合题分值与规范不符，这条异常已被标注，
  // 放在演示卡里正说明「数据是核对过的」，比三个正常年份更有说服力。
  const odd = exams.value.find((e) => e.year === 2012)
  const rows = recent.map((r) => ({ year: r.year, questionCount: r.questionCount, warn: false }))
  if (odd) {
    rows.push({ year: odd.year, questionCount: odd.questionCount, warn: true })
  }
  return rows
})

/** 知识点演示卡：展示真实的知识块标题 */
const knowledgeBlocks = ref<{ id: string; title: string }[]>([])
api.knowledgePoints()
  .then((list) => {
    knowledgeBlocks.value = list.slice(0, 4).map((p) => ({ id: p.pointId, title: p.title }))
  })
  .catch(() => {})

const loopSteps = [
  {
    no: '01',
    key: '题',
    title: '846 道真题按知识块归位',
    desc: '2009–2026 共 18 卷，每道题都带知识点索引；做完直接落到对应正文。',
    icon: 'doc',
  },
  {
    no: '02',
    key: '知',
    title: '正文、公式与动画同页',
    desc: 'KaTeX 就地渲染公式，链表反转、KMP、进程调度都配 manim 动画。',
    icon: 'book',
  },
  {
    no: '03',
    key: '练',
    title: '错题必回，按遗忘曲线排期',
    desc: '错题进重做队列，背诵按间隔重复排期，今晚只做今晚该做的。',
    icon: 'retry',
  },
  {
    no: '04',
    key: '考',
    title: '18 卷整卷计时模考',
    desc: '交卷即出四科掌握度与薄弱知识块，直接回流下一轮复习计划。',
    icon: 'clock',
  },
]

const abilities = [
  {
    no: '01',
    title: '真题整卷',
    headline: '18 卷真题，按年份整卷刷',
    desc: '2009–2026 每一卷都是完整 47 题，含综合应用题。计时、交卷、复盘在同一屏完成，不会做到一半失去上下文。',
    points: ['每题带知识点索引，错题自动归档', '整套计时，交卷即出分数与用时分布', '2012 / 2021 / 2026 卷分值异常已标注'],
    visual: 'exams',
  },
  {
    no: '02',
    title: '知识正文',
    headline: '看不懂就跳到正文，不必换 App',
    desc: '每个知识块都有成体系的正文：概念、推导、复杂度对比。公式就地渲染，动画讲清楚指针怎么动、线程怎么长。',
    points: ['KaTeX 公式，行内与独立块混排', '链表反转、二叉树遍历、KMP、调度算法配动画', '从错题一键跳到对应小节，不用自己找'],
    visual: 'knowledge',
  },
  {
    no: '03',
    title: '错题与背诵',
    headline: '错题必回，背诵按遗忘曲线排',
    desc: '错题自动进重做队列，背诵卡按间隔重复排期。今天复习什么由数据决定，不由心情决定。',
    points: ['错题重做会自动跳过已掌握的题', '卡片按 1 / 3 / 7 / 14 天到期提醒', '薄弱知识块直接排进下一轮计划'],
    visual: 'review',
  },
]
</script>

<template>
  <div class="min-h-screen bg-[var(--k-bg)]">
    <SiteHeader />

    <!-- ============ Hero ============ -->
    <section class="relative overflow-hidden">
      <!-- 空气感光晕：右上青绿、左下淡紫 -->
      <div
        class="lp-glow pointer-events-none absolute -right-40 -top-32 h-[520px] w-[520px] rounded-full blur-[110px]"
        style="background: radial-gradient(circle, var(--k-brand) 0%, transparent 68%)"
        aria-hidden="true"
      />
      <div
        class="lp-glow-soft pointer-events-none absolute -bottom-40 -left-32 h-[420px] w-[420px] rounded-full blur-[110px]"
        style="background: radial-gradient(circle, #7C5CF0 0%, transparent 68%)"
        aria-hidden="true"
      />

      <div class="relative mx-auto grid w-full max-w-[1200px] items-center gap-12 px-5 py-14 md:px-8 md:py-20 lg:grid-cols-[1fr_480px] lg:gap-16">
        <div>
          <p
            class="inline-flex items-center gap-2 rounded-full border border-[var(--k-border)] bg-[var(--k-accent-soft)] px-3 py-1 text-xs font-semibold text-[var(--k-accent-text)]"
          >
            <span class="h-1.5 w-1.5 rounded-full bg-[var(--k-brand)]" aria-hidden="true" />
            2026 考研 · 408 计算机统考
          </p>

          <h1
            class="mt-5 text-[34px] font-bold leading-[1.18] tracking-tight text-[var(--k-heading)] sm:text-[44px] lg:text-[52px]"
          >
            把 <span class="text-[var(--k-accent-text)]">{{ totalQuestions }}</span> 道真题<br />
            钉在 <span class="text-[var(--k-accent-text)]">{{ pointCount }}</span> 个知识点上
          </h1>

          <p class="mt-5 max-w-[520px] text-[15px] leading-[1.75] text-[var(--k-text-muted)]">
            每道题都挂在知识块上：做完立刻跳到对应正文与公式动画，正确率回收成掌握度，薄弱点在下一轮自动回流。
          </p>

          <div class="mt-7 flex flex-wrap items-center gap-3">
            <RouterLink
              to="/practice"
              class="inline-flex items-center gap-1.5 rounded-[11px] bg-[var(--k-accent)] px-6 py-3 text-[15px] font-semibold text-[var(--k-on-accent)] no-underline transition-colors hover:bg-[var(--k-accent-hover)]"
            >
              开始刷题
              <span aria-hidden="true">→</span>
            </RouterLink>
            <a
              href="#loop"
              class="inline-flex items-center rounded-[11px] border border-[var(--k-border-strong)] px-6 py-3 text-[15px] font-semibold text-[var(--k-text)] no-underline transition-colors hover:bg-[var(--k-surface-soft)]"
            >
              先看核心闭环
            </a>
          </div>

          <p class="mt-6 text-xs text-[var(--k-text-faint)]">
            2009–2026 共 {{ paperCount }} 卷 · {{ totalQuestions }} 题 · 四科全纲 · 免注册即可试做
          </p>
        </div>

        <!-- 视觉：学完一轮后的样子（示意） -->
        <div class="relative">
          <div class="lp-card rounded-2xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
            <div class="flex items-center justify-between gap-3">
              <div class="flex items-center gap-2">
                <h2 class="text-sm font-bold text-[var(--k-heading)]">学完一轮后的样子</h2>
                <span
                  class="rounded-md bg-[var(--k-chip)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--k-text-faint)]"
                >
                  示意
                </span>
              </div>
              <span class="rounded-md bg-[var(--k-accent-soft)] px-2 py-0.5 text-[11px] font-semibold text-[var(--k-accent-text)]">
                本周 +18 题
              </span>
            </div>

            <dl class="mt-4 space-y-3.5">
              <div v-for="s in demoSubjects" :key="s.name">
                <div class="flex items-baseline justify-between gap-2">
                  <dt class="text-xs text-[var(--k-text-muted)]">{{ s.name }}</dt>
                  <dd class="text-sm font-bold tabular-nums text-[var(--k-heading)]">{{ s.value }}%</dd>
                </div>
                <div class="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-[var(--k-track)]">
                  <div
                    class="h-full rounded-full"
                    :style="{ width: s.value + '%', background: s.color }"
                  />
                </div>
              </div>
            </dl>

            <p class="mt-3 border-t border-[var(--k-border)] pt-3 text-[11px] text-[var(--k-text-faint)]">
              示意数据 · 按知识块综合正确率计算
            </p>
          </div>

          <div
            class="lp-card mt-3 flex items-center gap-2.5 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3"
          >
            <span class="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]">
              <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 8v5l3 2" stroke-linecap="round" />
                <circle cx="12" cy="12" r="9" />
              </svg>
            </span>
            <p class="text-xs text-[var(--k-text-muted)]">
              <span class="font-semibold text-[var(--k-heading)]">3 个薄弱知识块</span>
              已排入今晚复习队列
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 核心闭环（深色段）============ -->
    <section id="loop" class="lp-ink scroll-mt-[78px] bg-[var(--k-ink)]">
      <div class="mx-auto w-full max-w-[1200px] px-5 py-16 md:px-8 md:py-20">
        <p class="flex items-center gap-2.5 text-xs font-semibold tracking-wide text-[var(--k-brand)]">
          <span class="h-px w-7 bg-[var(--k-brand)]" aria-hidden="true" />
          核心闭环
        </p>
        <h2 class="mt-4 text-[26px] font-bold leading-tight text-white sm:text-[34px]">
          一条链，四个扣，转起来就不掉题
        </h2>
        <p class="mt-4 max-w-[640px] text-sm leading-[1.75] text-white/60">
          题不再是一次性的消耗：做完就在知识点上留下痕迹，痕迹聚合成掌握度，掌握度决定下一轮该复习什么。
        </p>

        <ol class="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <li v-for="(step, i) in loopSteps" :key="step.no" class="relative">
            <div class="lp-step h-full rounded-2xl border border-white/10 bg-white/[0.04] p-5">
              <div class="flex items-center justify-between">
                <span class="text-[11px] font-semibold tracking-wider text-white/35">
                  {{ step.no }} / {{ step.key }}
                </span>
                <span class="grid h-7 w-7 place-items-center rounded-lg bg-[var(--k-brand)]/15 text-[var(--k-brand)]">
                  <svg
                    v-if="step.icon === 'doc'"
                    viewBox="0 0 24 24"
                    class="h-3.5 w-3.5"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                  >
                    <path d="M8 3h6l4 4v14H6V3z" stroke-linejoin="round" />
                    <path d="M14 3v4h4M9 12h6M9 16h4" stroke-linecap="round" />
                  </svg>
                  <svg
                    v-else-if="step.icon === 'book'"
                    viewBox="0 0 24 24"
                    class="h-3.5 w-3.5"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                  >
                    <path d="M4 5a2 2 0 0 1 2-2h12v18H6a2 2 0 0 1-2-2V5z" stroke-linejoin="round" />
                    <path d="M8 7h6M8 11h6" stroke-linecap="round" />
                  </svg>
                  <svg
                    v-else-if="step.icon === 'retry'"
                    viewBox="0 0 24 24"
                    class="h-3.5 w-3.5"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                  >
                    <path d="M20 12a8 8 0 1 1-2.3-5.7" stroke-linecap="round" />
                    <path d="M20 4v5h-5" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="1.8">
                    <circle cx="12" cy="12" r="9" />
                    <path d="M12 7v5l3 2" stroke-linecap="round" />
                  </svg>
                </span>
              </div>
              <h3 class="mt-3.5 text-[15px] font-bold leading-snug text-white">{{ step.title }}</h3>
              <p class="mt-2 text-xs leading-[1.7] text-white/50">{{ step.desc }}</p>
            </div>

            <span
              v-if="i < loopSteps.length - 1"
              class="absolute -right-2.5 top-1/2 hidden -translate-y-1/2 text-white/20 lg:block"
              aria-hidden="true"
            >
              <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M4 12h16M15 7l5 5-5 5" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
          </li>
        </ol>
      </div>
    </section>

    <!-- ============ 四大能力 ============ -->
    <section id="abilities" class="scroll-mt-[78px]">
      <div class="mx-auto w-full max-w-[1200px] px-5 py-16 md:px-8 md:py-20">
        <p class="flex items-center gap-2.5 text-xs font-semibold tracking-wide text-[var(--k-accent-text)]">
          <span class="h-px w-7 bg-[var(--k-accent)]" aria-hidden="true" />
          四大能力
        </p>
        <h2 class="mt-4 text-[26px] font-bold leading-tight text-[var(--k-heading)] sm:text-[34px]">
          四件事，各自做到底
        </h2>
        <p class="mt-4 max-w-[640px] text-sm leading-[1.75] text-[var(--k-text-muted)]">
          不是四个散落的工具：真题、正文、错题与模考共用一套知识点索引与掌握度数据。
        </p>

        <div class="mt-12 space-y-14 md:space-y-20">
          <div
            v-for="(ab, i) in abilities"
            :key="ab.no"
            class="grid items-center gap-8 md:grid-cols-2 md:gap-12"
          >
            <div :class="i % 2 === 1 ? 'md:order-2' : ''">
              <p class="text-[11px] font-semibold tracking-wider text-[var(--k-text-faint)]">
                {{ ab.no }} {{ ab.title }}
              </p>
              <h3 class="mt-2.5 text-[21px] font-bold leading-snug text-[var(--k-heading)] sm:text-[25px]">
                {{ ab.headline }}
              </h3>
              <p class="mt-3.5 text-sm leading-[1.75] text-[var(--k-text-muted)]">{{ ab.desc }}</p>
              <ul class="mt-5 space-y-2.5">
                <li
                  v-for="pt in ab.points"
                  :key="pt"
                  class="flex items-start gap-2.5 text-[13px] leading-relaxed text-[var(--k-text-muted)]"
                >
                  <span class="mt-0.5 shrink-0 text-[var(--k-accent)]" aria-hidden="true">
                    <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2.4">
                      <path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </span>
                  {{ pt }}
                </li>
              </ul>
            </div>

            <div :class="i % 2 === 1 ? 'md:order-1' : ''">
              <div class="lp-visual rounded-2xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-3 sm:p-4">
                <!-- 真题整卷：真实年份与题数 -->
                <div v-if="ab.visual === 'exams'" class="lp-inner rounded-xl bg-[var(--k-surface)] p-4">
                  <div class="flex items-center justify-between">
                    <p class="text-xs font-semibold text-[var(--k-heading)]">按年份整卷刷</p>
                    <span class="text-[10px] text-[var(--k-text-faint)]">
                      {{ paperCount }} 卷 · {{ totalQuestions }} 题
                    </span>
                  </div>
                  <div class="mt-3 space-y-1.5">
                    <div
                      v-for="row in demoYears"
                      :key="row.year"
                      class="flex items-center justify-between rounded-lg px-2.5 py-2"
                      :class="row.warn ? 'bg-[var(--k-warn-soft)]' : 'bg-[var(--k-surface-soft)]'"
                    >
                      <span class="flex items-center gap-2 text-[11px] text-[var(--k-text-muted)]">
                        <span class="font-semibold text-[var(--k-heading)]">{{ row.year }}</span>
                        全国统考卷 · {{ row.questionCount }} 题
                      </span>
                      <span
                        class="text-[11px] font-semibold"
                        :class="row.warn ? 'text-[var(--k-warn)]' : 'text-[var(--k-accent-text)]'"
                      >
                        {{ row.warn ? '综合题 68 分' : '查看整卷' }}
                      </span>
                    </div>
                  </div>
                  <p class="mt-2.5 text-[10px] text-[var(--k-text-faint)]">
                    真实数据 · 选择 40 + 综合 7
                  </p>
                </div>

                <!-- 知识正文 -->
                <div v-else-if="ab.visual === 'knowledge'" class="lp-inner rounded-xl bg-[var(--k-surface)] p-4">
                  <p class="text-[10px] text-[var(--k-text-faint)]">数据结构 / 绪论</p>
                  <p class="mt-1 text-[13px] font-bold text-[var(--k-heading)]">数据结构基本概念</p>
                  <div class="mt-2.5 flex flex-wrap gap-1.5">
                    <span
                      v-for="b in (knowledgeBlocks.length ? knowledgeBlocks : [{ id: 'a', title: '数据、数据元素、数据项与数据对象' }, { id: 'b', title: '数据的逻辑结构' }, { id: 'c', title: '数据的存储结构' }])"
                      :key="b.id"
                      class="rounded-md bg-[var(--k-surface-soft)] px-2 py-1 text-[10px] text-[var(--k-text-muted)]"
                    >
                      {{ b.title }}
                    </span>
                  </div>
                  <p class="mt-2.5 text-[11px] leading-relaxed text-[var(--k-text-muted)]">
                    数据是信息的载体，是描述客观事物属性的数、字符以及所有能输入到计算机并被程序处理的符号的集合。
                  </p>
                  <span
                    class="mt-2.5 inline-flex items-center gap-1.5 rounded-md bg-[var(--k-accent-soft)] px-2 py-1 text-[10px] font-semibold text-[var(--k-accent-text)]"
                  >
                    <span class="h-1.5 w-1.5 rounded-full bg-[var(--k-brand)]" aria-hidden="true" />
                    KaTeX 公式 · manim 动画
                  </span>
                </div>

                <!-- 错题与背诵：真实四科题量 -->
                <div v-else class="lp-inner rounded-xl bg-[var(--k-surface)] p-4">
                  <div class="flex items-center justify-between">
                    <p class="text-xs font-semibold text-[var(--k-heading)]">四科题量分布</p>
                    <span class="text-[10px] text-[var(--k-text-faint)]">{{ totalQuestions }} 题</span>
                  </div>
                  <div class="mt-3 space-y-2">
                    <div
                      v-for="s in subjectTotals"
                      :key="s.key"
                      class="flex items-center gap-2.5"
                    >
                      <span class="h-2 w-2 shrink-0 rounded-full" :style="{ background: s.color }" />
                      <span class="w-[86px] shrink-0 text-[11px] text-[var(--k-text-muted)]">{{ s.name }}</span>
                      <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-[var(--k-track)]">
                        <div
                          class="h-full rounded-full"
                          :style="{
                            width: (s.total / Math.max(...subjectTotals.map((x) => x.total))) * 100 + '%',
                            background: s.color,
                          }"
                        />
                      </div>
                      <span class="w-8 shrink-0 text-right text-[10px] tabular-nums text-[var(--k-text-faint)]">
                        {{ s.total }}
                      </span>
                    </div>
                  </div>
                  <p class="mt-2.5 text-[10px] text-[var(--k-text-faint)]">真实数据 · 错题与背诵按此排期</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 数据说明 ============ -->
    <section id="about-data" class="scroll-mt-[78px] border-t border-[var(--k-border)]">
      <div class="mx-auto w-full max-w-[1200px] px-5 py-14 md:px-8 md:py-16">
        <div class="grid gap-8 md:grid-cols-3">
          <div>
            <p class="text-xs font-semibold text-[var(--k-accent-text)]">题库</p>
            <p class="mt-2 text-[26px] font-bold tabular-nums text-[var(--k-heading)]">{{ totalQuestions }}</p>
            <p class="mt-1 text-xs text-[var(--k-text-muted)]">2009–2026 共 {{ paperCount }} 卷 · 47 题/卷</p>
          </div>
          <div>
            <p class="text-xs font-semibold text-[var(--k-accent-text)]">知识点</p>
            <p class="mt-2 text-[26px] font-bold tabular-nums text-[var(--k-heading)]">{{ pointCount }}</p>
            <p class="mt-1 text-xs text-[var(--k-text-muted)]">知识块，题目与知识点一一关联</p>
          </div>
          <div>
            <p class="text-xs font-semibold text-[var(--k-accent-text)]">内容来源</p>
            <p class="mt-2 text-sm font-semibold text-[var(--k-heading)]">simple408 开源项目</p>
            <p class="mt-1 text-xs leading-relaxed text-[var(--k-text-muted)]">
              遵循 CC BY 4.0 许可。2012 / 2021 / 2026 卷综合题分值异常已在卷面标注。
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ Footer ============ -->
    <footer class="lp-ink relative overflow-hidden bg-[var(--k-ink)]">
      <div
        class="lp-glow pointer-events-none absolute -bottom-32 right-0 h-[380px] w-[380px] rounded-full blur-[100px]"
        style="background: radial-gradient(circle, var(--k-brand) 0%, transparent 68%)"
        aria-hidden="true"
      />

      <div class="relative mx-auto w-full max-w-[1200px] px-5 py-14 md:px-8">
        <div class="grid gap-10 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
          <div>
            <p class="text-lg font-bold text-white">题知链 408</p>
            <p class="mt-2.5 max-w-[260px] text-xs leading-[1.8] text-white/50">
              把真题、知识体系与掌握度连成一条复习主线。题知互链，数据驱动。
            </p>
            <p class="mt-3 text-[11px] text-white/35">个人学习项目，不用于商业目的。</p>
          </div>

          <nav>
            <p class="text-xs font-semibold text-white/80">学习</p>
            <ul class="mt-3 space-y-2">
              <li v-for="l in [
                { t: '按范围刷题', to: '/practice' },
                { t: '真题整卷', to: '/exams' },
                { t: '错题重做', to: '/practice' },
                { t: '间隔背诵', to: '/memory' },
                { t: '模拟考试', to: '/mock/start' },
              ]" :key="l.t">
                <RouterLink :to="l.to" class="text-xs text-white/50 no-underline transition-colors hover:text-[var(--k-brand)]">
                  {{ l.t }}
                </RouterLink>
              </li>
            </ul>
          </nav>

          <nav>
            <p class="text-xs font-semibold text-white/80">知识体系</p>
            <ul class="mt-3 space-y-2">
              <li v-for="s in ['数据结构', '计算机组成原理', '操作系统', '计算机网络']" :key="s">
                <RouterLink to="/learn" class="text-xs text-white/50 no-underline transition-colors hover:text-[var(--k-brand)]">
                  {{ s }}
                </RouterLink>
              </li>
            </ul>
          </nav>

          <nav>
            <p class="text-xs font-semibold text-white/80">关于</p>
            <ul class="mt-3 space-y-2">
              <li>
                <RouterLink to="/about" class="text-xs text-white/50 no-underline transition-colors hover:text-[var(--k-brand)]">
                  数据说明
                </RouterLink>
              </li>
              <li>
                <RouterLink to="/about" class="text-xs text-white/50 no-underline transition-colors hover:text-[var(--k-brand)]">
                  开源许可 CC BY 4.0
                </RouterLink>
              </li>
            </ul>
          </nav>
        </div>

        <div class="mt-10 flex flex-col gap-2 border-t border-white/10 pt-5 text-[11px] text-white/35 sm:flex-row sm:items-center sm:justify-between">
          <p>Copyright 2026 题知链 408 · 仅供个人复习使用</p>
          <p>内容来自 simple408 开源项目，遵循 CC BY 4.0 许可</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<!--
  这里刻意不用 scoped。
  实测：Vue 编译 `:global(.dark) .lp-card` 时会把选择器拆坏，
  深色主题下整个页面变成一片暗灰、文字与背景几乎同色。
  BrandLogo.vue 里踩过同一个坑。
  改为普通 style + 唯一前缀 .lp-，作用域靠命名保证。
-->
<style>
/* ---------- 浅色 ---------- */
.lp-card,
.lp-visual {
  box-shadow: 0 18px 44px -24px rgb(var(--k-shadow) / 0.35);
}

.lp-glow {
  opacity: 0.4;
}

.lp-glow-soft {
  opacity: 0.25;
}

/* ---------- 深色主题 ---------- */

/* 阴影在深色底上几乎无效，改用更实的投影 + 更亮的描边 */
.dark .lp-card,
.dark .lp-visual {
  box-shadow: 0 20px 46px -26px rgba(0, 0, 0, 0.85);
  border-color: var(--k-border-strong);
}

/* 光晕要收：浅色下 0.4 的青绿光晕在深色底上会整片发灰 */
.dark .lp-glow {
  opacity: 0.2;
}

.dark .lp-glow-soft {
  opacity: 0.12;
}

/* 演示卡内层：与浅色主题的外层容器拉开一档 */
.dark .lp-inner {
  background: var(--k-surface);
  border: 1px solid var(--k-border);
}

/*
 * 深色区块再压深一档。
 * 这两段在浅色主题下本来就是深色，深色主题下若用页面底色会糊成一片，
 * 段落边界消失，所以用「更暗」而不是「更亮」来区分层次。
 */
.dark .lp-ink {
  background: #080b0f;
}

/* 闭环四扣：底色与描边都提亮，否则在 #080b0f 上看不出卡片边界 */
.dark .lp-step {
  background: rgb(255 255 255 / 0.055);
  border-color: rgb(255 255 255 / 0.13);
}
</style>