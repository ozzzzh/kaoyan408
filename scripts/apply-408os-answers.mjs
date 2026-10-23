#!/usr/bin/env node
/**
 * 按 408os 同步选择题答案（用户明确要求：完全和该刷题网站一致）。
 *
 * 注意：比对阶段发现 2026 卷有 5 处差异、2011 有 1 处，
 * 其中 2026 的本地答案带完整自洽解析，疑似 408os 侧数据问题。
 * 用户已确认以 408os 为准，故此处照做，并在报告中留痕。
 */
import { readFileSync, writeFileSync } from 'node:fs'

const APPLY = process.argv.includes('--apply')
const os = JSON.parse(readFileSync('scripts/408os-scores.json', 'utf8'))
const YEARS = Object.keys(os).map(Number).sort()

const changes = []

for (const year of YEARS) {
  const path = `client/public/exams/${year}/paper.json`
  const paper = JSON.parse(readFileSync(path, 'utf8'))
  const log = []

  for (const rq of os[year]) {
    if (rq.type !== 1) continue // 只处理选择题
    const lq = paper.questions.find((q) => q.number === rq.index)
    if (!lq) continue
    const local = (lq.answer ?? '').trim().toUpperCase()
    const remote = (rq.answer ?? '').trim().toUpperCase()
    if (local === remote) continue

    log.push(`第${rq.index}题 ${local || '(空)'}→${remote}`)
    lq.answer = remote
  }

  if (log.length) {
    changes.push({ year, log })
    if (APPLY) writeFileSync(path, JSON.stringify(paper, null, 2) + '\n', 'utf8')
  }
}

console.log(APPLY ? '=== 已写入（答案以 408os 为准）===' : '=== 预演（未写入）===')
console.log('')
if (!changes.length) console.log('无需改动')
for (const item of changes) {
  console.log(`${item.year}: ${item.log.length} 处`)
  console.log(`      ${item.log.join('  ')}`)
}
const total = changes.reduce((s, c) => s + c.log.length, 0)
console.log(`\n合计改动: ${total} 处，涉及 ${changes.length} 卷`)
if (!APPLY) console.log('\n加 --apply 实际写入。')