#!/usr/bin/env node
// 游客模式回归：能练不能存
const B = 'http://127.0.0.1:8080/api/v1'
let pass = 0, fail = 0
const ok = (c, l, e = '') => c ? (pass++, console.log(`  ✅ ${l}${e ? ' — ' + e : ''}`)) : (fail++, console.log(`  ❌ ${l}${e ? ' — ' + e : ''}`))

const post = async (p, b) => {
  const r = await fetch(B + p, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(b ?? {}),
  })
  return r.json()
}
const get = async (p) => (await fetch(B + p)).json()

const main = async () => {
  console.log('\n=== 1. 游客答题：应能判分 ===')
  // 取一道真题
  const exams = await get('/exams')
  const year = exams.data[exams.data.length - 1].year
  const detail = await get(`/exams/${year}`)
  const firstChoice = detail.data.questions.find(q => q.questionType === 'choice')
  ok(firstChoice != null, '取到一道选择题', firstChoice?.id)

  const ans = await post(`/questions/${firstChoice.id}/answer`, { answer: 'A' })
  ok(ans.code === 0, '游客可以提交答案', 'code=' + ans.code)
  ok(ans.data.guest === true, '响应标记为游客结果')
  ok(ans.data.saved === false, '响应明确表示未保存')
  ok(typeof ans.data.correct === 'boolean', '返回了对错', String(ans.data.correct))
  ok(ans.data.standardAnswer != null, '返回了标准答案', String(ans.data.standardAnswer))
  ok(Array.isArray(ans.data.blocks) && ans.data.blocks.length === 0, '不返回知识点掌握（游客无记录）')

  console.log('\n=== 2. 游客答题不写入数据库 ===')
  const { connect } = await import('file:///D:/agentwork/项目2/408kaoyan/scripts/_env.mjs')
  const db = await connect()
  const [before] = await db.query('SELECT COUNT(*) AS n FROM question_attempt')
  await post(`/questions/${firstChoice.id}/answer`, { answer: 'B' })
  const [after] = await db.query('SELECT COUNT(*) AS n FROM question_attempt')
  ok(before[0].n === after[0].n, '作答前后记录数不变', `${before[0].n} -> ${after[0].n}`)

  console.log('\n=== 3. 游客开考：应能开 ===')
  const start = await post(`/mock/start?year=${year}&minutes=180`)
  ok(start.code === 0, '游客可以开考', 'code=' + start.code)
  ok(start.data.sessionId < 0, '游客会话 id 为负数（内存态）', String(start.data.sessionId))
  ok(start.data.items.length === 47, '拿到 47 题', String(start.data.items.length))

  const sid = start.data.sessionId
  const choice = start.data.items.filter(i => i.questionType === 'choice')

  console.log('\n=== 4. 游客答题并交卷 ===')
  await post(`/mock/${sid}/answer?examRef=${choice[0].id}&answer=D`, {})
  const st = await get(`/mock/${sid}`)
  const answered = st.data.items.filter(i => i.myAnswer != null)
  ok(answered.length === 1, '作答已保存到内存', String(answered.length))

  const sub = await post(`/mock/${sid}/submit`, {})
  ok(sub.code === 0, '游客可以交卷', 'code=' + sub.code)
  ok(sub.data.status === 'submitted', '状态已交卷')
  ok(typeof sub.data.autoScore === 'number', '算出了分数', String(sub.data.autoScore))

  console.log('\n=== 5. 游客交卷不写库 ===')
  const [ms] = await db.query('SELECT COUNT(*) AS n FROM mock_session WHERE id = ?', [sid])
  ok(ms[0].n === 0, '数据库里没有这场考试', String(ms[0].n))

  console.log('\n=== 6. 游客不能看历史成绩 ===')
  const hist = await get('/mock/history')
  ok(hist.code === 401, '历史成绩要求登录', 'code=' + hist.code)

  console.log('\n=== 7. 背诵仍需登录 ===')
  const mem = await get('/memory/summary')
  ok(mem.code === 401, '背诵卡片要求登录', 'code=' + mem.code)

  console.log('\n=== 8. 游客会话过期后提示重新开始 ===')
  const ghost = await get('/mock/-999999')
  ok(ghost.code === 404, '不存在的游客场次返回 404', 'code=' + ghost.code)

  await db.end()
  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => { console.error('异常:', e.message); process.exit(1) })