/**
 * 动画注册表：animationId → ManimWebAnimation。
 *
 * 后端只存动画的元信息（id / ariaLabel / 步骤 id），因为渲染函数无法序列化。
 * 真正的渲染函数来自本地动画模块，这里在首屏按需收集并建立索引，
 * 供知识正文的 animation 块按 id 取回。
 */
import type { ManimWebAnimation } from '../../animations/types'

/** 各科目的动画模块（静态 import，由 Vite 打包进独立 chunk）。 */
import { crcDivisionAnimation } from '../../animations/computer-networks/data-link-layer/crc-division'
import { csmaCdCollisionAnimation } from '../../animations/computer-networks/data-link-layer/csma-cd-collision'
import { gbnErrorControlAnimation } from '../../animations/computer-networks/data-link-layer/go-back-n/error-control'
import { gbnRetransmissionAnimation } from '../../animations/computer-networks/data-link-layer/go-back-n/retransmission'
import { gbnWindowAnimation } from '../../animations/computer-networks/data-link-layer/go-back-n/send-window'
import { srErrorControlAnimation } from '../../animations/computer-networks/data-link-layer/selective-repeat/error-control'
import { arpProcessAnimation } from '../../animations/computer-networks/network-layer/arp-process'
import { dhcpProcessAnimation } from '../../animations/computer-networks/network-layer/dhcp-process'
import { subnetVlsmAnimation } from '../../animations/computer-networks/network-layer/subnet-vlsm'
import { tcpSeqGrowthAnimation } from '../../animations/computer-networks/transport-layer/tcp-seq-growth'

import { aluFlagsCircuitAnimation } from '../../animations/computer-organization/arithmetic/alu-flags-circuit'
import { divisionAnimation } from '../../animations/computer-organization/arithmetic/division'
import { multiplicationAnimation } from '../../animations/computer-organization/arithmetic/multiplication'
import { weightPositionAnimation } from '../../animations/computer-organization/base-conversion/weight-position'
import { twoWaySetAssociativeCacheAnimation } from '../../animations/computer-organization/cache/two-way-set-associative-cache'
import { executeAnimation, shiftImmediateAnimation } from '../../animations/computer-organization/execute/instruction-cycle'
import { interruptPriorityAnimation } from '../../animations/computer-organization/interrupt/interrupt-priority'
import { virtualMemoryTranslationAnimation } from '../../animations/computer-organization/vm/virtual-memory-translation'

import { topologicalSortAnimation, criticalPathAnimation } from '../../animations/data-structures/graph/dag-algorithms'
import { dijkstraAnimation, floydAnimation } from '../../animations/data-structures/graph/shortest-path'
import { fastSlowPointersAnimation } from '../../animations/data-structures/linear-list/fast-slow-pointers'
import { sequentialListInsertDeleteAnimation } from '../../animations/data-structures/linear-list/sequential-list'
import { singlyLinkedListInsertDeleteAnimation } from '../../animations/data-structures/linear-list/singly-linked-list'
import { quickSortAnimation, heapSortAnimation, radixSortAnimation } from '../../animations/data-structures/sorting/advanced-sorts'
import { loserTreeAnimation, replacementSelectionAnimation } from '../../animations/data-structures/sorting/external-sorts'
import {
  selectionSortAnimation,
  bubbleSortAnimation,
  directInsertionSortAnimation,
  binaryInsertionSortAnimation,
  mergeSortAnimation,
  shellSortAnimation,
} from '../../animations/data-structures/sorting/incremental-sorts'
import { prefixPostfixEvaluationAnimation } from '../../animations/data-structures/stack-expression/expression-evaluation'
import { infixPostfixConversionAnimation } from '../../animations/data-structures/stack-expression/infix-postfix'
import { kmpAnimation, kmpImprovedAnimation } from '../../animations/data-structures/string/kmp'
import { avlAdjustmentAnimation } from '../../animations/data-structures/tree/avl'
import { bTreeInsertionAnimation, bTreeDeletionAnimation } from '../../animations/data-structures/tree/b-tree'
import { rebuildBinaryTreeAnimation } from '../../animations/data-structures/tree/rebuild-from-traversals'
import { redBlackInsertionAnimation } from '../../animations/data-structures/tree/red-black-tree'

const ALL: ManimWebAnimation[] = [
  crcDivisionAnimation,
  csmaCdCollisionAnimation,
  gbnErrorControlAnimation,
  gbnRetransmissionAnimation,
  gbnWindowAnimation,
  srErrorControlAnimation,
  arpProcessAnimation,
  dhcpProcessAnimation,
  subnetVlsmAnimation,
  tcpSeqGrowthAnimation,
  aluFlagsCircuitAnimation,
  divisionAnimation,
  multiplicationAnimation,
  weightPositionAnimation,
  twoWaySetAssociativeCacheAnimation,
  executeAnimation,
  shiftImmediateAnimation,
  interruptPriorityAnimation,
  virtualMemoryTranslationAnimation,
  topologicalSortAnimation,
  criticalPathAnimation,
  dijkstraAnimation,
  floydAnimation,
  fastSlowPointersAnimation,
  sequentialListInsertDeleteAnimation,
  singlyLinkedListInsertDeleteAnimation,
  quickSortAnimation,
  heapSortAnimation,
  radixSortAnimation,
  loserTreeAnimation,
  replacementSelectionAnimation,
  selectionSortAnimation,
  bubbleSortAnimation,
  directInsertionSortAnimation,
  binaryInsertionSortAnimation,
  mergeSortAnimation,
  shellSortAnimation,
  prefixPostfixEvaluationAnimation,
  infixPostfixConversionAnimation,
  kmpAnimation,
  kmpImprovedAnimation,
  avlAdjustmentAnimation,
  bTreeInsertionAnimation,
  bTreeDeletionAnimation,
  rebuildBinaryTreeAnimation,
  redBlackInsertionAnimation,
]

/** id → 动画。同一模块导出的多个动画各自登记。 */
const registry = new Map<string, ManimWebAnimation>()
for (const animation of ALL) {
  if (animation?.id) {
    registry.set(animation.id, animation)
  }
}

export function getAnimation(id: string | null | undefined): ManimWebAnimation | null {
  if (!id) return null
  return registry.get(id) ?? null
}

export function animationCount(): number {
  return registry.size
}

export function allAnimationIds(): string[] {
  return [...registry.keys()].sort()
}