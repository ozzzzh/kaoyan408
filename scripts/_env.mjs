/**
 * 回归脚本共用的环境配置。
 *
 * 本地默认值与本机开发环境一致（root/123456 @ 127.0.0.1:3306），
 * CI 里通过环境变量覆盖，避免把 CI 专用凭据写死在脚本里。
 */
import { createConnection } from 'mysql2/promise'

export const DB = {
  host: process.env.DB_HOST ?? '127.0.0.1',
  port: Number(process.env.DB_PORT ?? 3306),
  user: process.env.DB_USER ?? 'root',
  password: process.env.DB_PASSWORD ?? '123456',
  database: process.env.DB_NAME ?? 'kaoyan_db',
  charset: 'utf8mb4',
}

export const API_BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1'

/** 回归用的测试账号（CI 会先注册，本地已存在）。 */
export const TEST_USER = {
  username: process.env.TEST_USERNAME ?? 'tester01',
  password: process.env.TEST_PASSWORD ?? 'test123456',
}

export function connect() {
  return createConnection(DB)
}

/** 连接数据库并执行查询，自动关闭连接。 */
export async function query(sql, params) {
  const connection = await createConnection(DB)
  try {
    const [rows] = await connection.query(sql, params)
    return rows
  } finally {
    await connection.end()
  }
}

/** 登录测试账号，返回 Authorization 头。 */
export async function authHeaders() {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(TEST_USER),
  })
  const payload = await response.json()
  const token = payload?.data?.token
  if (!token) {
    throw new Error(
      `登录失败（${TEST_USER.username}）：${payload?.msg ?? '未知错误'}。` +
        `CI 中请先注册该账号。`,
    )
  }
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
}