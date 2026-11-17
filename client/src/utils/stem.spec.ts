import { describe, expect, it } from 'vitest'
import { containsMath, renderStemCore } from './stem'

/**
 * 题干轻量渲染的回归测试。
 *
 * 这层替换掉的是完整的 marked 链路，所以必须保证两件事：
 *   1. 题库里真实出现的写法（代码块、行内代码、加粗、换行）仍然渲染成对应标签；
 *   2. 任何原始 HTML 都被转义，不会因为「省掉 DOMPurify」而留下注入口。
 */
describe('renderStemCore', () => {
  it('行内代码与加粗', () => {
    const html = renderStemCore('判断 `for(i=0;i<n;i++)` 的**时间复杂度**')
    expect(html).toContain('<code>for(i=0;i&lt;n;i++)</code>')
    expect(html).toContain('<strong>时间复杂度</strong>')
  })

  it('代码块保留内容并转义尖括号', () => {
    const html = renderStemCore('```c\nif (a < b) { return a; }\n```')
    expect(html).toContain('<pre><code>')
    expect(html).toContain('a &lt; b')
    expect(html).not.toContain('<b)')
  })

  it('换行转成 br，不产生额外块级标签', () => {
    expect(renderStemCore('第一行\n第二行')).toBe('第一行<br />第二行')
  })

  it('原始 HTML 一律转义（不依赖 DOMPurify 也安全）', () => {
    const html = renderStemCore('<img src=x onerror=alert(1)><script>alert(1)</script>')
    expect(html).not.toContain('<img')
    expect(html).not.toContain('<script')
    expect(html).toContain('&lt;img')
  })

  it('行内代码里的 ** 不会被误判成加粗', () => {
    const html = renderStemCore('`a**b` 与 **c**')
    expect(html).toContain('<code>a**b</code>')
    expect(html).toContain('<strong>c</strong>')
  })

  it('GFM 表格渲染成 table（AI 回答里的排期表依赖它）', () => {
    const html = renderStemCore('| 天 | 内容 |\n| --- | --- |\n| 1 | 线性表 |\n| 2 | 树 |')
    expect(html).toContain('<table class="stem-table">')
    expect(html).toContain('<th class="ta-left">天</th>')
    expect(html).toContain('<td class="ta-left">线性表</td>')
    // 表格行不能被拼成一行带 <br /> 的纯文本
    expect(html).not.toContain('| --- |')
  })

  it('表格对齐标记生效，且单元格内支持加粗', () => {
    const html = renderStemCore('| 左 | 中 | 右 |\n| :-- | :-: | --: |\n| a | **b** | c |')
    expect(html).toContain('class="ta-left">左')
    expect(html).toContain('class="ta-center">中')
    expect(html).toContain('class="ta-right">右')
    expect(html).toContain('<strong>b</strong>')
  })

  it('普通段落里的竖线不会误判成表格', () => {
    const html = renderStemCore('a | b 只是普通文本')
    expect(html).not.toContain('<table')
    expect(html).toContain('a | b')
  })
})

describe('containsMath', () => {
  it('识别常见的公式写法', () => {
    expect(containsMath('当 $n=9$ 时')).toBe(true)
    expect(containsMath('用 \\(O(n)\\) 表示')).toBe(true)
    expect(containsMath('\\[a^2+b^2\\]')).toBe(true)
  })

  it('纯文本不触发公式加载', () => {
    expect(containsMath('时间复杂度最高的是哪个排序')).toBe(false)
  })
})
