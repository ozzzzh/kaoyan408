import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useUserStore } from './store/user'
import './styles/main.css'
import 'katex/dist/katex.min.css'

/**
 * 先恢复登录态，再挂载。
 *
 * 为什么不能只靠 App.vue 的 onMounted：根路径 `/` 要按登录态分流
 * （已登录 → 工作台，未登录 → 落地页），而路由守卫比 onMounted 先跑。
 * 等 onMounted 里再 restore 的话，守卫读到的是「未登录」，
 * 已登录用户会先看到落地页、再被弹回工作台，明显闪一下。
 *
 * 这里只等 token 校验（一次 /user/current 请求），失败按游客处理，不阻塞启动。
 * 看板等页面数据仍交给 App.vue 异步加载，不拖慢首屏。
 */
const app = createApp(App)
app.use(createPinia())

const userStore = useUserStore()
try {
  await userStore.restore()
} catch {
  // 恢复失败按游客处理
}

app.use(router)
app.mount('#app')