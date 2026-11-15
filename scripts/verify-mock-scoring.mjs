// 精确验证判分：先用数据库里真实的正确答案答题，确认能得到满分逻辑
import { connect } from './_env.mjs'

const B = 'http://127.0.0.1:8080/api/v1'
let pass = 0, fail = 0
const ok = (c, l, e = '') => c ? (pass++, console.log(`  ✅ ${l}${e ? ' — ' + e : ''}`)) : (fail++, console.log(`  ❌ ${l}${e ? ' — ' + e : ''}`))

const post = async (p, h, b) => (await fetch(B + p, {
  method: 'POST', headers: { ...h, ...(b ? { 'Content-Type': 'application/json' } : {}) },
  body: b ? JSON.stringify(b) : undefined,
})).json()

const main = async () => {
  const db = await connect()
  // 取 2025 年选择题的真实答案
  const [rows] = await db.query(`
    SELECT q.exam_ref, q.answer, q.score
    FROM question q JOIN exam_year e ON e.id = q.exam_id
    WHERE e.year = 2025 AND q.question_type = 'choice'
    ORDER BY q.exam_number LIMIT 5
  `)
  await db.end()

  const login = await post('/auth/login', {}, { username: 'tester01', password: 'test123456' })
  const H = { Authorization: 'Bearer ' + login.data.token }

  console.log('\n=== 用真实答案答题，验证判分 ===')
  const start = await post('/mock/start?year=2025&minutes=180', H)
  const sid = start.data.sessionId

  // 前 5 题填真实答案
  for (const r of rows) {
    await post(`/mock/${sid}/answer?examRef=${r.exam_ref}&answer=${r.answer}`, H)
  }
  const sub = await post(`/mock/${sid}/submit`, H)
  const expected = rows.reduce((s, r) => s + r.score, 0)
  ok(sub.data.autoScore === expected, '全部答对得满分', `${sub.data.autoScore} / 期望 ${expected}`)

  const judged = sub.data.items.filter(i => i.correct === 1)
  ok(judged.length === rows.length, '判为正确的题数吻合', `${judged.length}/${rows.length}`)

  // 再把答案改错并重开一场验证 0 分
  console.log('\n=== 用错误答案，验证得 0 分 ===')
  const start2 = await post('/mock/start?year=2026&minutes=180', H)
  const sid2 = start2.data.sessionId
  const first = start2.data.items.find(i => i.questionType === 'choice')
  const wrong = first.options.map(o => o.key).find(k => k !== 'A') // 先随便填
  await post(`/mock/${sid2}/answer?examRef=${first.id}&answer=${wrong}`, H)
  const sub2 = await post(`/mock/${sid2}/submit`, H)
  const firstJudged = sub2.data.items.find(i => i.id === first.id)
  ok(
    (firstJudged.correct === 1) === (wrong === firstJudged.standardAnswer),
    '判定结果与标准答案一致',
    `填 ${wrong}，标准 ${firstJudged.standardAnswer}，判 ${firstJudged.correct}`
  )

  // 清理本次测试产生的场次
  const db2 = await connect()
  await db2.query('DELETE FROM mock_answer WHERE session_id IN (?, ?)', [sid, sid2])
  await db2.query('DELETE FROM mock_session WHERE id IN (?, ?)', [sid, sid2])
  await db2.end()

  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => { console.error('异常:', e.message); process.exit(1) })