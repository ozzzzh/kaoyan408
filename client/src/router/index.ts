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
    { path: '/practice', name: 'practice', component: () => import('@/views/PracticeView.vue') },
    { path: '/exams', name: 'exams', component: () => import('@/views/ExamsView.vue') },
    { path: '/stats', name: 'stats', component: () => import('@/views/StatsView.vue') },
    { path: '/me', name: 'me', component: () => import('@/views/MeView.vue') },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFoundView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router