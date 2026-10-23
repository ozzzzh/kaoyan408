import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 用 hash 路由：部署到任意静态目录都能直接打开（无 nginx rewrite 依赖）。
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: '/learn', name: 'learn', component: () => import('@/views/LearnView.vue') },
    { path: '/practice', name: 'practice', component: () => import('@/views/PracticeView.vue') },
    { path: '/exams', name: 'exams', component: () => import('@/views/ExamsView.vue') },
    { path: '/stats', name: 'stats', component: () => import('@/views/StatsView.vue') },
    { path: '/me', name: 'me', component: () => import('@/views/MeView.vue') },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFoundView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router