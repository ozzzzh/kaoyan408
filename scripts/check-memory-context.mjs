/** 查看 memory/due 卡片字段与可选上下文数据（关联真题、章节）。 */
const BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1'
const USER = {
  username: process.env.TEST_USERNAME ?? 'tester01',
  password: process.env.TEST_PASSWORD ?? 'test123456',
}

const login = await (
  await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(USER),
  })
).json()
if (!login.data?.token) {
  console.log('登录失败:', login.msg)
  process.exit(0)
}
const auth = { Authorization: `Bearer ${login.data.token}` }

const due = await (await fetch(`${BASE}/memory/due`, { headers: auth })).json()
const card = due.data.items[0]
if (!card) {
  console.log('无到期卡片')
  process.exit(0)
}
console.log('=== 卡片现有字段 ===')
for (const [k, v] of Object.entries(card)) {
  console.log(`  ${k} = ${String(v).slice(0, 70)}`)
}

console.log('\n=== 该知识点是否有可关联的上下文 ===')
const point = await (await fetch(`${BASE}/knowledge/point/${card.pointId}`)).json()
if (point.code === 0) {
  console.log(`  知识点标题: ${point.data.title}`)
  console.log(`  科目: ${point.data.subject}`)
  console.log(`  该知识点题目数: ${point.data.questionCount}`)
  console.log(`  知识块数: ${point.data.blocks?.length ?? 0}`)
}

console.log('\n=== 关联真题（按知识点查练习队列）===')
const q = await (
  await fetch(`${BASE}/practice/queue?pointId=${encodeURIComponent(card.pointId)}`, { headers: auth })
).json()
console.log(`  该知识点题目数: ${q.data?.total ?? 0}`)
if (q.data?.items?.length) {
  console.log('  前 3 道:')
  for (const item of q.data.items.slice(0, 3)) {
    console.log(`    ${item.year} 年第 ${item.number} 题 (${item.examRef})`)
  }
}