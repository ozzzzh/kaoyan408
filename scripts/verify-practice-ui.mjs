/**
 * 刷题页改造的接口侧回归：
 *   1) knowledgeBlocks 字段（可读名称 + pointId）
 *   2) 选项/题干数据未被破坏
 *   3) 提交答案后返回 standardAnswer / explanation
 *   4) 队列的 year / number 足以支撑按年份分组导航
 */
const BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1'
const USER = {
  username: process.env.TEST_USERNAME ?? 'tester01',
  password: process.env.TEST_PASSWORD ?? 'test123456',
}

let pass = 0
let fail = 0
function check(label, ok, detail = '') {
  if (ok) {
    pass += 1
    console.log(`  ✅ ${label}${detail ? ` — ${detail}` : ''}`)
  } else {
    fail += 1
    console.log(`  ❌ ${label}${detail ? ` — ${detail}` : ''}`)
  }
}

const login = await (
  await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(USER),
  })
).json()
const auth = { Authorization: `Bearer ${login.data.token}`, 'Content-Type': 'application/json' }

console.log('【1】题目详情：knowledgeBlocks')
const detail = await (await fetch(`${BASE}/questions/exam-2026-1`, { headers: auth })).json()
const blocks = detail.data.knowledgeBlocks ?? []
check('knowledgeBlocks 存在且非空', blocks.length > 0, `${blocks.length} 项`)
check(
  'label 是可读中文、不是裸 blockId',
  blocks.every((b) => b.label && b.label !== b.blockId && /[\u4e00-\u9fa5]/.test(b.label)),
  blocks[0]?.label,
)
check(
  '每项都带 pointId（供跳转）',
  blocks.every((b) => typeof b.pointId === 'string' && b.pointId.length > 0),
  blocks[0]?.pointId,
)
check('旧字段 knowledgeBlockIds 仍保留', Array.isArray(detail.data.knowledgeBlockIds))

console.log('\n【2】题干与选项未被破坏')
check('stem 非空', typeof detail.data.stem === 'string' && detail.data.stem.length > 10)
check('options 有 4 个选项', detail.data.options?.length === 4)
check(
  '选项格式为 {key,text}',
  detail.data.options?.every((o) => o.key && o.text),
  detail.data.options?.map((o) => o.key).join(''),
)
// 组合选择题的罗马数字行应存在于 stem 中（前端据此拆行）
check('stem 含罗马数字选项行（供前端紧凑排版）', /^\s*I{1,3}\s*[.、．]/m.test(detail.data.stem))

console.log('\n【3】提交答案返回解析')
const submitted = await (
  await fetch(`${BASE}/questions/exam-2026-1/answer`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ answer: detail.data.options[0].key }),
  })
).json()
check('提交成功', submitted.code === 0, `msg=${submitted.msg}`)
check('返回 correct 布尔值', typeof submitted.data?.correct === 'boolean')
check('返回 standardAnswer', typeof submitted.data?.standardAnswer === 'string')
check(
  '返回 explanation 非空',
  typeof submitted.data?.explanation === 'string' && submitted.data.explanation.length > 0,
)

console.log('\n【4】队列数据支撑按年份分组')
const queue = await (await fetch(`${BASE}/practice/queue?year=2026`, { headers: auth })).json()
const items = queue.data.items ?? []
check('2026 队列非空', items.length > 0, `${items.length} 题`)
check('每项都有 year（分组依据）', items.every((i) => typeof i.year === 'number'))
check('每项都有 number（导航格显示）', items.every((i) => typeof i.number === 'number'))
check(
  '同年份 number 不重复',
  new Set(items.map((i) => i.number)).size === items.length,
  `${new Set(items.map((i) => i.number)).size} 个唯一题号`,
)
check('每项都有 examRef（作为 key）', items.every((i) => typeof i.examRef === 'string'))

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
console.log(fail === 0 ? '✅ 刷题页接口回归全部通过' : '❌ 存在失败项')
process.exit(fail === 0 ? 0 : 1)