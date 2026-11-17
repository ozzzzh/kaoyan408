import { escapeHtml } from './htmlEscape'

/**
 * 题干 / AI 回答的轻量渲染。
 *
 * 为什么不用 marked：这两处主要需要「代码块 / 行内代码 / 加粗 / 换行」，
 * 加上 GFM 表格，而完整的 marked 会把 300KB+ 的解析链路拉进首屏。
 *
 * 安全模型很简单：全程只做「转义 + 拼固定标签」，不接受任何原始 HTML。
 * 行内的 $...$ 公式不在这里处理——由调用方按需动态加载 KaTeX（见 htmlMath.ts）。
 */

/** 行内标记：加粗与行内代码。输入必须已经过 escapeHtml。 */
function renderInline(text: string): string {
  // 行内代码先占位，避免里面的 ** 被当成加粗
  const codes: string[] = []
  let html = text.replace(/`([^`\n]+)`/g, (_m, code: string) => {
    codes.push(`<code>${code}</code>`)
    return `\u0000${codes.length - 1}\u0000`
  })
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\u0000(\d+)\u0000/g, (_m, i: string) => codes[Number(i)] ?? '')
  return html
}

/**
 * 表格分隔行判定：每个单元格都必须是 `:?-+:?` 且整行含竖线。
 *
 * 要求带竖线是必要的——否则段落后面的一行 `---`（setext 标题）会被误判成表格。
 * 减号只要求 1 个起（GFM 允许 `:-:` 这种写法，实测模型经常这么输出）。
 */
function isDelimiterRow(line: string): boolean {
  if (!line.includes('|')) return false
  const cells = splitCells(line)
  return cells.length > 0 && cells.every((cell) => /^:?-+:?$/.test(cell))
}

/** 把一行拆成单元格（去掉首尾竖线两侧的空单元）。 */
function splitCells(line: string): string[] {
  const trimmed = line.trim().replace(/^\|/, '').replace(/\|$/, '')
  return trimmed.split('|').map((cell) => cell.trim())
}

/** 由分隔行推断每列对齐方式。 */
function alignments(delimiter: string): string[] {
  return splitCells(delimiter).map((cell) => {
    const left = cell.startsWith(':')
    const right = cell.endsWith(':')
    if (left && right) return 'center'
    if (right) return 'right'
    return 'left'
  })
}

function renderTable(header: string, delimiter: string, rows: string[]): string {
  const aligns = alignments(delimiter)
  const cell = (tag: 'th' | 'td', text: string, index: number) => {
    const align = aligns[index] ?? 'left'
    return `<${tag} class="ta-${align}">${renderInline(text)}</${tag}>`
  }
  const head = splitCells(header)
    .map((text, index) => cell('th', text, index))
    .join('')
  const body = rows
    .map((row) => `<tr>${splitCells(row).map((text, index) => cell('td', text, index)).join('')}</tr>`)
    .join('')
  return `<table class="stem-table"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`
}

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

    // 普通段落：先整体转义，再逐行处理，识别出表格块单独渲染
    const lines = escapeHtml(part).split('\n')
    const buffer: string[] = []
    const flushParagraph = () => {
      if (!buffer.length) return
      out.push(buffer.map((line) => renderInline(line)).join('<br />'))
      buffer.length = 0
    }

    for (let i = 0; i < lines.length; i += 1) {
      const line = lines[i]
      const next = lines[i + 1]
      const looksLikeTable = line.includes('|') && next !== undefined && isDelimiterRow(next)

      if (looksLikeTable) {
        flushParagraph()
        const rows: string[] = []
        let cursor = i + 2
        while (cursor < lines.length && lines[cursor].includes('|') && lines[cursor].trim() !== '') {
          rows.push(lines[cursor])
          cursor += 1
        }
        out.push(renderTable(line, next, rows))
        i = cursor - 1
        continue
      }

      buffer.push(line)
    }
    flushParagraph()
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
