#!/usr/bin/env node
/** AI 助手后端回归：状态、游客限额、流式对话、知识点标记 */
const B = 'http://127.0.0.1:8080/api/v1'
let pass = 0, fail = 0
const ok = (c, l, e = '') => c ? (pass++, console.log(`  ✅ ${l}${e ? ' — ' + e : ''}`)) : (fail++, console.log(`  ❌ ${l}${e ? ' — ' + e : ''}`))

const get = async (p, h = {}) => (await fetch(B + p, { headers: h })).json()

/**
 * 读 SSE 流，收集各事件。
 *
 * 关键：事件之间用空行（\n\n）分隔，一个事件可能是多行。
 * 最初版本按行切分并把 data 直接当事件处理，遇到多行事件就拼错，
 * 表现为正文乱码、thinking/done 收不到。
 */
async function chat(question, headers = {}) {
  const res = await fetch(B + '/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ question }),
  })
  if (!res.headers.get('content-type')?.includes('text/event-stream')) {
    return { error: await res.json() }
  }
  const reader = res.body.getReader()
  const dec = new TextDecoder()
  let buf = '', text = '', thinking = false, done = null, error = null
  const t0 = Date.now()
  let firstDeltaAt = 0

  /** 消费一个完整事件块 */
  const consume = (block) => {
    let evt = null
    const dataLines = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) evt = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    if (!evt || !dataLines.length) return
    let d
    try {
      d = JSON.parse(dataLines.join(''))
    } catch {
      return
    }
    if (evt === 'thinking') thinking = true
    else if (evt === 'delta') {
      if (!firstDeltaAt) firstDeltaAt = Date.now() - t0
      text += d.text ?? ''
    } else if (evt === 'done') done = d
    else if (evt === 'error') error = d
  }

  while (true) {
    const { done: fin, value } = await reader.read()
    if (fin) break
    buf += dec.decode(value, { stream: true })
    // 以空行切分完整事件，最后一段可能不完整，留在 buf 里
    let idx
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      consume(buf.slice(0, idx))
      buf = buf.slice(idx + 2)
    }
  }
  if (buf.trim()) consume(buf)

  return { text, thinking, done, error, firstDeltaAt, total: Date.now() - t0 }
}

const main = async () => {
  // 游客额度是按 IP 累计的，上一条用例跑完就消耗掉了。
  // 不清零的话第二次运行必然失败，且失败原因看起来像功能坏了。
  const { connect } = await import('./_env.mjs')
  let db = null
  try {
    db = await connect()
    await db.query("DELETE FROM auth_rate_limit WHERE scope = 'ai_guest'")
  } catch {
    // CI 无库时忽略，状态接口仍可用
  }

  console.log('\n=== 1. 服务状态 ===')
  const st = await get('/ai/status')
  ok(st.code === 0, '状态接口可用', 'code=' + st.code)

  // CI 没有 API Key，也不该把真实 Key 写进仓库。
  // 未配置时只验证「接口正常降级」，不假装 AI 功能已验证过。
  if (st.data?.configured !== true) {
    console.log('\n  ⚠️ AI 未配置（无 KAOYAN_AI_API_KEY），跳过对话相关用例')
    ok(st.data?.configured === false, '未配置时状态接口正常返回 configured=false')

    // 未配置时对话应给出明确的服务不可用，而不是崩掉
    const res = await fetch(B + '/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: '测试' }),
    })
    const ct = res.headers.get('content-type') ?? ''
    let code = null
    if (ct.includes('text/event-stream')) {
      const text = await res.text()
      const m = /"code":(\d+)/.exec(text)
      code = m ? Number(m[1]) : null
    } else {
      const body = await res.json()
      code = body?.code ?? null
    }
    ok(code === 503, '未配置时明确返回 503', 'code=' + code)

    if (db) await db.end()
    console.log(`\n通过 ${pass} 项，失败 ${fail} 项（AI 对话用例已跳过）`)
    console.log('提示：本地设置 KAOYAN_AI_API_KEY 后可跑完整用例')
    process.exit(fail === 0 ? 0 : 1)
  }

  ok(st.data?.configured === true, 'AI 已配置', String(st.data?.configured))
  ok(st.data?.guest === true, '游客身份被识别')
  console.log(`     模型: ${st.data?.model}，游客剩余 ${st.data?.guestRemaining} 次`)

  console.log('\n=== 2. 流式对话（含知识点标记）===')
  const r = await chat('进程和线程有什么区别？请简要说明。')
  ok(!r.error, '对话无错误', r.error?.message ?? '')
  ok(r.thinking, '收到 thinking 事件（推理阶段）')
  ok(r.firstDeltaAt > 0, '首字已到达', r.firstDeltaAt + 'ms')
  ok(r.text.length > 30, '正文长度合理', r.text.length + ' 字')
  ok(r.done != null, '收到 done 事件')
  ok(r.done?.guest === true, 'done 标记为游客')
  console.log(`     首字 ${r.firstDeltaAt}ms，全文 ${r.total}ms，用量 ${r.done?.promptTokens}/${r.done?.completionTokens}`)
  console.log(`     正文前 80 字: ${r.text.slice(0, 80).replace(/\n/g, ' ')}`)

  // 知识点标记是这个功能的核心，单独检查
  const kp = [...r.text.matchAll(/\[\[kp:([^|\]]+)\|([^\]]+)\]\]/g)]
  if (kp.length) {
    console.log(`     知识点标记 ${kp.length} 个: ${kp.map(m => m[1]).join(', ')}`)
    // 标记里的 id 必须真实存在
    const points = await get('/knowledge')
    const validIds = new Set((points.data ?? []).map(p => p.pointId))
    const allValid = kp.every(m => validIds.has(m[1]))
    ok(allValid, '标记的知识点 id 全部真实存在')
  } else {
    console.log('     （本次回答未涉及具体知识点，未产生标记）')
  }

  console.log('\n=== 3. 游客限额（共 3 次）===')
  const st2 = await get('/ai/status')
  ok(st2.data.guestRemaining === 2, '已消耗 1 次', String(st2.data.guestRemaining))
  await chat('什么是时间复杂度？')
  await chat('死锁的四个必要条件？')
  const st3 = await get('/ai/status')
  ok(st3.data.guestRemaining === 0, '三次用完', String(st3.data.guestRemaining))
  const over = await chat('还能问吗？')
  ok(over.error?.code === 429, '第四次被拒绝', 'code=' + over.error?.code)
  console.log(`     ${over.error?.message}`)

  console.log('\n=== 4. 游客不能看会话列表 ===')
  const conv = await get('/ai/conversations')
  ok(conv.code === 401, '会话列表要求登录', 'code=' + conv.code)

  if (db) await db.end()
  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => { console.error('异常:', e.message); process.exit(1) })