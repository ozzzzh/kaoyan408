#!/usr/bin/env node
/** 图解生成质量测试：结构是否合格、内容是否切题、缓存是否生效 */
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

const main = async () => {
  // CI 没有 API Key，也不该把真实 Key 写进仓库。
  // 未配置时只验证接口降级到 503，并明确报告「用例已跳过」，
  // 不把它算成通过。
  const st = await fetch(B + '/ai/status').then((r) => r.json()).catch(() => null)
  if (st?.data?.configured === false) {
    console.log('\n  ⚠️ AI 未配置（无 KAOYAN_AI_API_KEY），图解用例已跳过')
    const r = await fetch(B + '/ai/diagram', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: '测试' }),
    })
    const j = await r.json().catch(() => ({}))
    ok(j.code === 503, '未配置时返回 503', 'code=' + j.code)
    console.log(`\n通过 ${pass} 项，失败 ${fail} 项（图解用例已跳过）`)
    process.exit(fail === 0 ? 0 : 1)
  }

  console.log('\n=== 1. 生成「进程三态转换」图解 ===')
  const t0 = Date.now()
  const r = await post('/ai/diagram', {
    question: '进程的三态转换是怎样的？',
    answerExcerpt: '进程有运行、就绪、阻塞三个基本状态，状态之间按条件转移。',
    knowledge: '进程的概念与状态',
  })
  const ms = Date.now() - t0
  ok(r.code === 0, '生成成功', 'code=' + r.code + (r.msg ? ' ' + r.msg : ''))
  if (r.code !== 0) { process.exit(1) }

  const dsl = r.data.dsl
  console.log(`     耗时 ${ms}ms，缓存命中: ${r.data.cached}`)
  console.log(`     标题: ${dsl.title}  (${dsl.width}×${dsl.height})`)
  console.log(`     节点 ${dsl.nodes.length} 个: ${dsl.nodes.map(n => n.label).join(' / ')}`)
  console.log(`     连线 ${dsl.edges.length} 条`)
  if (dsl.notes?.length) console.log(`     备注: ${dsl.notes.map(n => n.text).join(' | ')}`)

  console.log('\n=== 2. 结构校验 ===')
  ok(dsl.title && dsl.title.length > 0, '有标题')
  ok(dsl.nodes.length >= 2 && dsl.nodes.length <= 12, '节点数在 2-12', String(dsl.nodes.length))
  ok(dsl.edges.length <= 16, '连线数不超过 16', String(dsl.edges.length))

  const inBounds = dsl.nodes.every(n => n.x >= 30 && n.x <= dsl.width - 30 && n.y >= 24 && n.y <= dsl.height - 24)
  ok(inBounds, '所有节点坐标在画布内')

  const ids = new Set(dsl.nodes.map(n => n.id))
  const edgesValid = dsl.edges.every(e => ids.has(e.from) && ids.has(e.to))
  ok(edgesValid, '所有连线两端都存在')

  const shapesOk = dsl.nodes.every(n => ['box', 'circle', 'diamond', 'text'].includes(n.shape))
  ok(shapesOk, '图形类型合法')

  // 节点不重叠（中心距至少 40）
  let overlap = false
  for (let i = 0; i < dsl.nodes.length; i++) {
    for (let j = i + 1; j < dsl.nodes.length; j++) {
      const a = dsl.nodes[i], b = dsl.nodes[j]
      if (Math.hypot(a.x - b.x, a.y - b.y) < 40) overlap = true
    }
  }
  ok(!overlap, '节点之间没有重叠')

  console.log('\n=== 3. 内容切题 ===')
  const allText = [dsl.title, ...dsl.nodes.map(n => n.label), ...dsl.edges.map(e => e.label ?? '')].join(' ')
  const hasStateWords = /运行|就绪|阻塞|状态/.test(allText)
  ok(hasStateWords, '图中出现进程状态相关词', allText.slice(0, 60))

  console.log('\n=== 4. 缓存 ===')
  const t1 = Date.now()
  const r2 = await post('/ai/diagram', {
    question: '进程的三态转换是怎样的？',
    answerExcerpt: '进程有运行、就绪、阻塞三个基本状态，状态之间按条件转移。',
    knowledge: '进程的概念与状态',
  })
  ok(r2.code === 0, '第二次也成功')
  ok(r2.data.cached === true, '命中缓存')
  console.log(`     第二次耗时 ${Date.now() - t1}ms（应远小于首次 ${ms}ms）`)

  console.log('\n=== 5. 空问题应被拒 ===')
  const r3 = await post('/ai/diagram', { question: '' })
  ok(r3.code === 400, '空问题返回 400', 'code=' + r3.code)

  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => { console.error('异常:', e.message); process.exit(1) })