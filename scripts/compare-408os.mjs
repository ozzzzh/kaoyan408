#!/usr/bin/env node
/**
 * 比对本地 paper.json 与 408os 权威数据（分值 + 选择题答案）。
 * 408os 为用户指定基准，差异以 408os 为准。
 */
import { readFileSync, writeFileSync } from 'node:fs'

const os = JSON.parse(readFileSync('scripts/408os-scores.json', 'utf8'))
const YEARS = Object.keys(os).map(Number).sort()

let scoreDiff = 0
let answerDiff = 0
let same = 0
const report = []
const answerReport = []

for (const year of YEARS) {
  const paper = JSON.parse(readFileSync(`client/public/exams/${year}/paper.json`, 'utf8'))
  const local = paper.questions
  const remote = os[year]

  const localSum = local.reduce((s, q) => s + (q.score ?? 0), 0)
  const remoteSum = remote.reduce((s, q) => s + (q.score ?? 0), 0)

  const diffs = []
  for (const rq of remote) {
    const lq = local.find((q) => q.number === rq.index)
    if (!lq) continue
    if (lq.score !== rq.score) diffs.push({ number: rq.index, local: lq.score, remote: rq.score })
  }

  const aDiffs = []
  for (const rq of remote) {
    if (rq.type !== 1) continue
    const lq = local.find((q) => q.number === rq.index)
    if (!lq) continue
    const la = (lq.answer ?? '').trim().toUpperCase()
    const ra = (rq.answer ?? '').trim().toUpperCase()
    if (la !== ra) aDiffs.push({ number: rq.index, local: la || '(空)', remote: ra || '(空)' })
  }

  scoreDiff += diffs.length
  answerDiff += aDiffs.length
  if (diffs.length === 0 && aDiffs.length === 0) same++

  const flag = diffs.length === 0 && aDiffs.length === 0 ? '一致' : '有差异'
  console.log(
    `${year}: 分值 本地${localSum} / 408os${remoteSum}  分值差异${diffs.length}处  答案差异${aDiffs.length}处  ${flag}`,
  )

  if (diffs.length) {
    report.push({ year, diffs })
    console.log(`      分值: ${diffs.map((d) => `第${d.number}题 ${d.local}→${d.remote}`).join('  ')}`)
  }
  if (aDiffs.length) {
    answerReport.push({ year, diffs: aDiffs })
    console.log(`      答案: ${aDiffs.map((d) => `第${d.number}题 ${d.local}→${d.remote}`).join('  ')}`)
  }
}

console.log('\n================ 汇总 ================')
console.log(`完全一致的卷: ${same}/${YEARS.length}`)
console.log(`分值差异合计: ${scoreDiff} 处`)
console.log(`答案差异合计: ${answerDiff} 处`)

writeFileSync('scripts/408os-diff-report.json', JSON.stringify({ score: report, answer: answerReport }, null, 2), 'utf8')
console.log('\n已保存 scripts/408os-diff-report.json')