<script setup lang="ts">
/**
 * 营销落地页。
 *
 * 与工作台分流的规则见 router/index.ts：未登录访问 / 时落到这里，
 * 已登录直接进工作台，所以老用户不会被打扰。
 *
 * 关于页面里的数据：Hero 右侧的掌握度、闭环与能力区的演示卡都是
 * **示意值**，不是当前访客的真实统计。营销页用示意数据是常规做法，
 * 但标题已改为「学完一轮后的样子」而不是「四科掌握度」，
 * 避免未登录用户误以为系统已经在统计他。
 */
import { onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import { useThemeStore } from '@/store/theme'

const themeStore = useThemeStore()

onMounted(() => {
  themeStore.start()
})

/** 示意数据：与设计稿一致，仅用于展示形态 */
const demoSubjects = [
  { name: '数据结构', value: 72, color: '#3D6BE5' },
  { name: '计算机组成原理', value: 58, color: '#F0A020' },
  { name: '操作系统', value: 64, color: '#7C5CF0' },
  { name: '计算机网络', value: 41, color: '#E0574F' },
]

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
        class="pointer-events-none absolute -right-40 -top-32 h-[520px] w-[520px] rounded-full opacity-40 blur-[110px]"
        style="background: radial-gradient(circle, var(--k-brand) 0%, transparent 68%)"
        aria-hidden="true"
      />
      <div
        class="pointer-events-none absolute -bottom-40 -left-32 h-[420px] w-[420px] rounded-full opacity-25 blur-[110px]"
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
            把 <span class="text-[var(--k-accent-text)]">846</span> 道真题<br />
            钉在 <span class="text-[var(--k-accent-text)]">147</span> 个知识点上
          </h1>

          <p class="mt-5 max-w-[520px] text-[15px] leading-[1.75] text-[var(--k-text-muted)]">
            每道题都挂在知识块上：做完立刻跳到对应正文与公式动画，正确率回收成掌握度，薄弱点在下一轮自动回流。
          </p>

          <div class="mt-7 flex flex-wrap items-center gap-3">
            <RouterLink
              to="/practice"
              class="inline-flex items-center gap-1.5 rounded-[11px] bg-[var(--k-accent)] px-6 py-3 text-[15px] font-semibold text-white no-underline transition-colors hover:bg-[var(--k-accent-hover)]"
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
            2009–2026 共 18 卷 · 846 题 · 四科全纲 · 免注册即可试做
          </p>
        </div>

        <!-- 视觉：学完一轮后的样子（示意） -->
        <div class="relative">
          <div
            class="rounded-2xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5 shadow-[0_18px_44px_-24px_rgba(22,32,46,0.35)]"
          >
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
            class="mt-3 flex items-center gap-2.5 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 shadow-[0_10px_28px_-22px_rgba(22,32,46,0.4)]"
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
    <section id="loop" class="scroll-mt-[78px] bg-[var(--k-ink)]">
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
            <div class="h-full rounded-2xl border border-white/10 bg-white/[0.04] p-5">
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

            <!-- 卡片间的链条连接（仅桌面、非末项） -->
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
            <!-- 文案 -->
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

            <!-- 演示卡 -->
            <div :class="i % 2 === 1 ? 'md:order-1' : ''">
              <div class="rounded-2xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-3 sm:p-4">
                <!-- 真题整卷 -->
                <div v-if="ab.visual === 'exams'" class="rounded-xl bg-[var(--k-surface)] p-4">
                  <div class="flex items-center justify-between">
                    <p class="text-xs font-semibold text-[var(--k-heading)]">按年份整卷刷</p>
                    <span class="text-[10px] text-[var(--k-text-faint)]">18 卷 · 846 题</span>
                  </div>
                  <div class="mt-3 space-y-1.5">
                    <div
                      v-for="row in [
                        { year: '2024', score: '82%', warn: false },
                        { year: '2023', score: '68%', warn: false },
                        { year: '2012', score: '综合题 68 分', warn: true },
                      ]"
                      :key="row.year"
                      class="flex items-center justify-between rounded-lg px-2.5 py-2"
                      :class="row.warn ? 'bg-[var(--k-warn-soft)]' : 'bg-[var(--k-surface-soft)]'"
                    >
                      <span class="flex items-center gap-2 text-[11px] text-[var(--k-text-muted)]">
                        <span class="font-semibold text-[var(--k-heading)]">{{ row.year }}</span>
                        全国统考卷 · 47 题
                      </span>
                      <span
                        class="text-[11px] font-semibold"
                        :class="row.warn ? 'text-[var(--k-warn)]' : 'text-[var(--k-accent-text)]'"
                      >
                        {{ row.score }}
                      </span>
                    </div>
                  </div>
                  <p class="mt-2.5 text-[10px] text-[var(--k-text-faint)]">示意 · 已做 39/47 · 用时 96 分钟</p>
                </div>

                <!-- 知识正文 -->
                <div v-else-if="ab.visual === 'knowledge'" class="rounded-xl bg-[var(--k-surface)] p-4">
                  <p class="text-[10px] text-[var(--k-text-faint)]">操作系统 / 进程管理 / 状态转换</p>
                  <p class="mt-1 text-[13px] font-bold text-[var(--k-heading)]">进程的五态模型与调度</p>
                  <div class="mt-2.5 rounded-lg bg-[var(--k-surface-soft)] px-3 py-2.5 font-mono text-[11px] leading-relaxed text-[var(--k-text)]">
                    W = ( 完成时间 − 到达时间 − 服务时间 ) / n
                  </div>
                  <p class="mt-2.5 text-[11px] leading-relaxed text-[var(--k-text-muted)]">
                    阻塞态不占 CPU：仍在内存中，被调度器选中时才回到运行态。
                  </p>
                  <span
                    class="mt-2.5 inline-flex items-center gap-1.5 rounded-md bg-[var(--k-accent-soft)] px-2 py-1 text-[10px] font-semibold text-[var(--k-accent-text)]"
                  >
                    <span class="h-1.5 w-1.5 rounded-full bg-[var(--k-brand)]" aria-hidden="true" />
                    manim 动画 · 五态转换
                  </span>
                  <p class="mt-2 text-[10px] text-[var(--k-text-faint)]">示意</p>
                </div>

                <!-- 错题与背诵 -->
                <div v-else class="rounded-xl bg-[var(--k-surface)] p-4">
                  <div class="flex items-center justify-between">
                    <p class="text-xs font-semibold text-[var(--k-heading)]">今天该复习的内容</p>
                    <span class="text-[10px] text-[var(--k-text-faint)]">周三 19:40</span>
                  </div>
                  <div class="mt-3 space-y-2">
                    <div class="flex items-center justify-between rounded-lg bg-[var(--k-surface-soft)] px-3 py-2.5">
                      <span class="flex items-center gap-2">
                        <span class="grid h-5 w-5 place-items-center rounded-md bg-[var(--k-danger)]/12 text-[var(--k-danger)]">
                          <svg viewBox="0 0 24 24" class="h-3 w-3" fill="none" stroke="currentColor" stroke-width="2.4">
                            <path d="M12 8v5M12 16.5v.5" stroke-linecap="round" />
                          </svg>
                        </span>
                        <span class="text-[11px] text-[var(--k-text-muted)]">
                          <span class="font-semibold text-[var(--k-heading)]">错题重做 · 12 题</span><br />
                          <span class="text-[10px] text-[var(--k-text-faint)]">按正确率 0% 优先</span>
                        </span>
                      </span>
                      <span class="rounded-md bg-[var(--k-accent-soft)] px-2 py-1 text-[10px] font-semibold text-[var(--k-accent-text)]">开始</span>
                    </div>
                    <div class="flex items-center justify-between rounded-lg bg-[var(--k-surface-soft)] px-3 py-2.5">
                      <span class="flex items-center gap-2">
                        <span class="grid h-5 w-5 place-items-center rounded-md bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]">
                          <svg viewBox="0 0 24 24" class="h-3 w-3" fill="none" stroke="currentColor" stroke-width="2.4">
                            <path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" />
                          </svg>
                        </span>
                        <span class="text-[11px] text-[var(--k-text-muted)]">
                          <span class="font-semibold text-[var(--k-heading)]">背诵卡 · 8 张到期</span><br />
                          <span class="text-[10px] text-[var(--k-text-faint)]">按遗忘曲线排 · 迎战期 5 张</span>
                        </span>
                      </span>
                      <span class="rounded-md bg-[var(--k-accent-soft)] px-2 py-1 text-[10px] font-semibold text-[var(--k-accent-text)]">复习</span>
                    </div>
                  </div>
                  <p class="mt-2.5 text-[10px] text-[var(--k-text-faint)]">下次到期 今晚 21:00 · 同理 3/7/14 天</p>
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
            <p class="mt-2 text-[26px] font-bold tabular-nums text-[var(--k-heading)]">846</p>
            <p class="mt-1 text-xs text-[var(--k-text-muted)]">2009–2026 共 18 卷 · 47 题/卷</p>
          </div>
          <div>
            <p class="text-xs font-semibold text-[var(--k-accent-text)]">知识点</p>
            <p class="mt-2 text-[26px] font-bold tabular-nums text-[var(--k-heading)]">147</p>
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
    <footer class="relative overflow-hidden bg-[var(--k-ink)]">
      <div
        class="pointer-events-none absolute -bottom-32 right-0 h-[380px] w-[380px] rounded-full opacity-30 blur-[100px]"
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