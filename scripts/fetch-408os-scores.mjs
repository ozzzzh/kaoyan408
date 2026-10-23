#!/usr/bin/env node
/**
 * 从 408os 拉取 18 卷真题的分值与答案（用户指定 408os 为准），
 * 与本地 paper.json 逐题比对，输出差异报告。
 *
 * 只读：仅调用 list-with-details，不提交答案、不改动远端数据。
 */
import { writeFileSync } from 'node:fs'

const YEARS = [2009, 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026]
const API = 'https://api.408os.cn/api/zt/question/list-with-details?year='

const result = {}

for (const year of YEARS) {
  const response = await fetch(API + year)
  const json = await response.json()
  if (json.code !== 200 || !Array.isArray(json.data)) {
    console.error(`${year}: 拉取失败 code=${json.code} msg=${json.msg}`)
    continue
  }
  const questions = json.data.map((item) => ({
    index: item.question.questionIndex,
    id: item.question.id,
    score: item.question.score,
    answer: item.question.answer,
    type: item.question.questionType,
  }))
  result[year] = questions
  const total = questions.reduce((sum, q) => sum + (q.score ?? 0), 0)
  const choice = questions.filter((q) => q.type === 1)
  const comp = questions.filter((q) => q.type !== 1)
  const choiceSum = choice.reduce((sum, q) => sum + (q.score ?? 0), 0)
  const compSum = comp.reduce((sum, q) => sum + (q.score ?? 0), 0)
  console.log(
    `${year}: ${questions.length}题  选择${choice.length}(${choiceSum}分)  综合${comp.length}(${compSum}分)  总分${total}`,
  )
}

writeFileSync('scripts/408os-scores.json', JSON.stringify(result, null, 2), 'utf8')
console.log('\n已保存 scripts/408os-scores.json')