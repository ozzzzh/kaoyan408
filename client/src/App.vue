<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()
const { user, dashboard } = storeToRefs(userStore)

/** 首页为整幅 Hero 版式，需要脱离内容区最大宽度限制。 */
const isHome = computed(() => route.name === 'home')

const navItems = [
  { to: '/', label: '首页' },
  { to: '/learn', label: '知识' },
  { to: '/practice', label: '刷题' },
  { to: '/exams', label: '真题' },
  { to: '/stats', label: '看板' },
]

onMounted(async () => {
  await Promise.all([userStore.restore(), userStore.loadDashboard()])
})
</script>

<template>
  <div class="min-h-full flex flex-col">
    <header class="border-b border-[#e2e9f5] bg-white/85 backdrop-blur sticky top-0 z-20">
      <div class="mx-auto max-w-6xl px-4 h-14 flex items-center gap-6">
        <RouterLink to="/" class="flex items-baseline gap-2 shrink-0">
          <span class="text-lg font-bold tracking-tight text-[#0b1f45]">408 考盐</span>
          <span class="hidden sm:inline text-xs text-[#9aa9c3]">计算机统考复习主线</span>
        </RouterLink>

        <nav class="flex items-center gap-1 text-sm">
          <RouterLink
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="px-3 py-1.5 rounded-md text-[#5c6f94] hover:text-[#0b1f45] hover:bg-[#f0f4ff] transition"
            active-class="!text-[#1845ad] bg-[#e8efff] font-semibold"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="ml-auto flex items-center gap-3 text-sm">
          <span v-if="dashboard" class="hidden md:inline text-[#9aa9c3]">
            题库 {{ dashboard.totalQuestions }} 题 · 已刷 {{ dashboard.doneQuestions }}
          </span>
          <RouterLink v-if="!user" to="/me" class="text-[#1f49ba] font-semibold hover:underline">登录</RouterLink>
          <RouterLink v-else to="/me" class="text-[#5c6f94] hover:text-[#0b1f45]">{{ user.username }}</RouterLink>
        </div>
      </div>
    </header>

    <main class="flex-1 w-full" :class="isHome ? '' : 'mx-auto max-w-6xl px-4 py-8'">
      <RouterView />
    </main>

    <footer class="border-t border-[#e2e9f5] bg-white">
      <div class="mx-auto max-w-6xl px-4 py-6 text-xs text-[#9aa9c3] leading-relaxed">
        <p>408 考盐 · 真题 2009–2026 共 18 卷，按 408os 官方参照校准。</p>
        <p class="mt-1">
          知识正文与真题数据源自
          <a class="text-[#1f49ba] hover:underline" href="https://github.com/liangbohan/postgraduate-exam-website" target="_blank" rel="noreferrer">simple408</a>
          （CC BY 4.0），本站为复习用途的二次组织。
        </p>
      </div>
    </footer>
  </div>
</template>