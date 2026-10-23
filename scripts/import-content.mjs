#!/usr/bin/env node
/**
 * 内容导入：把 client 侧的静态内容（真题 JSON + 知识正文 TS）转成
 *   server/src/main/resources/db/migration/V2__seed_content.sql
 * 并（可选）直接灌入本地 MySQL。
 *
 * 用法：
 *   node scripts/import-content.mjs            # 只生成 SQL
 *   node scripts/import-content.mjs --apply    # 生成并写入数据库
 *
 * 数据来源声明：真题与知识正文来自 simple408（CC BY 4.0），真题以 408os 为准。
 */
import { readFileSync, readdirSync, existsSync, writeFileSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  articlesByBook,
  knowledgeBooks,
  subpointLocationByBlockId,
  blockIdsOfPoint,
  bookOfPoint,
} from '../client/content-bundle/all.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const EXAMS_DIR = join(ROOT, 'client/public/exams')
const OUT_SQL = join(ROOT, 'server/src/main/resources/db/migration/V2__seed_content.sql')

const BOOK_SUBJECT = {
  'data-structures': 'ds',
  'operating-systems': 'os',
  'computer-organization': 'co',
  'computer-network': 'cn',
}

const problems = []
const warnings = []
const fail = (message) => problems.push(message)

/* ---------------------------------- SQL 工具 --------------------------------- */

const sqlStr = (value) => {
  if (value === null || value === undefined) return 'NULL'
  return `'${String(value)
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "''")
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\u0000/g, '')}'`
}

const sqlJson = (value) => sqlStr(JSON.stringify(value ?? []))

/* ---------------------------------- 读取真题 ---------------------------------- */

const examYears = []
const questions = []
const options = []
const subQuestions = []
const questionKnowledge = []

const yearDirs = readdirSync(EXAMS_DIR, { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && /^\d{4}$/.test(entry.name))
  .map((entry) => entry.name)
  .sort()

for (const yearDir of yearDirs) {
  const paperPath = join(EXAMS_DIR, yearDir, 'paper.json')
  if (!existsSync(paperPath)) {
    fail(`缺少试卷文件：${yearDir}/paper.json`)
    continue
  }
  const paper = JSON.parse(readFileSync(paperPath, 'utf8'))
  const year = Number(paper.year ?? yearDir)

  examYears.push({ year, title: paper.title ?? `${year} 年 408 真题` })

  const list = paper.questions ?? []
  if (list.length !== 47) {
    warnings.push(`${year} 年题目数为 ${list.length}，与 47 不一致`)
  }
  if (Number(paper.questionCount) !== list.length) {
    warnings.push(`${year} 年 questionCount=${paper.questionCount} 与实际 ${list.length} 不一致`)
  }

  for (const question of list) {
    const ref = question.id ?? `exam-${year}-${question.number}`
    questions.push({
      ref,
      year,
      number: question.number,
      subject: question.subject,
      chapterName: question.chapterName ?? null,
      questionType: question.questionType ?? 'choice',
      score: question.score ?? 0,
      stem: question.stem ?? '',
      answer: question.answer ?? null,
      difficulty: question.difficulty ?? null,
      explanation: question.explanation ?? null,
      tags: question.tags ?? [],
      imagePaths: question.resourceLinks ?? [],
    })

    for (const option of question.options ?? []) {
      options.push({ ref, key: option.key, text: option.text })
    }
    for (const sub of question.subQuestions ?? []) {
      subQuestions.push({
        ref,
        number: sub.number,
        score: sub.score ?? null,
        description: sub.description ?? null,
      })
    }
    for (const blockId of question.knowledgeBlockIds ?? []) {
      if (!subpointLocationByBlockId[blockId]) {
        fail(`题目 ${ref} 引用了不存在的知识块：${blockId}`)
        continue
      }
      questionKnowledge.push({ ref, blockId })
    }
  }
}

/* ---------------------------------- 读取知识 ---------------------------------- */

const pointTitleById = new Map()
const pointSubjectById = new Map()
const pointOrder = []
for (const book of knowledgeBooks) {
  for (const chapter of book.chapters ?? []) {
    for (const section of chapter.sections ?? []) {
      for (const point of section.points ?? []) {
        if (pointTitleById.has(point.id)) continue
        pointTitleById.set(point.id, point.title)
        pointSubjectById.set(point.id, BOOK_SUBJECT[book.id] ?? book.id)
        pointOrder.push(point.id)
      }
    }
  }
}

// 树里没有、但块索引里存在的点（保证外键语义完整）
for (const [pointId, bookId] of Object.entries(bookOfPoint)) {
  if (pointTitleById.has(pointId)) continue
  pointTitleById.set(pointId, pointId)
  pointSubjectById.set(pointId, BOOK_SUBJECT[bookId] ?? bookId)
  pointOrder.push(pointId)
  warnings.push(`知识树缺少知识点 ${pointId}，已按块索引补齐`)
}

const articles = Object.values(articlesByBook).flatMap((byPoint) => Object.values(byPoint ?? {}))
const blocks = []
const blockIds = new Set()
let animationBlocks = 0

for (const article of articles) {
  if (!article?.pointId) {
    fail('存在缺少 pointId 的知识文章')
    continue
  }
  if (!pointTitleById.has(article.pointId)) {
    fail(`知识正文 pointId=${article.pointId} 不在知识树中`)
  }
  let order = 0
  for (const subpoint of article.subpoints ?? []) {
    for (const block of subpoint.blocks ?? []) {
      if (!block.id) {
        fail(`知识点 ${article.pointId} 存在无 id 的知识块`)
        continue
      }
      if (blockIds.has(block.id)) {
        fail(`知识块 id 重复：${block.id}`)
        continue
      }
      blockIds.add(block.id)
      const location = subpointLocationByBlockId[block.id]
      if (!location) {
        warnings.push(`知识块 ${block.id} 未出现在块索引中`)
      }
      if (block.type === 'animation') animationBlocks++

      // 动画块剥离不可序列化的渲染函数，仅保留元信息（渲染在前端完成）
      const content = block.type === 'animation'
        ? {
            id: block.id,
            type: 'animation',
            animationId: block.animation?.id ?? null,
            ariaLabel: block.animation?.ariaLabel ?? null,
            scene: block.animation?.scene ?? null,
            stepIds: (block.animation?.steps ?? []).map((step) => step.id),
            sourceImport: block.sourceImport ?? null,
          }
        : block

      blocks.push({
        pointId: article.pointId,
        blockId: block.id,
        subpointId: location?.subpointId ?? subpoint.id ?? null,
        subpointTitle: location?.subpointTitle ?? subpoint.title ?? null,
        order: ++order,
        type: block.type,
        content,
      })
    }
  }
}

// 每点内按块索引顺序重排，保证阅读顺序稳定
const blockOrderIndex = new Map()
for (const [pointId, ids] of Object.entries(blockIdsOfPoint ?? {})) {
  ids.forEach((id, index) => blockOrderIndex.set(id, index))
}
blocks.sort((a, b) => {
  if (a.pointId !== b.pointId) return a.pointId.localeCompare(b.pointId)
  const ai = blockOrderIndex.has(a.blockId) ? blockOrderIndex.get(a.blockId) : Number.MAX_SAFE_INTEGER
  const bi = blockOrderIndex.has(b.blockId) ? blockOrderIndex.get(b.blockId) : Number.MAX_SAFE_INTEGER
  if (ai !== bi) return ai - bi
  return a.order - b.order
})

/* ---------------------------------- 校验 ---------------------------------- */

const refs = new Set()
for (const question of questions) {
  if (refs.has(question.ref)) fail(`题目 id 重复：${question.ref}`)
  refs.add(question.ref)
  if (!['ds', 'os', 'co', 'cn'].includes(question.subject)) {
    fail(`题目 ${question.ref} 科目非法：${question.subject}`)
  }
  if (question.questionType === 'choice' && !question.answer) {
    fail(`选择题 ${question.ref} 缺少答案`)
  }
}

const imageFiles = []
const collectImages = (dir) => {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) collectImages(full)
    else if (/\.(webp|png|jpe?g|gif|svg)$/i.test(entry.name)) imageFiles.push(full)
  }
}
collectImages(EXAMS_DIR)

for (const question of questions) {
  for (const path of question.imagePaths) {
    const relative = String(path).replace(/^\/+/, '')
    if (!existsSync(join(EXAMS_DIR, relative.replace(/^exams\//, '')))) {
      warnings.push(`题目 ${question.ref} 引用的图片不存在：${path}`)
    }
  }
}

const summary = {
  years: examYears.length,
  questions: questions.length,
  choice: questions.filter((q) => q.questionType === 'choice').length,
  comprehensive: questions.filter((q) => q.questionType !== 'choice').length,
  options: options.length,
  subQuestions: subQuestions.length,
  points: pointOrder.length,
  blocks: blocks.length,
  animations: animationBlocks,
  questionKnowledge: questionKnowledge.length,
  images: imageFiles.length,
}

/* ---------------------------------- 生成 SQL ---------------------------------- */

const lines = []
lines.push('-- 408 考盐：内容种子数据（由 scripts/import-content.mjs 自动生成，请勿手改）')
lines.push(`-- 真题 ${summary.years} 卷 / ${summary.questions} 题（选择题 ${summary.choice}，综合题 ${summary.comprehensive}）`)
lines.push(`-- 知识点 ${summary.points} 个 / 知识块 ${summary.blocks} 块（含动画块 ${summary.animations}）/ 题目-知识关联 ${summary.questionKnowledge} 条`)
lines.push('-- 数据来源：simple408（CC BY 4.0）；真题以 408os 为官方参照。')
lines.push('')
lines.push('SET NAMES utf8mb4;')
lines.push('DELETE FROM question_knowledge;')
lines.push('DELETE FROM question_subquestion;')
lines.push('DELETE FROM question_option;')
lines.push('DELETE FROM question;')
lines.push('DELETE FROM exam_year;')
lines.push('DELETE FROM knowledge_block;')
lines.push('DELETE FROM knowledge_point;')
lines.push('')

lines.push('INSERT INTO knowledge_point (point_id, subject, title, sort_order) VALUES')
lines.push(
  pointOrder
    .map((pointId, index) => `(${sqlStr(pointId)}, ${sqlStr(pointSubjectById.get(pointId))}, ${sqlStr(pointTitleById.get(pointId))}, ${index + 1})`)
    .join(',\n') + ';',
)
lines.push('')

const chunk = 200
for (let i = 0; i < blocks.length; i += chunk) {
  const slice = blocks.slice(i, i + chunk)
  lines.push('INSERT INTO knowledge_block (point_id, block_id, subpoint_id, subpoint_title, block_order, type, content_json) VALUES')
  lines.push(
    slice
      .map((block) => `(${sqlStr(block.pointId)}, ${sqlStr(block.blockId)}, ${sqlStr(block.subpointId)}, ${sqlStr(block.subpointTitle)}, ${block.order}, ${sqlStr(block.type)}, ${sqlStr(JSON.stringify(block.content))})`)
      .join(',\n') + ';',
  )
  lines.push('')
}

for (const exam of examYears) {
  lines.push(`INSERT INTO exam_year (year, title) VALUES (${exam.year}, ${sqlStr(exam.title)});`)
  lines.push('SET @exam_id = LAST_INSERT_ID();')
  const yearQuestions = questions.filter((q) => q.year === exam.year)
  const questionChunk = 100
  for (let i = 0; i < yearQuestions.length; i += questionChunk) {
    const slice = yearQuestions.slice(i, i + questionChunk)
    lines.push('INSERT INTO question (exam_id, exam_ref, exam_number, subject, chapter_name, question_type, score, stem, answer, difficulty, explanation, tags_json, image_paths_json) VALUES')
    lines.push(
      slice
        .map((q) => `(@exam_id, ${sqlStr(q.ref)}, ${q.number}, ${sqlStr(q.subject)}, ${sqlStr(q.chapterName)}, ${sqlStr(q.questionType)}, ${q.score}, ${sqlStr(q.stem)}, ${sqlStr(q.answer)}, ${q.difficulty === null ? 'NULL' : q.difficulty}, ${sqlStr(q.explanation)}, ${sqlJson(q.tags)}, ${sqlJson(q.imagePaths)})`)
        .join(',\n') + ';',
    )
  }
  lines.push('')
}

for (const exam of examYears) {
  lines.push(`SET @exam_id = (SELECT id FROM exam_year WHERE year = ${exam.year});`)
  const yearQuestions = questions.filter((q) => q.year === exam.year)
  for (const question of yearQuestions) {
    lines.push(`SET @qid = (SELECT id FROM question WHERE exam_ref = ${sqlStr(question.ref)});`)
    const questionOptions = options.filter((option) => option.ref === question.ref)
    if (questionOptions.length > 0) {
      lines.push('INSERT INTO question_option (question_id, option_key, option_text) VALUES')
      lines.push(
        questionOptions.map((option) => `(@qid, ${sqlStr(option.key)}, ${sqlStr(option.text)})`).join(',\n') + ';',
      )
    }
    const subs = subQuestions.filter((sub) => sub.ref === question.ref)
    if (subs.length > 0) {
      lines.push('INSERT INTO question_subquestion (question_id, sub_number, score, description) VALUES')
      lines.push(
        subs.map((sub) => `(@qid, ${sub.number}, ${sub.score === null ? 'NULL' : sub.score}, ${sqlStr(sub.description)})`).join(',\n') + ';',
      )
    }
    const links = questionKnowledge.filter((link) => link.ref === question.ref)
    if (links.length > 0) {
      lines.push('INSERT IGNORE INTO question_knowledge (question_id, block_id) VALUES')
      lines.push(links.map((link) => `(@qid, ${sqlStr(link.blockId)})`).join(',\n') + ';')
    }
  }
  lines.push('')
}

mkdirSync(dirname(OUT_SQL), { recursive: true })
writeFileSync(OUT_SQL, lines.join('\n'), 'utf8')

/* ---------------------------------- 报告 ---------------------------------- */

console.log('内容导入检查报告')
console.log('─'.repeat(48))
for (const [key, value] of Object.entries(summary)) {
  console.log(`  ${key.padEnd(18)} ${value}`)
}
console.log(`  SQL 文件           ${OUT_SQL.replace(ROOT + '\\', '')}`)
console.log(`  文件大小           ${(readFileSync(OUT_SQL).length / 1024 / 1024).toFixed(2)} MB`)

if (warnings.length) {
  console.log(`\n警告 ${warnings.length} 条（前 10）:`)
  warnings.slice(0, 10).forEach((warning) => console.log(`  - ${warning}`))
}
if (problems.length) {
  console.error(`\n错误 ${problems.length} 条（前 20）:`)
  problems.slice(0, 20).forEach((problem) => console.error(`  - ${problem}`))
  process.exit(1)
}

/* ---------------------------------- 入库 ---------------------------------- */

if (process.argv.includes('--apply')) {
  const { createConnection } = await import('mysql2/promise')
  const connection = await createConnection({
    host: process.env.DB_HOST ?? '127.0.0.1',
    port: Number(process.env.DB_PORT ?? 3306),
    user: process.env.DB_USER ?? 'kaoyan',
    password: process.env.DB_PASSWORD ?? '123456',
    database: process.env.DB_NAME ?? 'kaoyan_db',
    multipleStatements: true,
    charset: 'utf8mb4',
  })
  try {
    const sql = readFileSync(OUT_SQL, 'utf8')
    await connection.query(sql)
    const [[counts]] = await connection.query(
      `SELECT (SELECT COUNT(*) FROM question) AS questions,
              (SELECT COUNT(*) FROM question_option) AS options,
              (SELECT COUNT(*) FROM knowledge_point) AS points,
              (SELECT COUNT(*) FROM knowledge_block) AS blocks,
              (SELECT COUNT(*) FROM question_knowledge) AS links`,
    )
    console.log('\n已写入数据库 kaoyan_db：', counts)
  } finally {
    await connection.end()
  }
} else {
  console.log('\n仅生成 SQL。加 --apply 直接写入本地数据库。')
}