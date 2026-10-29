import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/api/client'

/**
 * 用 hash 路由：部署到任意静态目录都能直接打开（无 nginx rewrite 依赖）。
 *
 * 首页分流：未登录看到营销落地页，已登录直接进工作台。
 * 这样既保留了对外介绍页，老用户也不会每次进来都多点一次。
 * 落地页由前端会话状态决定，服务端不做重定向。
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      // 未登录时改由落地页承接，见下方 beforeEach
    },
    // 独立路由：落地页不套工作台侧边栏外壳
    {
      path: '/welcome',
      name: 'landing',
      component: () => import('@/views/LandingView.vue'),
      meta: { bare: true },
    },
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
    // sessionId 允许负号：游客场次走服务端内存，用负数 id 与数据库自增 id 区分。
    { path: '/mock/start', name: 'mock-start', component: () => import('@/views/MockStartView.vue') },
    { path: '/mock/:sessionId(-?\\d+)/result', name: 'mock-result', component: () => import('@/views/MockResultView.vue') },
    { path: '/mock/:sessionId(-?\\d+)', name: 'mock-exam', component: () => import('@/views/MockExamView.vue') },
    { path: '/stats', name: 'stats', component: () => import('@/views/StatsView.vue') },
    { path: '/me', name: 'me', component: () => import('@/views/MeView.vue') },
    { path: '/about', name: 'about', component: () => import('@/views/AboutView.vue') },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFoundView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

/**
 * 未登录访问根路径时改看落地页。
 *
 * 只拦 `/` 这一条：其它页面（刷题、真题、知识点）游客本来就能用，
 * 不该因为没登录被推走。已登录用户访问 /welcome 则直接送回工作台，
 * 避免登录后还停留在营销页。
 */
router.beforeEach((to) => {
  const hasToken = Boolean(getToken())
  if (to.name === 'dashboard' && !hasToken) {
    return { name: 'landing', replace: true }
  }
  if (to.name === 'landing' && hasToken) {
    return { name: 'dashboard', replace: true }
  }
  return true
})

export default router