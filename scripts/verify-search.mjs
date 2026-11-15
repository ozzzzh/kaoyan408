/**
 * 搜索回归：覆盖中英文关键词、题号查询、相关性排序与片段质量。
 * 需要后端已启动（默认 127.0.0.1:8080）。
 */
const BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1'

const search = async (q) => (await fetch(`${BASE}/search?q=${encodeURIComponent(q)}`)).json()
const suggest = async (q) =>
  (await fetch(`${BASE}/search/suggest?q=${encodeURIComponent(q)}`)).json()

let pass = 0
let fail = 0
const problems = []

function check(ok, label) {
  if (ok) {
    pass++
  } else {
    fail++
    problems.push(label)
  }
}

// --- 1) 关键词搜索应命中预期内容 ---
const expectedPoints = {
  KMP: ['KMP 算法'],
  二叉树: ['二叉树的概念与性质', '二叉树的存储'],
  死锁: ['死锁'],
  银行家算法: ['死锁'],
  子网掩码: ['子网掩码与子网划分'],
  cache: ['Cache 的基本概念'],
}

for (const [q, titles] of Object.entries(expectedPoints)) {
  const r = await search(q)
  const got = r.data.points.map((p) => p.title)
  for (const title of titles) {
    check(got.includes(title), `「${q}」应命中知识点「${title}」，实际: ${got.join('/') || '(无)'}`)
  }
}

// --- 2) 相关性：标题命中必须排在正文命中之前 ---
for (const q of ['KMP', 'CRC', '死锁']) {
  const r = await search(q)
  const scores = r.data.points.map((p) => p.score)
  const sorted = [...scores].sort((a, b) => b - a)
  check(
    JSON.stringify(scores) === JSON.stringify(sorted),
    `「${q}」知识点结果未按 score 降序: ${scores.join(',')}`,
  )
}

// --- 3) 题号查询：多种写法都要精确定位到同一题 ---
const refWays = ['2026 3', '2026年第3题', 'exam-2026-3', '2026-3']
for (const q of refWays) {
  const r = await search(q)
  const list = r.data.questions
  check(
    list.length === 1 && list[0].examRef === 'exam-2026-3',
    `题号查询「${q}」应唯一命中 exam-2026-3，实际 ${list.length} 条`,
  )
}

// --- 4) 片段质量：不得泄漏 JSON 外壳 ---
for (const q of ['KMP', '二叉树', '死锁', '银行家算法', '子网掩码', 'CRC', 'cache']) {
  const r = await search(q)
  const leaked = r.data.blocks.filter(
    (b) =>
      b.snippet.includes('"type"') ||
      b.snippet.includes('"text"') ||
      b.snippet.includes('content_json') ||
      b.snippet.includes('{"id"'),
  )
  check(leaked.length === 0, `「${q}」有 ${leaked.length} 条片段泄漏 JSON 外壳`)
}

// --- 5) 正文排除 image/html 块（内嵌 SVG/base64 会产生无关命中）---
const kmp = await search('KMP')
check(
  kmp.data.points.every((p) => p.title.includes('KMP')),
  `「KMP」知识点结果含无关项: ${kmp.data.points.map((p) => p.title).join('/')}`,
)

// --- 6) 空查询与无结果查询 ---
const empty = await search('')
check(empty.data.total === 0, '空查询应返回 0 条')
const none = await search('zzzz-不存在的词-9999')
check(none.code === 0, '无结果查询应正常返回而非报错')

// --- 7) 联想接口 ---
const s1 = await suggest('KMP')
check(s1.data.length > 0 && s1.data[0].to.startsWith('/learn/'), '「KMP」联想应有知识点且指向 /learn/')
const s2 = await suggest('2026 3')
check(
  s2.data.length === 1 && s2.data[0].to === '/practice?examRef=exam-2026-3',
  '「2026 3」联想应指向该题',
)
const s3 = await suggest('')
check(s3.data.length === 0, '空联想应返回空数组')

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
if (problems.length) {
  console.log('\n失败明细:')
  for (const p of problems) console.log(`  ✗ ${p}`)
  process.exitCode = 1
} else {
  console.log('✅ 搜索回归全部通过')
}