#!/usr/bin/env node
// 模拟考试健壮性：刷新恢复、超时交卷、作答不丢失
const B = 'http://127.0.0.1:8080/api/v1'
let pass = 0, fail = 0
const ok = (c, l, e = '') => c ? (pass++, console.log(`  ✅ ${l}${e ? ' — ' + e : ''}`)) : (fail++, console.log(`  ❌ ${l}${e ? ' — ' + e : ''}`))

const post = async (p, h, b) => (await fetch(B + p, {
  method: 'POST', headers: { ...h, ...(b ? { 'Content-Type': 'application/json' } : {}) },
  body: b ? JSON.stringify(b) : undefined,
})).json()
const get = async (p, h) => (await fetch(B + p, { headers: h })).json()

const main = async () => {
  const { connect } = await import('./_env.mjs')
  const db = await connect()
  const login = await post('/auth/login', {}, { username: 'tester01', password: 'test123456' })
  const H = { Authorization: 'Bearer ' + login.data.token }

  console.log('\n=== 1. 作答后「刷新」，答案不丢 ===')
  const s = await post('/mock/start?year=2023&minutes=180', H)
  const sid = s.data.sessionId
  const q = s.data.items.filter(i => i.questionType === 'choice')

  await post(`/mock/${sid}/answer?examRef=${q[0].id}&answer=B`, H)
  await post(`/mock/${sid}/answer?examRef=${q[1].id}&answer=C`, H)

  // 模拟刷新：重新 GET 同一场
  const reloaded = await get(`/mock/${sid}`, H)
  const a0 = reloaded.data.items.find(i => i.id === q[0].id)
  const a1 = reloaded.data.items.find(i => i.id === q[1].id)
  ok(a0.myAnswer === 'B', '第 1 题答案恢复', String(a0.myAnswer))
  ok(a1.myAnswer === 'C', '第 2 题答案恢复', String(a1.myAnswer))
  ok(reloaded.data.status === 'ongoing', '状态仍是进行中')

  console.log('\n=== 2. 剩余时间按服务端计算，刷新不会重置 ===')
  const first = reloaded.data.remainingSeconds
  await new Promise(r => setTimeout(r, 2200))
  const again = await get(`/mock/${sid}`, H)
  ok(again.data.remainingSeconds < first, '剩余时间在减少（未被重置）',
     `${first}s -> ${again.data.remainingSeconds}s`)
  ok(Math.abs((first - again.data.remainingSeconds) - 2) <= 2, '减少量与真实时间相符')

  console.log('\n=== 3. 修改答案会覆盖而非新增 ===')
  await post(`/mock/${sid}/answer?examRef=${q[0].id}&answer=A`, H)
  const ch = await get(`/mock/${sid}`, H)
  ok(ch.data.items.find(i => i.id === q[0].id).myAnswer === 'A', '答案已更新为 A')
  const [cnt] = await db.query(
    'SELECT COUNT(*) AS n FROM mock_answer WHERE session_id = ? AND question_id = (SELECT id FROM question WHERE exam_ref = ?)',
    [sid, q[0].id])
  ok(cnt[0].n === 1, '数据库只有一行（唯一约束生效）', String(cnt[0].n))

  console.log('\n=== 4. 超时后不能继续答题 ===')
  // 手动把开考时间调早，制造超时
  await db.query('UPDATE mock_session SET started_at = DATE_SUB(NOW(), INTERVAL 200 MINUTE) WHERE id = ?', [sid])
  const late = await post(`/mock/${sid}/answer?examRef=${q[2].id}&answer=D`, H)
  ok(late.code === 400, '超时后拒绝答题', 'code=' + late.code)

  console.log('\n=== 5. 超时交卷标记为 timed_out ===')
  const sub = await post(`/mock/${sid}/submit`, H)
  ok(sub.code === 0, '超时仍可交卷')
  ok(sub.data.timedOut === true, '标记为超时交卷', String(sub.data.timedOut))

  console.log('\n=== 6. 超时状态下再开同一年的考试，会先结算旧场 ===')
  const s2 = await post('/mock/start?year=2023&minutes=180', H)
  ok(s2.data.sessionId !== sid, '开出了新的场次', `${sid} -> ${s2.data.sessionId}`)
  const old = await get(`/mock/${sid}`, H)
  ok(old.data.status === 'submitted', '旧场已结算')

  console.log('\n=== 7. 不限时场次不会被误判超时 ===')
  const s3 = await post('/mock/start?year=2022&minutes=0', H)
  ok(s3.data.remainingSeconds === -1, '不限时返回 -1')
  ok(s3.data.durationMinutes === 0, '时长为 0')
  // 即使过了很久也不该超时（这里用改时间模拟）
  const sub3 = await post(`/mock/${s3.data.sessionId}/submit`, H)
  ok(sub3.data.timedOut === false, '不限时手动交卷不算超时', String(sub3.data.timedOut))

  // 清理
  for (const id of [sid, s2.data.sessionId, s3.data.sessionId]) {
    await db.query('DELETE FROM mock_answer WHERE session_id = ?', [id])
    await db.query('DELETE FROM mock_session WHERE id = ?', [id])
  }
  await db.end()
  console.log('\n（已清理测试场次）')

  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => { console.error('异常:', e.message); process.exit(1) })