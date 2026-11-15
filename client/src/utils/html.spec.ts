import { describe, expect, it } from 'vitest'
import {
  escapeHtml,
  renderFormulaBlock,
  renderMarkdown,
  renderTextWithMath,
  sanitizeHtmlBlock,
} from './html'

/**
 * 渲染链路的回归测试。
 *
 * 这一组用例锁住的不是「渲染好看」，而是**安全不变式**：
 *   1. 来自内容里的标签/事件属性不得穿过消毒器；
 *   2. 公式仍然渲染成 KaTeX（消毒没有把公式吃掉）；
 *   3. 纯文本字段里的 `<` 是内容，不是标签。
 *
 * 之所以要专门写测试：这三条以前靠「每个调用点都记得消毒」维持，
 * 而 callout 与 HTML 块两处已经漏了——漏掉的时候页面看起来完全正常。
 */

describe('escapeHtml', () => {
  it('转义全部 HTML 特殊字符', () => {
    expect(escapeHtml('<b>&"\'')).toBe('&lt;b&gt;&amp;&quot;&#039;')
  })
})

describe('renderMarkdown', () => {
  it('渲染 $...$ 为 KaTeX，且公式内容不被吞掉', () => {
    const html = renderMarkdown('时间复杂度的瓶颈是 $O(n\\log n)$')
    expect(html).toContain('katex')
    expect(html).not.toContain('$O(n')
  })

  it('渲染 $$...$$ 为块级公式', () => {
    const html = renderMarkdown('$$T(n) = 2T(n/2) + O(n)$$')
    expect(html).toContain('katex')
    expect(html).toContain('katex-display')
  })

  it('剥掉 script 标签与内联事件', () => {
    const html = renderMarkdown('<script>alert(1)</script>\n\n<img src=x onerror="alert(1)">')
    expect(html.toLowerCase()).not.toContain('<script')
    expect(html.toLowerCase()).not.toContain('onerror')
    expect(html).not.toContain('alert(1)</script>')
  })

  it('禁用 javascript: 链接与 style/id 属性', () => {
    const html = renderMarkdown('[点我](javascript:alert(1))<div id="x" style="position:fixed">t</div>')
    expect(html.toLowerCase()).not.toContain('javascript:')
    expect(html).not.toContain('position:fixed')
    expect(html).not.toContain('id="x"')
  })

  it('保留普通文本里的比较符号（不被当标签解析）', () => {
    const html = renderMarkdown('当 cwnd < ssthresh 时慢开始')
    expect(html).toContain('&lt;')
    expect(html).not.toContain('< ssthresh')
  })
})

describe('renderTextWithMath（callout 纯文本字段）', () => {
  it('先转义再渲染公式：裸 < 不会变成标签', () => {
    const html = renderTextWithMath('当 low <= high 且 $i < n$ 时继续')
    expect(html).toContain('&lt;=')
    // 公式部分应已变成 KaTeX，而不是原样残留
    expect(html).toContain('katex')
    expect(html).not.toContain('$i < n$')
  })

  it('注入尝试被转义为文本', () => {
    const html = renderTextWithMath('<img src=x onerror=alert(1)>')
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;img')
  })
})

describe('sanitizeHtmlBlock（HTML 型知识块）', () => {
  it('保留内嵌 SVG（知识块确实用 SVG 画图）', () => {
    const html = sanitizeHtmlBlock('<svg viewBox="0 0 10 10"><circle cx="5" cy="5" r="4" /></svg>')
    expect(html).toContain('<svg')
    expect(html).toContain('<circle')
  })

  it('剥掉 script 与事件属性', () => {
    const html = sanitizeHtmlBlock(
      '<div><script>alert(1)</script><svg onload="alert(1)"><rect /></svg></div>',
    )
    expect(html.toLowerCase()).not.toContain('<script')
    expect(html.toLowerCase()).not.toContain('onload')
  })
})

describe('renderFormulaBlock', () => {
  it('渲染独立公式块', () => {
    expect(renderFormulaBlock('E = mc^2')).toContain('katex')
  })

  it('非法公式降级为 KaTeX 的 katex-error 标记，而不是抛异常或渲染成空白', () => {
    // KaTeX 配置了 throwOnError:false，所以它不抛异常，而是自己输出 katex-error 标记。
    // 换句话说：题目里写错公式不会让整页崩掉，只会让这一处显示成红色原式。
    const html = renderFormulaBlock('\\frac{1}')
    expect(html).toContain('katex-error')
    expect(html).not.toContain('k-math-error')
  })
})
