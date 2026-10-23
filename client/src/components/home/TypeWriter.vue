<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 打字机副标题：逐字打出 → 停留 → 倒退删除 → 换下一句。
 */
const ROTATING_LINES = [
  '真题 2009–2026 全 18 卷，每道题都挂到知识点上',
  '做题 → 看正文 → 看掌握度，复习形成闭环',
  '846 道正式真题 · 147 个知识点 · 42 个交互动画',
  '用更少的内容，建立更清晰的知识体系',
]

const typedText = ref('')
type Phase = 'typing' | 'holding' | 'deleting' | 'wait'
let phase: Phase = 'typing'
let lineIndex = 0
let charIndex = 0
let timer: ReturnType<typeof setTimeout> | null = null

function schedule(delay: number) {
  if (timer) clearTimeout(timer)
  timer = setTimeout(step, delay)
}

function step() {
  const full = ROTATING_LINES[lineIndex]
  if (phase === 'typing') {
    charIndex += 1
    typedText.value = full.slice(0, charIndex)
    if (charIndex >= full.length) {
      phase = 'holding'
      schedule(2500)
    } else {
      schedule(70 + Math.random() * 60)
    }
  } else if (phase === 'holding') {
    phase = 'deleting'
    schedule(80)
  } else if (phase === 'deleting') {
    charIndex -= 1
    typedText.value = full.slice(0, charIndex)
    if (charIndex <= 0) {
      lineIndex = (lineIndex + 1) % ROTATING_LINES.length
      phase = 'wait'
      schedule(400)
    } else {
      schedule(35)
    }
  } else {
    phase = 'typing'
    step()
  }
}

onMounted(() => schedule(220))
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <div class="typed-wrap">
    <p class="typed-line">
      <span>{{ typedText }}</span><span class="caret">|</span>
    </p>
  </div>
</template>

<style scoped>
.typed-wrap {
  min-height: calc(25px * 1.7 + 4px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.typed-line {
  margin: 0;
  font-size: clamp(16px, 1.8vw, 24px);
  line-height: 1.7;
  color: #465979;
  white-space: nowrap;
  overflow: hidden;
}
.typed-line .caret {
  display: inline-block;
  margin-left: 2px;
  color: #2c5de0;
  animation: caret-blink 1s steps(1, end) infinite;
}
@media (max-width: 768px) {
  .typed-line {
    font-size: 15px;
    white-space: normal;
  }
}
@keyframes caret-blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}
</style>