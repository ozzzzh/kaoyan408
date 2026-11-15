import { describe, expect, it } from 'vitest'
import { ApiError, isUnauthorized } from './client'

/**
 * 401 判断的回归测试。
 *
 * 为什么值得单独测：这个项目的接口一律返回 HTTP 200，登录失效体现在响应体的
 * `code` 上。所以「是不是没登录」不能靠 HTTP 状态判断，也不能靠错误文案匹配
 * （文案一改就失效）。这里锁住的是「只认业务码 401」这一条。
 */
describe('isUnauthorized', () => {
  it('业务码 401 判为未登录', () => {
    expect(isUnauthorized(new ApiError(401, '背题模式需要登录后使用。'))).toBe(true)
  })

  it('限流、服务端错误、参数错误都不算未登录', () => {
    expect(isUnauthorized(new ApiError(429, '请求过于频繁'))).toBe(false)
    expect(isUnauthorized(new ApiError(500, '系统异常'))).toBe(false)
    expect(isUnauthorized(new ApiError(400, '参数校验失败'))).toBe(false)
  })

  it('网络异常（code=-1）不算未登录', () => {
    expect(isUnauthorized(new ApiError(-1, '网络异常'))).toBe(false)
  })

  it('普通 Error 不算未登录', () => {
    expect(isUnauthorized(new Error('boom'))).toBe(false)
    expect(isUnauthorized(null)).toBe(false)
    expect(isUnauthorized('401')).toBe(false)
  })
})
