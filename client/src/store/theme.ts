import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'auto'

const STORAGE_KEY = 'kaoyan408.theme'

/**
 * 主题：支持「跟随时间」——按当前小时自动切换昼夜。
 * 白天（06:00–18:00）用亮色，夜间用暗色；也允许手动锁定。
 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readMode())
  const hour = ref(new Date().getHours())

  /** 按时间推导出的主题（与手动选择无关）。 */
  const timeBasedTheme = computed<'light' | 'dark'>(() =>
    hour.value >= 6 && hour.value < 18 ? 'light' : 'dark',
  )

  /** 最终生效的主题。 */
  const effectiveTheme = computed<'light' | 'dark'>(() =>
    mode.value === 'auto' ? timeBasedTheme.value : mode.value,
  )

  const isDark = computed(() => effectiveTheme.value === 'dark')

  /** 距离下次自动切换还有多久（用于展示提示）。 */
  const nextSwitchHint = computed(() => {
    const h = hour.value
    if (h >= 6 && h < 18) return '18:00 自动切换到夜间'
    return '06:00 自动切换到日间'
  })

  function apply() {
    const root = document.documentElement
    root.classList.toggle('dark', effectiveTheme.value === 'dark')
    root.dataset.theme = effectiveTheme.value
    // 让浏览器原生控件（滚动条等）跟随
    root.style.colorScheme = effectiveTheme.value
  }

  function readMode(): ThemeMode {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw === 'light' || raw === 'dark' || raw === 'auto') return raw
    } catch {
      /* 忽略隐私模式 */
    }
    return 'auto'
  }

  function setMode(next: ThemeMode) {
    mode.value = next
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* 忽略 */
    }
  }

  /** 循环切换：自动 → 亮 → 暗 → 自动 */
  function cycle() {
    setMode(mode.value === 'auto' ? 'light' : mode.value === 'light' ? 'dark' : 'auto')
  }

  let timer: ReturnType<typeof setInterval> | null = null

  function start() {
    apply()
    // 每分钟对一次表，跨过 06:00 / 18:00 时自动切换
    if (timer === null) {
      timer = setInterval(() => {
        const current = new Date().getHours()
        if (current !== hour.value) hour.value = current
      }, 60_000)
    }
  }

  watch(effectiveTheme, apply)

  return { mode, hour, isDark, effectiveTheme, timeBasedTheme, nextSwitchHint, setMode, cycle, start }
})