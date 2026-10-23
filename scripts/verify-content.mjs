import { createConnection } from 'mysql2/promise'

const connection = await createConnection({
  host: '127.0.0.1',
  port: 3306,
  user: 'root',
  password: '123456',
  database: 'kaoyan_db',
  charset: 'utf8mb4',
})

const [questions] = await connection.query(
  "SELECT LEFT(stem, 60) AS stem, answer, LEFT(explanation, 50) AS explanation FROM question WHERE exam_ref = 'exam-2026-1'",
)
console.log('题干:', questions[0].stem)
console.log('答案:', questions[0].answer)
console.log('解析:', questions[0].explanation)

const [points] = await connection.query('SELECT point_id, title FROM knowledge_point LIMIT 5')
console.log('知识点:', points.map((row) => `${row.point_id}=${row.title}`).join(' | '))

const [blocks] = await connection.query(
  "SELECT block_id, type, LEFT(content_json, 70) AS head FROM knowledge_block WHERE type='animation' LIMIT 2",
)
for (const block of blocks) {
  console.log(`动画块 ${block.block_id}:`, block.head)
}

const [mojibake] = await connection.query(
  "SELECT COUNT(*) AS bad FROM question WHERE stem LIKE '%Ã%' OR stem LIKE '%å%'",
)
console.log('乱码行数:', mojibake[0].bad)

await connection.end()