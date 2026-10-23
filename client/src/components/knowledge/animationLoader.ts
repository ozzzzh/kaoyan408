/**
 * 动画按需加载：animationId → 动态 import 其所在模块。
 *
 * <p>为什么不用静态 import 建注册表：manim-web + three 引擎约 1.8MB，
 * 静态引入会让「打开任意知识正文」都下载整个引擎，
 * 即使这篇文章一个动画都没有（实测 LearnView chunk 曾达 1.86MB）。
 *
 * <p>改为动态 import 后：引擎与动画代码进入独立 chunk，
 * 只有正文里真的出现 animation 块时才拉取。
 *
 * <p>模块路径是字面量（而非拼接变量），Vite 才能静态分析并正确切分。
 */

type AnimationModule = Record<string, unknown>

/** animationId → () => import(模块) */
const LOADERS: Record<string, () => Promise<AnimationModule>> = {
  // 计算机网络
  'crc-division': () =>
    import('../../animations/computer-networks/data-link-layer/crc-division'),
  'csma-cd-collision': () =>
    import('../../animations/computer-networks/data-link-layer/csma-cd-collision'),
  'gbn-error-control': () =>
    import('../../animations/computer-networks/data-link-layer/go-back-n/error-control'),
  'gbn-retransmission': () =>
    import('../../animations/computer-networks/data-link-layer/go-back-n/retransmission'),
  'gbn-window': () =>
    import('../../animations/computer-networks/data-link-layer/go-back-n/send-window'),
  'sr-error-control': () =>
    import('../../animations/computer-networks/data-link-layer/selective-repeat/error-control'),
  'arp-process': () => import('../../animations/computer-networks/network-layer/arp-process'),
  'dhcp-process': () => import('../../animations/computer-networks/network-layer/dhcp-process'),
  'subnet-vlsm': () => import('../../animations/computer-networks/network-layer/subnet-vlsm'),
  'tcp-seq-growth': () =>
    import('../../animations/computer-networks/transport-layer/tcp-seq-growth'),

  // 计算机组成原理
  'alu-add-sub-flags-circuit': () =>
    import('../../animations/computer-organization/arithmetic/alu-flags-circuit'),
  'compare-before-subtract-division-circuit': () =>
    import('../../animations/computer-organization/arithmetic/division'),
  'sequential-multiplication-circuit': () =>
    import('../../animations/computer-organization/arithmetic/multiplication'),
  'weight-position': () =>
    import('../../animations/computer-organization/base-conversion/weight-position'),
  'two-way-set-associative-cache-match': () =>
    import('../../animations/computer-organization/cache/two-way-set-associative-cache'),
  'instruction-cycle-datapath': () =>
    import('../../animations/computer-organization/execute/instruction-cycle'),
  'left-shift-immediate-datapath': () =>
    import('../../animations/computer-organization/execute/instruction-cycle'),
  'nested-interrupt-priority': () =>
    import('../../animations/computer-organization/interrupt/interrupt-priority'),
  'two-level-virtual-memory-translation': () =>
    import('../../animations/computer-organization/vm/virtual-memory-translation'),

  // 数据结构
  'topological-sort': () => import('../../animations/data-structures/graph/dag-algorithms'),
  'critical-path': () => import('../../animations/data-structures/graph/dag-algorithms'),
  dijkstra: () => import('../../animations/data-structures/graph/shortest-path'),
  floyd: () => import('../../animations/data-structures/graph/shortest-path'),
  'fast-slow-pointer-applications': () =>
    import('../../animations/data-structures/linear-list/fast-slow-pointers'),
  'sequential-list-insert-delete': () =>
    import('../../animations/data-structures/linear-list/sequential-list'),
  'singly-linked-list-insert-delete': () =>
    import('../../animations/data-structures/linear-list/singly-linked-list'),
  'quick-sort': () => import('../../animations/data-structures/sorting/advanced-sorts'),
  'heap-sort': () => import('../../animations/data-structures/sorting/advanced-sorts'),
  'radix-sort': () => import('../../animations/data-structures/sorting/advanced-sorts'),
  'loser-tree': () => import('../../animations/data-structures/sorting/external-sorts'),
  'replacement-selection': () =>
    import('../../animations/data-structures/sorting/external-sorts'),
  'selection-sort': () => import('../../animations/data-structures/sorting/incremental-sorts'),
  'bubble-sort': () => import('../../animations/data-structures/sorting/incremental-sorts'),
  'direct-insertion-sort': () =>
    import('../../animations/data-structures/sorting/incremental-sorts'),
  'binary-insertion-sort': () =>
    import('../../animations/data-structures/sorting/incremental-sorts'),
  'merge-sort': () => import('../../animations/data-structures/sorting/incremental-sorts'),
  'shell-sort': () => import('../../animations/data-structures/sorting/incremental-sorts'),
  'prefix-postfix-evaluation': () =>
    import('../../animations/data-structures/stack-expression/expression-evaluation'),
  'infix-postfix-conversion': () =>
    import('../../animations/data-structures/stack-expression/infix-postfix'),
  'kmp-match-and-next': () => import('../../animations/data-structures/string/kmp'),
  'kmp-nextval': () => import('../../animations/data-structures/string/kmp'),
  'avl-adjustment': () => import('../../animations/data-structures/tree/avl'),
  'b-tree-insertion': () => import('../../animations/data-structures/tree/b-tree'),
  'b-tree-deletion': () => import('../../animations/data-structures/tree/b-tree'),
  'rebuild-binary-tree-from-traversals': () =>
    import('../../animations/data-structures/tree/rebuild-from-traversals'),
  'red-black-insertion': () => import('../../animations/data-structures/tree/red-black-tree'),
}

/** 已解析的动画缓存，避免同页重复加载。 */
const cache = new Map<string, unknown>()

/** 待进行的加载，避免同一动画并发触发多次 import。 */
const pending = new Map<string, Promise<unknown>>()

/**
 * 按 animationId 取回动画对象。
 * 找不到 id 或模块内没有该动画时返回 null（由调用方显示占位）。
 */
export async function loadAnimation(id: string | null | undefined): Promise<unknown | null> {
  if (!id) return null
  if (cache.has(id)) return cache.get(id) ?? null

  const loader = LOADERS[id]
  if (!loader) return null

  if (pending.has(id)) return pending.get(id) ?? null

  const task = (async () => {
    try {
      const module = await loader()
      // 模块里可能导出多个动画，按 id 字段挑出目标
      for (const value of Object.values(module)) {
        const candidate = value as { id?: string } | null
        if (candidate && typeof candidate === 'object' && candidate.id === id) {
          cache.set(id, candidate)
          return candidate
        }
      }
      return null
    } catch {
      return null
    } finally {
      pending.delete(id)
    }
  })()

  pending.set(id, task)
  return task
}

/** 该 id 是否有对应的加载器（用于快速判断能否渲染，不发请求）。 */
export function hasAnimation(id: string | null | undefined): boolean {
  return !!id && id in LOADERS
}

/** 已登记的 animationId 总数（用于校验脚本与调试）。 */
export function registeredAnimationIds(): string[] {
  return Object.keys(LOADERS).sort()
}