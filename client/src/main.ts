import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useUserStore } from './store/user'
import './styles/main.css'
import 'katex/dist/katex.min.css'

/**
 * 挂载不再等待登录态。
 *
 * 原来 `await restore()` 之后才 mount，等于首屏必须先等一次 /user/current 往返；
 * 在只有几百毫秒 RTT 的链路上可接受，但线上入口要经过隧道（单请求 0.4–1.8 秒），
 * 这段时间用户看到的是完全空白。
 *
 * 现在先挂载（骨架屏立刻可见），登录态在后台恢复；唯一需要「确定结果」的地方是
 * 「根路径按登录态分流」，那里会 await whenAuthReady()，所以不会出现
 * 「已登录用户先看到落地页、再被弹回工作台」的闪烁。
 */
const app = createApp(App)
app.use(createPinia())

const userStore = useUserStore()
void userStore.restore()

app.use(router)
app.mount('#app')

/**
 * Service Worker：回访时把带哈希的构建产物与外壳从本地缓存直接给出，
 * 免掉再次跨境下载（线上入口单次往返 0.9–2.7 秒）。
 *
 * 只在生产构建里注册：开发环境注册会干扰 HMR，也让「改完刷新看不到变化」变成常态。
 * 失败不影响任何功能——它只是加速层。
 */
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      /* 注册失败按无缓存运行 */
    })
  })
}
