/**
 * 热力图数据检查：确认后端返回的 days 是否按「周一对齐」排列。
 *
 * 前端按每 7 个一组切列，并假定第 0 项是周一。
 * 若后端从「90 天前的当天」开始返回，则第 0 项不是周一，
 * 整张图的星期标签就会整体错位。
 */
const BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1'
const USER = {
  username: process.env.TEST_USERNAME ?? 'tester01',
  password: process.env.TEST_PASSWORD ?? 'test123456',
}

const login = await (
  await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(USER),
  })
).json()
const auth = { Authorization: `Bearer ${login.data.token}`, 'Content-Type': 'application/json' }

const dash = await (await fetch(`${BASE}/stats/dash`, { headers: auth })).json()
const days = dash.data.heatmap?.days ?? []

console.log(`days 长度: ${days.length}`)
console.log(`首日: ${days[0]?.date}   末日: ${days[days.length - 1]?.date}`)

const weekdayOf = (iso) => {
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return names[new Date(`${iso}T00:00:00`).getDay()]
}

console.log(`首日是: ${weekdayOf(days[0].date)}`)
console.log(`末日是: ${weekdayOf(days[days.length - 1].date)}`)
console.log('')

// 前端按 7 个一组切列，并假设每组第 0 项是周一
console.log('前端切列后，每列第 1 格实际对应的星期：')
const wrong = []
for (let i = 0; i < Math.min(days.length, 28); i += 7) {
  const d = days[i]
  const w = weekdayOf(d.date)
  const ok = w === '周一'
  if (!ok) wrong.push(d.date)
  console.log(`  第 ${i / 7 + 1} 列第 1 格 = ${d.date} (${w}) ${ok ? '✅' : '❌ 不是周一'}`)
}

console.log('')
console.log('今天:', new Date().toLocaleDateString('sv-SE'), weekdayOf(new Date().toLocaleDateString('sv-SE')))

if (wrong.length) {
  console.log(`\n❌ ${wrong.length} 列的起始日不是周一 → 星期标签会整体错位`)
  console.log('   根因：前端假定 days[0] 是周一，但后端从任意日期开始返回。')
} else {
  console.log('\n✅ 每列起始日都是周一，前端分组假设成立')
}