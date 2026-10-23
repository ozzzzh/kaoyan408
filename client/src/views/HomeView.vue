<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'
import BackgroundFx from '@/components/home/BackgroundFx.vue'
import LogoBrand from '@/components/home/LogoBrand.vue'
import TypeWriter from '@/components/home/TypeWriter.vue'
import SearchBox from '@/components/home/SearchBox.vue'
import CtaButtons from '@/components/home/CtaButtons.vue'

const userStore = useUserStore()
const { dashboard, user } = storeToRefs(userStore)

const subjectMeta: Record<string, { name: string; bar: string }> = {
  ds: { name: '数据结构', bar: 'bg-[#2d68ef]' },
  os: { name: '操作系统', bar: 'bg-[#7c5cf0]' },
  co: { name: '计算机组成原理', bar: 'bg-[#f0a020]' },
  cn: { name: '计算机网络', bar: 'bg-[#12a594]' },
}

const subjects = computed(() => dashboard.value?.subjects ?? [])

const features = [
  {
    title: '题与知识双向互链',
    desc: '846 道真题每题都挂到知识块上：做完题直接看对应正文，看正文时也能反查考过的真题。',
  },
  {
    title: '四科掌握度看板',
    desc: '按知识块聚合正确率，数据结构 / 操作系统 / 组成原理 / 计算机网络各自看到进度与薄弱点。',
  },
  {
    title: '公式与交互动画',
    desc: '知识点正文支持 KaTeX 公式与 manim 交互动画，把抽象的算法过程直接演给你看。',
  },
]
</script>

<template>
  <main
    class="home-page relative min-h-screen overflow-hidden px-6 pt-14 pb-16 font-sans text-[#0b1f45]"
  >
    <BackgroundFx />

    <!-- 首屏 Hero -->
    <section class="relative z-[1] w-full max-w-[980px] mx-auto text-center">
      <div class="text-sm tracking-[0.28em] text-[#5c6f94] font-bold">真题 · 知识 · 掌握度</div>

      <LogoBrand />

      <h1
        class="mt-[42px] mb-[18px] text-[clamp(48px,7vw,102px)] leading-[1.05] tracking-[-0.045em] font-[850]"
      >
        把<span class="text-[#1B3FA1] font-mono mx-3">408</span>学薄一点
      </h1>

      <TypeWriter />

      <SearchBox />

      <CtaButtons />

      <p v-if="!user" class="mt-6 text-sm text-[#7c8bab]">
        当前为游客模式：题库与知识可直接浏览，做题记录需要
        <RouterLink to="/me" class="text-[#1f49ba] font-semibold hover:underline">登录</RouterLink>
        后保存
      </p>
    </section>

    <!-- 数据面板 -->
    <section v-if="dashboard" class="relative z-[1] w-full max-w-[980px] mx-auto mt-20">
      <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
        <div class="rounded-2xl border border-[#e2e9f5] bg-white/80 px-5 py-4 backdrop-blur">
          <p class="text-xs text-[#7c8bab]">题库总量</p>
          <p class="mt-1 text-3xl font-bold tabular-nums">{{ dashboard.totalQuestions }}</p>
          <p class="mt-0.5 text-xs text-[#9aa9c3]">道正式真题</p>
        </div>
        <div class="rounded-2xl border border-[#e2e9f5] bg-white/80 px-5 py-4 backdrop-blur">
          <p class="text-xs text-[#7c8bab]">已作答</p>
          <p class="mt-1 text-3xl font-bold tabular-nums">{{ dashboard.doneQuestions }}</p>
          <p class="mt-0.5 text-xs text-[#9aa9c3]">完成度 {{ dashboard.progress }}%</p>
        </div>
        <div class="rounded-2xl border border-[#e2e9f5] bg-white/80 px-5 py-4 backdrop-blur">
          <p class="text-xs text-[#7c8bab]">正确率</p>
          <p class="mt-1 text-3xl font-bold tabular-nums">{{ dashboard.accuracy }}%</p>
          <p class="mt-0.5 text-xs text-[#9aa9c3]">答对 {{ dashboard.correctQuestions }} 题</p>
        </div>
        <div class="rounded-2xl border border-[#e2e9f5] bg-white/80 px-5 py-4 backdrop-blur">
          <p class="text-xs text-[#7c8bab]">连续打卡</p>
          <p class="mt-1 text-3xl font-bold tabular-nums">{{ dashboard.streakDays }}</p>
          <p class="mt-0.5 text-xs text-[#9aa9c3]">天</p>
        </div>
      </div>

      <!-- 四科掌握度 -->
      <div class="mt-4 rounded-2xl border border-[#e2e9f5] bg-white/80 p-6 backdrop-blur">
        <div class="flex items-baseline justify-between">
          <h2 class="text-sm font-bold text-[#3d4d6b]">四科掌握度</h2>
          <RouterLink to="/stats" class="text-xs text-[#1f49ba] hover:underline">
            查看学习看板 →
          </RouterLink>
        </div>
        <div class="mt-5 grid gap-x-8 gap-y-5 md:grid-cols-2">
          <div v-for="item in subjects" :key="item.subject">
            <div class="flex items-baseline justify-between text-sm">
              <span class="font-semibold">{{ subjectMeta[item.subject]?.name ?? item.subject }}</span>
              <span class="text-xs text-[#7c8bab] tabular-nums">
                {{ item.done }} / {{ item.total }}
              </span>
            </div>
            <div class="mt-2 h-2 overflow-hidden rounded-full bg-[#eef2fa]">
              <div
                class="h-full rounded-full transition-all duration-500"
                :class="subjectMeta[item.subject]?.bar ?? 'bg-[#2d68ef]'"
                :style="{ width: Math.max(item.progress, item.done > 0 ? 2 : 0) + '%' }"
              />
            </div>
            <p class="mt-1.5 text-xs text-[#9aa9c3]">
              完成 {{ item.progress }}% · 正确率 {{ item.accuracy }}%
            </p>
          </div>
        </div>
      </div>
    </section>

    <section
      v-else
      class="relative z-[1] mx-auto mt-20 w-full max-w-[980px] rounded-2xl border border-dashed border-[#dbe4ef] bg-white/70 p-6 text-center text-sm text-[#7c8bab]"
    >
      统计数据加载中或后端未启动（默认 http://127.0.0.1:8080）
    </section>

    <!-- 特性 -->
    <section class="relative z-[1] w-full max-w-[980px] mx-auto mt-14">
      <div class="grid gap-4 md:grid-cols-3">
        <article
          v-for="feature in features"
          :key="feature.title"
          class="rounded-2xl border border-[#e2e9f5] bg-white/80 p-6 backdrop-blur transition hover:-translate-y-0.5 hover:shadow-[0_14px_32px_rgba(52,87,150,0.1)]"
        >
          <h3 class="text-base font-bold text-[#14306b]">{{ feature.title }}</h3>
          <p class="mt-2 text-sm leading-relaxed text-[#5c6f94]">{{ feature.desc }}</p>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped>
.home-page {
  background: #fbfdff;
}
</style>