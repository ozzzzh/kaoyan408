<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 鼠标跟随背景：把指针坐标写进 CSS 变量 --mx / --my，
 * 底层径向光斑与上层网格 mask 都跟着走。
 */
const bgStyle = ref<Record<string, string>>({ '--mx': '50%', '--my': '33%' })
let raf = 0

function onMouseMove(event: MouseEvent) {
  if (raf) return
  raf = requestAnimationFrame(() => {
    bgStyle.value = {
      '--mx': ((event.clientX / window.innerWidth) * 100).toFixed(2) + '%',
      '--my': ((event.clientY / window.innerHeight) * 100).toFixed(2) + '%',
    }
    raf = 0
  })
}

onMounted(() => window.addEventListener('mousemove', onMouseMove, { passive: true }))
onBeforeUnmount(() => {
  if (raf) cancelAnimationFrame(raf)
  window.removeEventListener('mousemove', onMouseMove)
})
</script>

<template>
  <div class="bg-layer" :style="bgStyle" aria-hidden="true">
    <div class="bg-base"></div>
    <div class="bg-spot"></div>
    <div class="bg-grid"></div>
  </div>
</template>

<style scoped>
.bg-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-base {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(
      circle at var(--mx, 50%) var(--my, 33%),
      rgba(94, 179, 178, 0.12),
      transparent 28%
    ),
    linear-gradient(180deg, #fbfdff, #f6faff);
  transition: background 0.4s ease-out;
}

.bg-spot {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    420px circle at var(--mx, 50%) var(--my, 33%),
    rgba(122, 184, 196, 0.12),
    transparent 60%
  );
  transition: background 0.2s ease-out;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(122, 184, 196, 0.1) 1px, transparent 1px),
    linear-gradient(90deg, rgba(122, 184, 196, 0.1) 1px, transparent 1px);
  background-size: 48px 48px;
  -webkit-mask-image: radial-gradient(
    circle at var(--mx, 50%) var(--my, 33%),
    black 0%,
    transparent 35%
  );
  mask-image: radial-gradient(
    circle at var(--mx, 50%) var(--my, 33%),
    black 0%,
    transparent 35%
  );
}
</style>