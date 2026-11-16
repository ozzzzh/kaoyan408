/**
 * HTML 转义。
 *
 * 单独一个模块（不带任何依赖）是刻意的：刷题页只需要转义与「轻量 Markdown」，
 * 不应该为了一个 escape 把 marked / KaTeX 之类的东西拉进首屏包。
 */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
