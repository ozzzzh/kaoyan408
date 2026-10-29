import { createRouter, createWebHashHistory } from 'vue-router'

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
      // 工作台固定占用根路径。
      //
      // 之前试过「未登录时 / 分流到落地页」，结果是游客点侧边栏的「工作台」
      // 会被守卫拦回落地页，等于把工作台弄没了。工作台是主入口，
      // 不该因为登录状态而消失，所以 / 永远是工作台。
      // 落地页放在 /welcome，从首页 Logo 或直接输入地址进入。
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
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
  // 只在路径变化时回到顶部。
  // 落地页的「核心闭环 / 四大能力」是页内跳转，靠改 query 记录当前段落；
  // 若无条件回到顶部，点导航会先滚过去又被拉回页首，看起来像没反应。
  scrollBehavior: (to, from) => {
    if (to.name === from.name && to.path === from.path) return false
    return { top: 0 }
  },
})

export default router