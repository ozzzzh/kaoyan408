#!/usr/bin/env node
/** 动画 DSL 生成质量测试 */
const B = 'http://127.0.0.1:8080/api/v1'
let pass = 0, fail = 0
const ok = (c, l, e = '') => c ? (pass++, console.log(`  ✅ ${l}${e ? ' — ' + e : ''}`)) : (fail++, console.log(`  ❌ ${l}${e ? ' — ' + e : ''}`))

const post = async (p, b) => {
  const r = await fetch(B + p, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(b ?? {}),
  })
  return r.json()
}

const main = async () => {
  const { connect } = await import('./_env.mjs')
  let db = null
  try {
    db = await connect()
    await db.query("DELETE FROM auth_rate_limit WHERE scope = 'ai_guest'")
  } catch {}

  console.log('\n=== 1. 生成「进程三态转换」动画 ===')
  const t0 = Date.now()
  const r = await post('/ai/animation', {
    question: '进程的三态转换是怎样的？',
    answerExcerpt: '进程在就绪、运行、阻塞三个状态之间转移。',
    knowledge: '进程的概念与状态',
  })
  const ms = Date.now() - t0
  ok(r.code === 0, '生成成功', 'code=' + r.code + (r.msg ? ' ' + r.msg : ''))
  if (r.code !== 0) { if (db) await db.end(); process.exit(1) }

  const d = r.data.dsl
  console.log(`     耗时 ${ms}ms，缓存: ${r.data.cached}`)
  console.log(`     标题: ${d.title}  (${d.width}×${d.height})`)
  console.log(`     节点 ${d.nodes.length}: ${d.nodes.map(n => n.label).join(' / ')}`)
  console.log(`     初始连线 ${d.edges.length} 条`)
  console.log(`     步骤 ${d.steps.length} 步：`)
  d.steps.forEach((s, i) => {
    const bits = []
    if (s.highlight?.length) bits.push(`高亮[${s.highlight.join(',')}]`)
    if (s.dim?.length) bits.push(`淡化[${s.dim.join(',')}]`)
    if (s.addEdges?.length) bits.push(`+${s.addEdges.length}线`)
    if (s.moveNodes?.length) bits.push(`移动${s.moveNodes.length}`)
    console.log(`       ${i + 1}. ${s.caption}${bits.length ? '  (' + bits.join(' ') + ')' : ''}`)
  })

  console.log('\n=== 2. 结构校验 ===')
  ok(d.steps.length >= 2 && d.steps.length <= 6, '步骤数在 2-6', String(d.steps.length))
  ok(d.steps.every(s => s.caption && s.caption.length > 0), '每步都有旁白')

  const ids = new Set(d.nodes.map(n => n.id))
  const allRefsValid = d.steps.every(s =>
    [...(s.highlight ?? []), ...(s.dim ?? []), ...(s.moveNodes ?? []).map(m => m.id),
     ...(s.addEdges ?? []).flatMap(e => [e.from, e.to])].every(id => ids.has(id))
  )
  ok(allRefsValid, '所有步骤引用的节点都存在')

  const movesInBounds = d.steps.every(s => (s.moveNodes ?? []).every(m =>
    m.x >= 30 && m.x <= d.width - 30 && m.y >= 20 && m.y <= d.height - 20))
  ok(movesInBounds, '移动目标坐标在画布内')

  const nodesInBounds = d.nodes.every(n => n.x >= 30 && n.x <= d.width - 30)
  ok(nodesInBounds, '初始节点在画布内')

  console.log('\n=== 3. 内容切题 ===')
  const allText = [d.title, ...d.nodes.map(n => n.label), ...d.steps.map(s => s.caption)].join(' ')
  ok(/就绪|运行|阻塞|状态/.test(allText), '出现进程状态相关词')
  console.log(`     ${allText.slice(0, 100)}`)

  console.log('\n=== 4. 缓存（与静态图解互不干扰）===')
  const t1 = Date.now()
  const r2 = await post('/ai/animation', {
    question: '进程的三态转换是怎样的？',
    answerExcerpt: '进程在就绪、运行、阻塞三个状态之间转移。',
    knowledge: '进程的概念与状态',
  })
  ok(r2.code === 0 && r2.data.cached === true, '动画命中缓存')
  console.log(`     第二次 ${Date.now() - t1}ms`)

  const r3 = await post('/ai/diagram', {
    question: '进程的三态转换是怎样的？',
    answerExcerpt: '进程在就绪、运行、阻塞三个状态之间转移。',
    knowledge: '进程的概念与状态',
  })
  ok(r3.code === 0, '同问题的静态图解仍可独立生成', 'cached=' + r3.data?.cached)

  if (db) await db.end()
  console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => { console.error('异常:', e.message); process.exit(1) })