// 模拟考试后端回归：开考 → 答题 → 交卷 → 自评 → 历史
import { connect } from './_env.mjs'

const B = 'http://127.0.0.1:8080/api/v1'
let pass = 0, fail = 0

function ok(cond, label, extra = '') {
  if (cond) { pass++; console.log(`  ✅ ${label}${extra ? ' — ' + extra : ''}`) }
  else { fail++; console.log(`  ❌ ${label}${extra ? ' — ' + extra : ''}`) }
}

async function post(path, headers, body) {
  const r = await fetch(B + path, {
    method: 'POST',
    headers: { ...headers, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return r.json()
}

async function get(path, headers) {
  const r = await fetch(B + path, { headers })
  return r.json()
}

const main = async () => {
  // 登录
  const login = await post('/auth/login', {}, { username: 'tester01', password: 'test123456' })
  if (login.code !== 0) { console.log('登录失败，无法继续'); process.exit(1) }
  const H = { Authorization: 'Bearer ' + login.data.token }

  console.log('\n=== 1. 未登录也能开考（游客场次走内存）===')
  // 原先这里断言「未登录开考返回 401」。方案 D 之后开考对游客开放
  // （会话存内存、不落库），这条断言随之失效。改为验证游客能开考，
  // 且拿到的是负数 sessionId（服务端用负数区分内存态与数据库自增 id）。
  const anon = await fetch(B + '/mock/start?year=2024', { method: 'POST' })
  const anonBody = await anon.json()
  ok(anonBody.code === 0, '游客可以开考', 'code=' + anonBody.code)
  ok(anonBody.data?.sessionId < 0, '游客 sessionId 为负数', String(anonBody.data?.sessionId))

  console.log('\n=== 2. 开考 ===')
  const start = await post('/mock/start?year=2024&minutes=180', H)
  ok(start.code === 0, '开考成功')
  const s = start.data
  ok(s.items.length === 47, '题目数 47', String(s.items.length))
  // 剩余时间由 started_at 与当前时刻算出，服务端取整方向与请求耗时会让它
  // 略大于总时长（实测出现过 10801 秒）。这里留 2 秒余量，避免偶发失败。
  ok(s.remainingSeconds > 0 && s.remainingSeconds <= 180 * 60 + 2,
     '剩余时间已下发', s.remainingSeconds + 's')
  ok(s.status === 'ongoing', '状态 ongoing')

  const choice = s.items.filter(i => i.questionType === 'choice')
  const comp = s.items.filter(i => i.questionType === 'comprehensive')
  ok(choice.length === 40, '选择题 40', String(choice.length))
  ok(comp.length === 7, '综合题 7', String(comp.length))
  ok(choice[0].options.length === 4, '选择题有 4 个选项', String(choice[0].options.length))

  console.log('\n=== 3. 考试期间绝不能下发答案 ===')
  const leakedChoice = s.items.filter(i => i.standardAnswer != null || i.explanation != null)
  ok(leakedChoice.length === 0, '没有任何题目泄露答案/解析', `${leakedChoice.length} 题泄露`)

  console.log('\n=== 4. 重复开考返回同一场 ===')
  const again = await post('/mock/start?year=2024', H)
  ok(again.data.sessionId === s.sessionId, '未重复建场', `id=${again.data.sessionId}`)

  console.log('\n=== 5. 答题保存 ===')
  // 答对 3 题、答错 1 题、留空其余
  const correctKeys = ['A', 'B', 'C']
  for (let i = 0; i < choice.length; i++) {
    if (i < 3) {
      await post(`/mock/${s.sessionId}/answer?examRef=${choice[i].id}&answer=${correctKeys[i]}`, H)
    } else if (i === 3) {
      await post(`/mock/${s.sessionId}/answer?examRef=${choice[i].id}&answer=Z`, H)
    }
  }
  const mid = await get(`/mock/${s.sessionId}`, H)
  const answered = mid.data.items.filter(i => i.myAnswer != null)
  ok(answered.length === 4, '已保存 4 题', String(answered.length))

  console.log('\n=== 6. 交卷判分 ===')
  const sub = await post(`/mock/${s.sessionId}/submit`, H)
  ok(sub.code === 0, '交卷成功')
  const d = sub.data
  ok(d.status === 'submitted', '状态已变 submitted')
  ok(typeof d.autoScore === 'number', '自动分已算出', String(d.autoScore) + '/80')
  ok(d.remainingSeconds === 0, '交卷后剩余时间归零')

  console.log('\n=== 7. 交卷后才下发答案 ===')
  const withAns = d.items.filter(i => i.standardAnswer != null)
  ok(withAns.length === 47, '全部题目下发标准答案', `${withAns.length}/47`)
  const judged = d.items.filter(i => i.correct != null)
  ok(judged.length === 4, '仅已作答的选择题有判定结果', String(judged.length))

  console.log('\n=== 8. 综合题自评 ===')
  const compQ = d.items.find(i => i.questionType === 'comprehensive')
  ok(compQ != null && compQ.score > 0, '综合题有满分', compQ ? String(compQ.score) : '无')
  const g = await post(`/mock/${s.sessionId}/grade?examRef=${compQ.id}&score=${compQ.score}`, H)
  ok(g.code === 0, '自评提交成功')
  if (g.code === 0) {
    ok(g.data.selfScore === compQ.score, '自评分已记录', String(g.data.selfScore))
    const after = await get(`/mock/${s.sessionId}`, H)
    ok(after.data.selfScore === g.data.sessionSelfScore, '会话自评总分同步', String(after.data.selfScore))
  }

  console.log('\n=== 9. 自评超上限应被截断 ===')
  const over = await post(`/mock/${s.sessionId}/grade?examRef=${compQ.id}&score=999`, H)
  ok(over.code === 0 && over.data.selfScore === compQ.score, '超上限被夹到满分', String(over.data.selfScore))

  console.log('\n=== 10. 交卷后不能改选择题 ===')
  const locked = await post(`/mock/${s.sessionId}/answer?examRef=${choice[0].id}&answer=A`, H)
  ok(locked.code === 400, '已交卷拒绝修改选择题', 'code=' + locked.code)

  console.log('\n=== 11. 历史成绩 ===')
  const hist = await get('/mock/history', H)
  ok(hist.code === 0, '历史接口可用')
  ok(hist.data.items.length >= 1, '至少 1 条记录', String(hist.data.items.length))
  const h0 = hist.data.items[0]
  ok(h0.sessionId === s.sessionId, '最新一条就是本场')
  ok(h0.autoScore === d.autoScore, '历史里的分数与交卷一致', String(h0.autoScore))

  console.log('\n=== 12. 越权访问他人场次 ===')
  // 注册现在需要邮箱验证码，用「换个用户名重新注册」的老办法会静默跳过，
  // 导致这个用例悄悄失效。改为复用 tester01 的密码哈希建一个临时用户，验证完即删。
  // 不硬编码 bcrypt 串：哈希是随机的，写死一个就等于把测试绑在某台机器的数据上。
  const otherName = 'mockother' + Date.now()
  const db = await connect()
  const [donor] = await db.query(
    'SELECT password_hash FROM app_user WHERE username = ? LIMIT 1', ['tester01'])
  if (donor.length === 0) {
    ok(false, '找不到 tester01，无法做越权测试')
  } else {
    await db.query(
      `INSERT INTO app_user (username, email, password_hash, is_active, is_admin)
       VALUES (?, ?, ?, 1, 0)`,
      [otherName, `m${Date.now()}@example.com`, donor[0].password_hash],
    )
    const login2 = await post('/auth/login', {}, { username: otherName, password: 'test123456' })
    ok(login2.code === 0, '第二个账号登录成功（用于越权测试）', 'code=' + login2.code)

    if (login2.code === 0) {
      const H2 = { Authorization: 'Bearer ' + login2.data.token }
      const steal = await get(`/mock/${s.sessionId}`, H2)
      ok(steal.code === 404, '他人场次不可见', 'code=' + steal.code)

      const steal2 = await post(`/mock/${s.sessionId}/submit`, H2)
      ok(steal2.code === 404, '他人不能被代交卷', 'code=' + steal2.code)
    }
    await db.query('DELETE FROM app_user WHERE username = ?', [otherName])
  }

  // 清理本场考试。原先不清理，重复运行后库里堆满 submitted 记录，
  // 让「最新一条就是本场」这类断言依赖历史数据而偶发失败。
  await db.query('DELETE FROM mock_answer WHERE session_id = ?', [s.sessionId])
  await db.query('DELETE FROM mock_session WHERE id = ?', [s.sessionId])
  await db.end()

  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => { console.error('异常:', e.message); process.exit(1) })