/**
 * 错题重做闭环回归：
 *   答错 → 进入待订正 → 重做答对 → 移入已订正 → 再答错 → 回到待订正
 */
const BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1'

const login = await (
  await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
    username: process.env.TEST_USERNAME ?? 'tester01',
    password: process.env.TEST_PASSWORD ?? 'test123456',
  }),
  })
).json()
const auth = { Authorization: `Bearer ${login.data.token}`, 'Content-Type': 'application/json' }

const queue = async (scope) =>
  (await (await fetch(`${BASE}/practice/queue?scope=${scope}`, { headers: auth })).json()).data
const wrongBook = async () => (await (await fetch(`${BASE}/practice/wrong-book`, { headers: auth })).json()).data
const answer = async (ref, value) =>
  (
    await (
      await fetch(`${BASE}/questions/${ref}/answer`, {
        method: 'POST',
        headers: auth,
        body: JSON.stringify({ answer: value }),
      })
    ).json()
  ).data

const REF = process.env.TEST_REF ?? 'exam-2026-7'

// 先看这题标准答案。
// 注意字段名：单题详情返回 answer；作答提交的响应才叫 standardAnswer。
// 详情接口仅在「已作答」后下发 answer，故先取一次，没有就靠提交响应拿。
let detail = await (await fetch(`${BASE}/questions/${REF}`, { headers: auth })).json()
let correctAnswer = detail.data.answer
if (!correctAnswer) {
  // 还没作答过：先随便提交一次拿到标准答案，再重置为「未答错」状态
  const probe = await answer(REF, 'A')
  correctAnswer = probe.standardAnswer
  console.log(`（首次探测，标准答案取自提交响应: ${correctAnswer}）`)
}
console.log(`测试题 ${REF}，标准答案: ${correctAnswer}`)

// --- 步骤 1：故意答错 ---
const wrongPick = ['A', 'B', 'C', 'D'].find((k) => k !== correctAnswer) ?? 'A'
console.log(`\n[1] 提交错误答案 ${wrongPick}`)
const r1 = await answer(REF, wrongPick)
console.log(`    correct=${r1.correct}`)

let pending = await queue('wrong-pending')
let mastered = await queue('wrong-mastered')
console.log(`    待订正=${pending.total} 已订正=${mastered.total}`)
const inPending = pending.items.some((i) => i.examRef === REF)
console.log(`    ${inPending ? '✅' : '❌'} 该题在待订正列表中`)
const item1 = pending.items.find((i) => i.examRef === REF)
if (item1) console.log(`    wrongCount=${item1.wrongCount} mastered=${item1.mastered}`)

// --- 步骤 2：重做答对 ---
console.log(`\n[2] 重做并提交正确答案 ${correctAnswer}`)
const r2 = await answer(REF, correctAnswer)
console.log(`    correct=${r2.correct}`)

pending = await queue('wrong-pending')
mastered = await queue('wrong-mastered')
console.log(`    待订正=${pending.total} 已订正=${mastered.total}`)
const stillPending = pending.items.some((i) => i.examRef === REF)
const nowMastered = mastered.items.some((i) => i.examRef === REF)
console.log(`    ${!stillPending ? '✅' : '❌'} 已移出待订正`)
console.log(`    ${nowMastered ? '✅' : '❌'} 已进入已订正`)
const item2 = mastered.items.find((i) => i.examRef === REF)
if (item2) console.log(`    wrongCount=${item2.wrongCount} retryCorrect=${item2.retryCorrectCount} mastered=${item2.mastered}`)

// --- 步骤 3：再次答错，应回到待订正 ---
console.log(`\n[3] 再次答错 ${wrongPick}（应撤销已订正）`)
await answer(REF, wrongPick)
pending = await queue('wrong-pending')
mastered = await queue('wrong-mastered')
const backToPending = pending.items.some((i) => i.examRef === REF)
console.log(`    待订正=${pending.total} 已订正=${mastered.total}`)
console.log(`    ${backToPending ? '✅' : '❌'} 已退回待订正`)
const item3 = pending.items.find((i) => i.examRef === REF)
if (item3) console.log(`    wrongCount=${item3.wrongCount}（应 ≥2） mastered=${item3.mastered}`)

// --- 步骤 4：错题本汇总 ---
const wb = await wrongBook()
console.log(`\n[4] 错题本汇总: 待订正=${wb.pending} 已订正=${wb.mastered} 合计=${wb.total}`)
console.log(`    subjects: ${wb.subjects.map((s) => `${s.name} 待${s.pending}/订${s.mastered}`).join('  ')}`)

// --- 步骤 5：wrong 口径应含全部错过的题 ---
const all = await queue('wrong')
console.log(`\n[5] scope=wrong（含已订正）=${all.total}，应 ≥ 待订正+已订正`)
console.log(`    ${all.total >= wb.total ? '✅' : '❌'} 口径一致`)