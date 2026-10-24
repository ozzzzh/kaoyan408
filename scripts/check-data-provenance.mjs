/**
 * 数据来源核查：区分「真实作答记录」与「回归脚本的测试数据」。
 *
 * 背景：本地库里有两个账号 tester01 / admin。
 * 期间我为了验证错题闭环、背诵 SRS、刷题页等，用脚本反复提交过答案。
 * 这个脚本把每个账号的作答明细列出来，便于判断哪些是验证产生的。
 */
import { connect } from './_env.mjs'

const db = await connect()

console.log('=== 1) 用户 ===')
const [users] = await db.query(
  'SELECT id, username, email, created_at FROM app_user ORDER BY id',
)
for (const u of users) {
  console.log(`  id=${u.id}  ${u.username}  ${u.email || '(无邮箱)'}  注册于 ${u.created_at}`)
}

console.log('\n=== 2) 各用户的作答记录 ===')
const [attempts] = await db.query(`
  SELECT u.username,
         COUNT(*) AS attempts,
         SUM(a.result = 1) AS correct,
         MIN(a.answered_at) AS first_at,
         MAX(a.answered_at) AS last_at,
         COUNT(DISTINCT a.question_id) AS distinct_questions
  FROM question_attempt a
  JOIN app_user u ON u.id = a.user_id
  GROUP BY u.username
  ORDER BY u.username
`)
for (const r of attempts) {
  console.log(
    `  ${r.username.padEnd(10)} 作答 ${String(r.attempts).padStart(4)} 次` +
      ` (对 ${r.correct})  涉及 ${r.distinct_questions} 道题` +
      `  ${r.first_at} ~ ${r.last_at}`,
  )
}

console.log('\n=== 3) 作答时间分布（判断是人工还是脚本）===')
const [byDay] = await db.query(`
  SELECT u.username, DATE(a.answered_at) AS d, COUNT(*) AS n
  FROM question_attempt a JOIN app_user u ON u.id = a.user_id
  GROUP BY u.username, DATE(a.answered_at)
  ORDER BY u.username, d
`)
for (const r of byDay) {
  console.log(`  ${r.username.padEnd(10)} ${r.d}  ${r.n} 次`)
}

console.log('\n=== 4) 同一秒内的密集提交（脚本特征）===')
const [bursts] = await db.query(`
  SELECT u.username, a.answered_at, COUNT(*) AS n
  FROM question_attempt a JOIN app_user u ON u.id = a.user_id
  GROUP BY u.username, a.answered_at
  HAVING n > 1
  ORDER BY n DESC LIMIT 8
`)
if (bursts.length) {
  for (const r of bursts) {
    console.log(`  ⚠ ${r.username} 在 ${r.created_at} 同一时间戳提交 ${r.n} 次`)
  }
} else {
  console.log('  没有同一秒的密集提交')
}

console.log('\n=== 5) 背诵卡片 ===')
const [cards] = await db.query(`
  SELECT u.username, COUNT(*) AS cards, SUM(c.review_count) AS reviews,
         MAX(c.box) AS max_box, MIN(c.next_review_date) AS next_due
  FROM memory_card c JOIN app_user u ON u.id = c.user_id
  GROUP BY u.username
`)
for (const r of cards) {
  console.log(
    `  ${r.username.padEnd(10)} 卡片 ${r.cards} 张  复习 ${r.reviews ?? 0} 次` +
      `  最高盒号 ${r.max_box}  下次复习 ${r.next_due}`,
  )
}

console.log('\n=== 6) 知识点做题记录（block_progress）===')
const [progress] = await db.query(`
  SELECT u.username, COUNT(*) AS rows_, SUM(p.done_count) AS done
  FROM block_progress p JOIN app_user u ON u.id = p.user_id
  GROUP BY u.username
`)
for (const r of progress) {
  console.log(`  ${r.username.padEnd(10)} ${r.rows_} 行  累计作答 ${r.done}`)
}

await db.end()

console.log('\n=== 结论提示 ===')
console.log('  题库内容（846 题 / 147 知识点 / 1634 块）来自真实真题，不是测试数据。')
console.log('  作答记录、背诵卡片是开发期验证产物，属于测试数据。')
console.log('  上线前应清空：question_attempt / block_progress / daily_stat / memory_card。')