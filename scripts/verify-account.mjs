#!/usr/bin/env node
// 账号体系回归：邮箱验证码注册、校验、限流、登录锁定、禁用账号
import { connect } from './_env.mjs'

const B = 'http://127.0.0.1:8080/api/v1'
let pass = 0, fail = 0
const ok = (c, l, e = '') => c ? (pass++, console.log(`  ✅ ${l}${e ? ' — ' + e : ''}`)) : (fail++, console.log(`  ❌ ${l}${e ? ' — ' + e : ''}`))

const post = async (p, h, b) => {
  const r = await fetch(B + p, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...h },
    body: b ? JSON.stringify(b) : undefined,
  })
  return r.json()
}

const stamp = Date.now()
const EMAIL = `verify${stamp}@example.com`
const USER = `vuser${stamp % 100000}`

/** 从数据库读验证码（mock 模式等价于读服务端日志） */
async function getCode(db, email) {
  const [rows] = await db.query(
    'SELECT code FROM verify_code WHERE target = ? AND channel = ?', [email.toLowerCase(), 'email'])
  return rows.length ? rows[0].code : null
}

const main = async () => {
  const db = await connect()

  console.log('\n=== 1. 发送验证码 ===')
  const send = await post('/auth/send-mail-code', {}, { email: EMAIL })
  ok(send.code === 0, '发码成功', send.data?.message)
  ok(send.data.mock === true, '处于 mock 模式（不真发信）')
  ok(send.data.cooldownSeconds === 60, '返回 60 秒冷却', String(send.data.cooldownSeconds))

  const code = await getCode(db, EMAIL)
  ok(/^\d{6}$/.test(code ?? ''), '验证码为 6 位数字', code)

  console.log('\n=== 2. 60 秒内重复发码被拒 ===')
  const again = await post('/auth/send-mail-code', {}, { email: EMAIL })
  ok(again.code === 429, '冷却期内拒绝', 'code=' + again.code)

  console.log('\n=== 3. 错误验证码被拒 ===')
  const wrong = await post('/auth/register', {}, {
    username: USER, email: EMAIL, password: 'test123456', code: '000000' === code ? '111111' : '000000',
  })
  ok(wrong.code === 400, '错误验证码拒绝', wrong.msg)
  ok((wrong.msg ?? '').includes('还可尝试'), '提示剩余次数', wrong.msg)

  console.log('\n=== 4. 密码太短被拒（服务端校验）===')
  const shortPwd = await post('/auth/register', {}, {
    username: USER, email: EMAIL, password: '1234567', code,
  })
  ok(shortPwd.code === 400, '7 位密码被拒', shortPwd.msg)

  console.log('\n=== 5. 用户名格式校验 ===')
  const badName = await post('/auth/register', {}, {
    username: 'a', email: EMAIL, password: 'test123456', code,
  })
  ok(badName.code === 400, '1 位用户名被拒', badName.msg)

  console.log('\n=== 6. 正确验证码注册成功 ===')
  const reg = await post('/auth/register', {}, {
    username: USER, email: EMAIL, password: 'test123456', code,
  })
  ok(reg.code === 0, '注册成功', reg.data?.user?.username)
  ok(reg.data?.token != null, '返回了 token')
  ok(reg.data?.user?.email === EMAIL.toLowerCase(), '邮箱已存', reg.data?.user?.email)

  console.log('\n=== 7. 验证码一次性：重放失败 ===')
  const replay = await post('/auth/register', {}, {
    username: USER + 'x', email: EMAIL, password: 'test123456', code,
  })
  ok(replay.code === 400, '同一验证码不能重复使用', replay.msg)

  console.log('\n=== 8. 重复邮箱被拒 ===')
  await post('/auth/send-mail-code', {}, { email: EMAIL }).catch(() => {})
  // 等冷却过去太慢，直接改 created_at 绕过
  await db.query('UPDATE verify_code SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE target = ?', [EMAIL.toLowerCase()])
  const send2 = await post('/auth/send-mail-code', {}, { email: EMAIL })
  ok(send2.code === 0 || send2.code === 429, '冷却后可再发或仍被限流', 'code=' + send2.code)
  const code2 = await getCode(db, EMAIL)
  const dupEmail = await post('/auth/register', {}, {
    username: USER + 'y', email: EMAIL, password: 'test123456', code: code2,
  })
  ok(dupEmail.code === 400 && (dupEmail.msg ?? '').includes('邮箱'), '重复邮箱被拒', dupEmail.msg)

  console.log('\n=== 9. 登录失败锁定 ===')
  const lockUser = USER
  for (let i = 0; i < 5; i++) {
    await post('/auth/login', {}, { username: lockUser, password: 'wrongpass' })
  }
  const locked = await post('/auth/login', {}, { username: lockUser, password: 'wrongpass' })
  ok(locked.code === 429, '连续 5 次失败后被锁定', 'code=' + locked.code)

  const correctAfterLock = await post('/auth/login', {}, { username: lockUser, password: 'test123456' })
  ok(correctAfterLock.code === 429, '锁定期内正确密码也进不去', 'code=' + correctAfterLock.code)

  // 清掉锁定记录，继续后面的测试
  await db.query("DELETE FROM auth_rate_limit WHERE scope='target' AND key_value = ?", ['login:' + lockUser.toLowerCase()])

  console.log('\n=== 10. 正常登录 ===')
  const login = await post('/auth/login', {}, { username: lockUser, password: 'test123456' })
  ok(login.code === 0, '解锁后正常登录', login.data?.user?.username)

  console.log('\n=== 11. 禁用账号后无法使用 ===')
  await db.query('UPDATE app_user SET is_active = 0 WHERE username = ?', [lockUser])
  const disabled = await post('/auth/login', {}, { username: lockUser, password: 'test123456' })
  ok(disabled.code === 403, '禁用后登录被拒', disabled.msg)

  // 已有 token 也应失效
  const H = { Authorization: 'Bearer ' + login.data.token }
  const withOldToken = await fetch(B + '/user/current', { headers: H }).then(r => r.json())
  ok(withOldToken.code === 403, '已签发的 token 也失效', 'code=' + withOldToken.code)

  // 清理
  await db.query('DELETE FROM verify_code WHERE target = ?', [EMAIL.toLowerCase()])
  await db.query('DELETE FROM app_user WHERE username LIKE ?', [`vuser${stamp % 100000}%`])
  await db.query("DELETE FROM auth_rate_limit WHERE key_value LIKE ?", [`login:vuser${stamp % 100000}%`])
  await db.end()
  console.log('\n（已清理测试账号）')

  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => { console.error('异常:', e.message, e.stack?.split('\n')[1]); process.exit(1) })