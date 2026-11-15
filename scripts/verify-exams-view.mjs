/**
 * 原卷接口回归：18 卷逐卷校验题量、分值、配图与知识点关联。
 */
const BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1'

const list = await (await fetch(`${BASE}/exams`)).json()
console.log(`试卷总数: ${list.data.length}\n`)

const EXPECT_QUESTIONS = 47
const EXPECT_CHOICE = 40
const EXPECT_CHOICE_SCORE = 80

let problems = 0
const scoreAnomalies = []

console.log('年份  题数  选择  综合  选分  综分  合计  配图  有知识点')
for (const item of list.data) {
  const r = await (await fetch(`${BASE}/exams/${item.year}`)).json()
  const qs = r.data.questions
  const choice = qs.filter((q) => q.questionType === 'choice')
  const comp = qs.filter((q) => q.questionType !== 'choice')
  const cScore = choice.reduce((s, q) => s + q.score, 0)
  const pScore = comp.reduce((s, q) => s + q.score, 0)
  const withImg = qs.filter((q) => q.imagePaths?.length).length
  const withKb = qs.filter((q) => q.knowledgeBlockIds?.length).length
  const total = cScore + pScore

  const flags = []
  if (qs.length !== EXPECT_QUESTIONS) flags.push(`题数≠${EXPECT_QUESTIONS}`)
  if (choice.length !== EXPECT_CHOICE) flags.push(`选择≠${EXPECT_CHOICE}`)
  if (cScore !== EXPECT_CHOICE_SCORE) flags.push(`选分≠${EXPECT_CHOICE_SCORE}`)
  if (total !== 150) scoreAnomalies.push(`${item.year}(综合${pScore})`)
  if (withKb === 0) flags.push('无知识点关联')

  if (flags.length) problems++

  console.log(
    String(item.year).padEnd(6) +
      String(qs.length).padStart(4) +
      String(choice.length).padStart(6) +
      String(comp.length).padStart(6) +
      String(cScore).padStart(6) +
      String(pScore).padStart(6) +
      String(total).padStart(6) +
      String(withImg).padStart(6) +
      String(withKb).padStart(10) +
      (flags.length ? '  ⚠ ' + flags.join('/') : ''),
  )
}

console.log('')
console.log(`结构异常卷数: ${problems}`)
if (scoreAnomalies.length) {
  console.log(`总分≠150 的卷（源数据缺陷，按用户要求保留）: ${scoreAnomalies.join(', ')}`)
}
if (problems === 0) {
  console.log('✅ 18 卷结构全部正常（题数/选择数/选择分值/知识点关联）')
}