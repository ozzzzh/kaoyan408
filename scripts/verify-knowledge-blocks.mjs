/**
 * 校验题目接口新增的 knowledgeBlocks 字段。
 * 用 Node 读取（正确处理 UTF-8），避免 PowerShell 控制台编码干扰。
 */
const BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1'

const response = await fetch(`${BASE}/questions/exam-2026-1`)
const payload = await response.json()

if (payload.code !== 0) {
  console.log(`❌ 接口返回 code=${payload.code} msg=${payload.msg}`)
  process.exit(1)
}

const blocks = payload.data.knowledgeBlocks
console.log(`knowledgeBlocks 数量: ${blocks?.length ?? 0}`)
console.log('')

for (const block of blocks ?? []) {
  console.log(`  blockId       = ${block.blockId}`)
  console.log(`  pointId       = ${block.pointId}`)
  console.log(`  pointTitle    = ${block.pointTitle}`)
  console.log(`  subpointTitle = ${block.subpointTitle}`)
  console.log(`  label         = ${block.label}`)
  console.log('')
}

// 断言：label 不应是裸的 blockId，且应含中文
const ok = (blocks ?? []).every(
  (b) => b.label && b.label !== b.blockId && /[\u4e00-\u9fa5]/.test(b.label),
)
console.log(ok ? '✅ label 均为可读中文名称，且不再是裸 blockId' : '❌ label 校验未通过')

// 顺带确认旧的 knowledgeBlockIds 仍保留（前端其它位置可能还在用）
console.log(
  `旧字段 knowledgeBlockIds 仍在: ${Array.isArray(payload.data.knowledgeBlockIds)} (${payload.data.knowledgeBlockIds?.join(', ')})`,
)
process.exit(ok ? 0 : 1)