import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 用 hash 路由：部署到任意静态目录都能直接打开（无 nginx rewrite 依赖）。
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    // 首页即工作台：不再保留独立的营销落地页
    { path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/dashboard', redirect: '/' },
    { path: '/learn', name: 'learn', component: () => import('@/views/LearnView.vue') },
    { path: '/learn/:pointId', name: 'learn-detail', component: () => import('@/views/LearnView.vue') },
    { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue') },
    { path: '/practice', name: 'practice', component: () => import('@/views/PracticeView.vue') },
    { path: '/memory', name: 'memory', component: () => import('@/views/MemoryView.vue') },
    { path: '/exams', name: 'exams', component: () => import('@/views/ExamsView.vue') },
    { path: '/exams/:year', name: 'exam-detail', component: () => import('@/views/ExamDetailView.vue') },
    // 模拟考试：start 是选择页，:sessionId 是考试中，result 是成绩单。
    // 注意 /mock/start 必须在 /mock/:sessionId 之前，否则 "start" 会被当成 sessionId。
    { path: '/mock/start', name: 'mock-start', component: () => import('@/views/MockStartView.vue') },
    { path: '/mock/:sessionId(\\d+)/result', name: 'mock-result', component: () => import('@/views/MockResultView.vue') },
    { path: '/mock/:sessionId(\\d+)', name: 'mock-exam', component: () => import('@/views/MockExamView.vue') },
    { path: '/stats', name: 'stats', component: () => import('@/views/StatsView.vue') },
    { path: '/me', name: 'me', component: () => import('@/views/MeView.vue') },
    { path: '/about', name: 'about', component: () => import('@/views/AboutView.vue') },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFoundView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router