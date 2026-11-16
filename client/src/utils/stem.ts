import { escapeHtml } from './htmlEscape'

/**
 * 题干的轻量渲染。
 *
 * 为什么不用 marked：题干只需要「代码块 / 行内代码 / 加粗 / 换行」这四件事，
 * 而完整的 marked + GFM 会把 300KB+ 的解析链路拉进刷题页首屏。
 *
 * 安全模型很简单：全程只做「转义 + 拼固定标签」，不接受任何原始 HTML。
 * 行内的 $...$ 公式不在这里处理——由调用方按需动态加载 KaTeX（见 htmlMath.ts）。
 */
export function renderStemCore(source: string): string {
  if (!source) return ''

  const out: string[] = []
  // 先按 ``` 围栏切分：围栏内一律当代码，不再做行内替换
  const parts = source.split(/```/)

  parts.forEach((part, idx) => {
    if (idx % 2 === 1) {
      // 奇数段是代码围栏内容；去掉开头的语言标记行
      const body = part.replace(/^[A-Za-z0-9+#-]*\n/, '')
      out.push(`<pre><code>${escapeHtml(body.replace(/\n$/, ''))}</code></pre>`)
      return
    }

    // 普通段落：转义后处理行内代码、加粗、换行
    let html = escapeHtml(part)

    // 行内代码先占位，避免里面的 ** 被当成加粗
    const codes: string[] = []
    html = html.replace(/`([^`\n]+)`/g, (_m, code: string) => {
      codes.push(`<code>${code}</code>`)
      return `\u0000${codes.length - 1}\u0000`
    })

    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    html = html.replace(/\n/g, '<br />')

    html = html.replace(/\u0000(\d+)\u0000/g, (_m, i: string) => codes[Number(i)] ?? '')
    out.push(html)
  })

  return out.join('')
}

/**
 * 文本里是否可能出现公式。
 *
 * 放在这里而不是 htmlMath：调用方要先用它判断「要不要动态加载 KaTeX」，
 * 如果这个判断本身来自 htmlMath，静态 import 就会把 KaTeX 又拖回首屏。
 */
export function containsMath(text: string): boolean {
  return text.includes('$') || text.includes('\\(') || text.includes('\\[')
}