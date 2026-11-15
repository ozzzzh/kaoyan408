import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const EXAMS = join(ROOT, 'client/public/exams')

console.log('=== 各卷结构与分值一致性 ===')
let grandTotal = 0
for (const year of readdirSync(EXAMS).filter((d) => /^\d{4}$/.test(d)).sort()) {
  const paper = JSON.parse(readFileSync(join(EXAMS, year, 'paper.json'), 'utf8'))
  const qs = paper.questions ?? []
  const choice = qs.filter((q) => q.questionType === 'choice')
  const comp = qs.filter((q) => q.questionType !== 'choice')
  const choiceScore = choice.reduce((s, q) => s + (q.score ?? 0), 0)
  const compScore = comp.reduce((s, q) => s + (q.score ?? 0), 0)
  const missingAnswer = choice.filter((q) => !q.answer).length
  const missingExplain = qs.filter((q) => !q.explanation).length
  const numbers = qs.map((q) => q.number)
  const contiguous = numbers.every((n, i) => n === i + 1)
  grandTotal += qs.length

  const flags = []
  if (qs.length !== 47) flags.push(`题数=${qs.length}`)
  if (choiceScore !== 80) flags.push(`选择分=${choiceScore}`)
  if (compScore !== 70) flags.push(`综合分=${compScore}`)
  if (missingAnswer) flags.push(`缺答案=${missingAnswer}`)
  if (missingExplain) flags.push(`缺解析=${missingExplain}`)
  if (!contiguous) flags.push('题号不连续')

  const status = flags.length ? '⚠ ' + flags.join(' ') : '✓'
  console.log(
    `${year}: ${qs.length}题 选择${choice.length}(${choiceScore}分) 综合${comp.length}(${compScore}分) 总分${choiceScore + compScore} ${status}`,
  )
}
console.log(`\n合计题数: ${grandTotal}`)

console.log('\n=== 2026 卷前 3 题（人工核对用） ===')
const p2026 = JSON.parse(readFileSync(join(EXAMS, '2026', 'paper.json'), 'utf8'))
for (const q of p2026.questions.slice(0, 3)) {
  console.log(`\n[第 ${q.number} 题 · ${q.subject} · ${q.chapterName} · ${q.score}分] 答案 ${q.answer}`)
  console.log(q.stem.slice(0, 120).replace(/\n/g, ' '))
  console.log((q.options ?? []).map((o) => `${o.key}.${o.text.slice(0, 40)}`).join('  '))
}

console.log('\n=== 缺失图片的题（已知内容缺陷） ===')
for (const year of readdirSync(EXAMS).filter((d) => /^\d{4}$/.test(d))) {
  const paper = JSON.parse(readFileSync(join(EXAMS, year, 'paper.json'), 'utf8'))
  for (const q of paper.questions ?? []) {
    for (const link of q.resourceLinks ?? []) {
      const rel = String(link).replace(/^\/+/, '').replace(/^exams\//, '')
      if (!existsSync(join(EXAMS, rel))) {
        console.log(`  ${q.id}: ${link}`)
      }
    }
  }
}