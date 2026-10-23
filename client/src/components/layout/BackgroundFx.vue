<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 鼠标跟随背景（工作台版）。
 * 与首页版相比刻意调淡、调慢：工作台是密集信息页，动效只做质感、不抢注意力。
 * 把指针坐标写进 CSS 变量 --mx / --my，光斑与网格 mask 跟着走。
 */
const bgStyle = ref<Record<string, string>>({ '--mx': '50%', '--my': '18%' })
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

/* 光标附近的极淡光斑 */
.bg-spot {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    520px circle at var(--mx, 50%) var(--my, 18%),
    rgba(94, 179, 178, 0.07),
    transparent 62%
  );
  transition: background 0.45s ease-out;
}

/* 光标附近的网格：只在指针周围显现，透明度压到很低 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(122, 184, 196, 0.09) 1px, transparent 1px),
    linear-gradient(90deg, rgba(122, 184, 196, 0.09) 1px, transparent 1px);
  background-size: 56px 56px;
  -webkit-mask-image: radial-gradient(
    circle at var(--mx, 50%) var(--my, 18%),
    black 0%,
    transparent 32%
  );
  mask-image: radial-gradient(
    circle at var(--mx, 50%) var(--my, 18%),
    black 0%,
    transparent 32%
  );
}

@media (prefers-reduced-motion: reduce) {
  .bg-spot,
  .bg-grid {
    transition: none;
  }
}
</style>