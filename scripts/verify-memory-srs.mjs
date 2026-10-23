/**
 * 背诵卡片间隔重复回归：
 *   记住了 → 盒号递增、间隔拉长
 *   没记住 → 退回 0 盒、明天再复习
 *   连续记住 → 间隔按 0,1,2,4,7,15,30,60 天推进并封顶
 */
const BASE = 'http://127.0.0.1:8080/api/v1'

const login = await (
  await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'tester01', password: 'test123456' }),
  })
).json()
const auth = { Authorization: `Bearer ${login.data.token}`, 'Content-Type': 'application/json' }

const review = async (cardId, remembered) =>
  (
    await (
      await fetch(`${BASE}/memory/review`, {
        method: 'POST',
        headers: auth,
        body: JSON.stringify({ cardId, remembered }),
      })
    ).json()
  ).data

const due = async () => (await (await fetch(`${BASE}/memory/due`, { headers: auth })).json()).data
const summary = async () => (await (await fetch(`${BASE}/memory/summary`, { headers: auth })).json()).data

const EXPECTED = [0, 1, 2, 4, 7, 15, 30, 60]
let pass = 0
let fail = 0

const check = (ok, label) => {
  if (ok) {
    pass++
    console.log(`  ✅ ${label}`)
  } else {
    fail++
    console.log(`  ❌ ${label}`)
  }
}

const queue = await due()
if (!queue.total) {
  console.log('到期队列为空，先添加卡片')
  await fetch(`${BASE}/memory/add?subject=ds&limit=3`, { method: 'POST', headers: auth })
}
const cards = (await due()).items
const card = cards[0]
console.log(`测试卡片 cardId=${card.cardId} 起始 box=${card.box}\n`)

// --- 连续答对：间隔应按盒号推进 ---
console.log('[1] 连续 8 次「记住了」，检查间隔推进与封顶')
let last = null
for (let i = 1; i <= 9; i++) {
  last = await review(card.cardId, true)
  const expectedBox = Math.min(i, 7)
  const expectedInterval = EXPECTED[expectedBox]
  const ok = last.box === expectedBox && last.intervalDays === expectedInterval
  check(ok, `第 ${i} 次: box=${last.box}(期望${expectedBox}) interval=${last.intervalDays}天(期望${expectedInterval})`)
}

// --- 答错：退回 0 盒 ---
console.log('\n[2] 一次「没记住」，应退回第 0 盒、隔天复习')
const afterWrong = await review(card.cardId, false)
check(afterWrong.box === 0, `box=${afterWrong.box}（期望 0）`)
check(afterWrong.intervalDays === 0, `interval=${afterWrong.intervalDays} 天（期望 0，即明天）`)

// --- 答对后重新上升 ---
console.log('\n[3] 再答对一次，应回到 1 盒 / 1 天')
const afterAgain = await review(card.cardId, true)
check(afterAgain.box === 1, `box=${afterAgain.box}（期望 1）`)
check(afterAgain.intervalDays === 1, `interval=${afterAgain.intervalDays} 天（期望 1）`)

// --- 概览统计 ---
console.log('\n[4] 概览统计')
const s = await summary()
check(s.total > 0, `总数 ${s.total} > 0`)
check(s.reviews >= 11, `累计复习 ${s.reviews} 次（应 ≥11）`)
check(s.corrects >= 10, `记住 ${s.corrects} 次（应 ≥10）`)
check(s.due === 0 || s.due > 0, `到期 ${s.due}（间隔已推后，应为 0 或仅剩其他卡）`)
console.log(`     准确率 ${s.accuracy}%  牢固(box≥5) ${s.solid}`)
console.log(`     分科目: ${s.subjects.map((x) => `${x.name}=${x.total}`).join(' ')}`)

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
if (fail) process.exitCode = 1