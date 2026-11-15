/**
 * 首帧主题预设（在 <head> 里同步加载，早于任何渲染）。
 *
 * 为什么需要它：theme store 的 apply() 只在 App.vue 的 onMounted 里执行，也就是说
 * 暗色用户的第一帧一定是白底，随后才闪成暗色。加上 main.ts 里 await restore()
 * 会先等一次用户信息请求（网络不好时 1-2 秒），这一闪会格外明显。
 *
 * 为什么是外链而不是内联脚本：站点下发的是严格 CSP（script-src 'self'），
 * 内联脚本需要登记 sha256，每次改这段逻辑都要同步改 nginx 配置并重新部署；
 * 外链文件既满足 CSP，也不会有「改了脚本忘了改 hash、主题静默失效」这种坑。
 *
 * 本文件位于 public/，构建时原样复制到 dist 根目录，不经打包。
 * 逻辑必须与 src/store/theme.ts 一致：
 *   light / dark / auto（缺省 auto：06:00-18:00 日间，其余夜间）
 */
;(function () {
  try {
    var mode = localStorage.getItem('kaoyan408.theme') || 'auto'
    var hour = new Date().getHours()
    var dark = mode === 'dark' || (mode !== 'light' && (hour < 6 || hour >= 18))
    var root = document.documentElement
    root.classList.toggle('dark', dark)
    root.dataset.theme = dark ? 'dark' : 'light'
    root.style.colorScheme = dark ? 'dark' : 'light'
  } catch (e) {
    // 隐私模式下 localStorage 抛异常：保持默认亮色，不阻塞渲染
  }
})()