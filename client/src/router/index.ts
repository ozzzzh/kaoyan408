import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 用 hash 路由：部署到任意静态目录都能直接打开（无 nginx rewrite 依赖）。
 *
 * 首页分流：根路径 `/` 是营销落地页，工作台在 `/dashboard`。
 * 但分流**只在进入根路径时发生**，且用 beforeEnter 而不是全局守卫：
 *
 * 之前吃过亏——用全局守卫做「未登录 → 落地页」，结果游客点侧边栏的
 * 「工作台」也会被拦回落地页，等于把工作台弄没了。
 * 现在区分两件事：
 *   - 打开 `/`（对外入口）→ 已登录给工作台，未登录给落地页；
 *   - 直接打开 `/dashboard`（侧边栏入口）→ 永远给工作台，不看登录态。
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      // 根路径：按登录态决定看哪个。
      // 用户 store 在 main.ts 里已经 restore 过，这里能直接读到。
      path: '/',
      name: 'root',
      component: () => import('@/views/LandingView.vue'),
      meta: { bare: true },
      beforeEnter: () => {
        // 动态导入避免 router 与 store 的循环依赖
        return import('@/store/user').then(({ useUserStore }) => {
          const userStore = useUserStore()
          // 已登录：根路径直接给工作台，不套 bare 外壳
          return userStore.isLoggedIn ? { path: '/dashboard' } : true
        })
      },
    },
    // 工作台。不带 meta.bare，走侧边栏外壳。
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
    },
    // 落地页的固定地址：分享给别人介绍产品时用。
    // 已登录也能进来（有人就是想看介绍页），所以不做跳转。
    {
      path: '/welcome',
      name: 'landing',
      component: () => import('@/views/LandingView.vue'),
      meta: { bare: true },
    },
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
    { path: '/ai', name: 'ai', component: () => import('@/views/AiView.vue') },
    { path: '/stats', name: 'stats', component: () => import('@/views/StatsView.vue') },
    { path: '/me', name: 'me', component: () => import('@/views/MeView.vue') },
    { path: '/about', name: 'about', component: () => import('@/views/AboutView.vue') },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFoundView.vue') },
  ],
  // 只在路径变化时回到顶部。
  // 落地页的「核心闭环 / 四大能力」是页内跳转，靠改 query 记录当前段落；
  // 若无条件回到顶部，点导航会先滚过去又被拉回页首，看起来像没反应。
  scrollBehavior: (to, from) => {
    if (to.name === from.name && to.path === from.path) return false
    return { top: 0 }
  },
})

export default router