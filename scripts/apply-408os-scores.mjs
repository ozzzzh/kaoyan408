#!/usr/bin/env node
/**
 * 按 408os 校准本地分值（用户指定 408os 为基准）。
 *
 * 分值：以 408os 为准，直接覆盖。
 * 答案：**不覆盖**。比对发现 2026 卷 5 处差异全部把答案改成 A，
 *       而本地对应题目均带完整自洽解析，疑似 408os 侧数据问题。
 *       交由用户决策，脚本只报告不改动。
 */
import { readFileSync, writeFileSync } from 'node:fs'

const APPLY = process.argv.includes('--apply')
const os = JSON.parse(readFileSync('scripts/408os-scores.json', 'utf8'))
const YEARS = Object.keys(os).map(Number).sort()

let changedQuestions = 0
let changedPapers = 0
const summary = []

for (const year of YEARS) {
  const path = `client/public/exams/${year}/paper.json`
  const paper = JSON.parse(readFileSync(path, 'utf8'))
  let changed = 0
  const log = []

  for (const rq of os[year]) {
    const lq = paper.questions.find((q) => q.number === rq.index)
    if (!lq || lq.score === rq.score) continue

    log.push(`第${rq.index}题 ${lq.score}→${rq.score}`)
    lq.score = rq.score
    changed++
  }

  if (changed > 0) {
    changedPapers++
    changedQuestions += changed
    const before = paper.questions.reduce((s, q) => s + (q.score ?? 0), 0)
    const compSum = paper.questions
      .filter((q) => q.questionType !== 'choice')
      .reduce((s, q) => s + (q.score ?? 0), 0)
    summary.push({ year, changed, log, total: before, compSum })

    if (APPLY) {
      writeFileSync(path, JSON.stringify(paper, null, 2) + '\n', 'utf8')
    }
  }
}

console.log(APPLY ? '=== 已写入（分值以 408os 为准）===' : '=== 预演（未写入）===')
console.log(`\n涉及卷数: ${changedPapers}  改动题数: ${changedQuestions}\n`)
for (const item of summary) {
  console.log(`${item.year}: 综合题合计 ${item.compSum} 分, 共 ${item.changed} 处`)
  console.log(`      ${item.log.join('  ')}`)
}
console.log('\n答案差异未处理（需用户决策）:')
console.log('  2026 第11/17/20/29/37 题、2011 第26题')
if (!APPLY) console.log('\n加 --apply 实际写入。')