/**
 * 全量核对：知识点的 questionCount 与刷题队列题数是否一致，
 * 并与数据库的「按 point_id 去重」真值比对。
 */
const BASE = 'http://127.0.0.1:8080/api/v1'

const login = await fetch(`${BASE}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'tester01', password: 'test123456' }),
}).then((r) => r.json())
const token = login.data.token
const auth = { Authorization: `Bearer ${token}` }

// 真值：按 point_id 关联去重
const { createConnection } = await import('mysql2/promise')
const connection = await createConnection({
  host: '127.0.0.1',
  port: 3306,
  user: 'root',
  password: '123456',
  database: 'kaoyan_db',
  charset: 'utf8mb4',
})
const [truth] = await connection.query(`
  SELECT p.point_id, COUNT(DISTINCT qk.question_id) AS total
  FROM knowledge_point p
  JOIN knowledge_block b ON b.point_id = p.point_id
  JOIN question_knowledge qk ON qk.block_id = b.block_id
  GROUP BY p.point_id
`)
const truthMap = new Map(truth.map((r) => [r.point_id, Number(r.total)]))
const [allPoints] = await connection.query('SELECT point_id FROM knowledge_point')
await connection.end()

const withQuestions = [...truthMap.keys()]
console.log(`知识点总数: ${allPoints.length}，有真题关联的: ${withQuestions.length}`)

let checked = 0
const problems = []
for (const pointId of withQuestions) {
  const expected = truthMap.get(pointId)
  const [knowledge, queue] = await Promise.all([
    fetch(`${BASE}/knowledge/point/${pointId}`).then((r) => r.json()),
    fetch(`${BASE}/practice/queue?pointId=${pointId}`, { headers: auth }).then((r) => r.json()),
  ])
  checked++
  const kCount = knowledge.data?.questionCount
  const qCount = queue.data?.total
  if (kCount !== expected || qCount !== expected) {
    problems.push({ pointId, expected, kCount, qCount })
  }
}

console.log(`已核对 ${checked} 个知识点\n`)
if (problems.length === 0) {
  console.log('✅ 全部一致：questionCount 与队列题数均等于数据库去重真值')
} else {
  console.log(`❌ ${problems.length} 个不一致:`)
  for (const p of problems.slice(0, 20)) {
    console.log(`   ${p.pointId}: 真值=${p.expected} 正文页=${p.kCount} 队列=${p.qCount}`)
  }
}

// 无关联题的知识点也应返回 0 而不是报错
const noQ = allPoints.map((p) => p.point_id).filter((id) => !truthMap.has(id))
if (noQ.length) {
  const sample = noQ[0]
  const k = await fetch(`${BASE}/knowledge/point/${sample}`).then((r) => r.json())
  const q = await fetch(`${BASE}/practice/queue?pointId=${sample}`, { headers: auth }).then((r) => r.json())
  console.log(
    `\n无关联题的知识点（${noQ.length} 个）抽查 ${sample}: questionCount=${k.data?.questionCount} 队列=${q.data?.total}`,
  )
}