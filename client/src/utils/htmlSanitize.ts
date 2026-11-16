import DOMPurify from 'dompurify'

/**
 * 内容消毒。与渲染（Markdown / 公式）分开，这样只做转义的页面
 * （例如背诵卡片、题干）不必把 marked 与 KaTeX 一起打包进来。
 */

/** 正文消毒配置：不放行 class / id / style，避免内容影响页面结构与排版。 */
const CONTENT_CONFIG: Parameters<typeof DOMPurify.sanitize>[1] = {
  FORBID_TAGS: ['button', 'embed', 'form', 'iframe', 'input', 'object', 'script', 'style'],
  FORBID_ATTR: ['class', 'id', 'style'],
  USE_PROFILES: { html: true },
}

/**
 * HTML 型知识块（含内嵌 SVG 图示）的消毒配置。
 *
 * 比正文宽一档，因为这类块确实带 <svg>：html-only 配置会把整张图剥空。
 * SVG 里能执行脚本的入口（<script>、on* 属性、javascript: 链接）由 DOMPurify 处理。
 */
const HTML_BLOCK_CONFIG: Parameters<typeof DOMPurify.sanitize>[1] = {
  FORBID_TAGS: ['button', 'embed', 'form', 'iframe', 'input', 'object', 'script', 'style'],
  FORBID_ATTR: ['style', 'onerror', 'onload', 'onclick'],
  USE_PROFILES: { html: true, svg: true, svgFilters: true },
}

/** 消毒「已由我们自己生成的 HTML」；用于 Markdown 输出。 */
export function sanitizeContentHtml(html: string): string {
  // sanitize 的返回类型是 string | TrustedHTML，本项目的 v-html 需要 string。
  return String(DOMPurify.sanitize(html, CONTENT_CONFIG))
}

/** 消毒 HTML 型知识块（允许 svg）。 */
export function sanitizeHtmlBlock(html: string): string {
  return String(DOMPurify.sanitize(html, HTML_BLOCK_CONFIG))
}
