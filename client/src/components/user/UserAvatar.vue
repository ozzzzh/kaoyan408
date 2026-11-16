<script setup lang="ts">
import { computed } from 'vue'

/**
 * 账号头像。
 *
 * 没有图片上传：头像只有两种来源——按用户名哈希生成的首字母 + 配色，
 * 或从固定 emoji 库里选一个（后端存 'emoji:🦊'）。
 * 这样不需要对象存储、不需要上传接口，nginx 也不用多挂目录。
 */
const props = withDefaults(
  defineProps<{
    name: string
    avatar?: string | null
    /** 直径（px） */
    size?: number
  }>(),
  { avatar: 'auto', size: 48 },
)

const COLORS = ['#3D6BE5', '#7C5CF0', '#F0A020', '#E0574F', '#2E9E7A', '#B4577E']

const emoji = computed(() => {
  const value = props.avatar ?? 'auto'
  return value.startsWith('emoji:') ? value.slice('emoji:'.length) : ''
})

const initial = computed(() => (props.name || '?').trim().slice(0, 1).toUpperCase())

/** 同名同色：把用户名哈希到一个固定色板，避免每次刷新换色。 */
const color = computed(() => {
  let hash = 0
  for (const ch of props.name || '') {
    hash = (hash * 31 + (ch.codePointAt(0) ?? 0)) % 997
  }
  return COLORS[hash % COLORS.length]
})

const boxStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  backgroundColor: color.value,
  fontSize: `${Math.round(props.size * 0.42)}px`,
}))
</script>

<template>
  <span
    class="grid shrink-0 place-items-center rounded-xl font-bold leading-none text-white"
    :style="boxStyle"
    aria-hidden="true"
  >
    <span v-if="emoji" class="emoji">{{ emoji }}</span>
    <span v-else>{{ initial }}</span>
  </span>
</template>

<style scoped>
.emoji {
  font-family: 'Apple Color Emoji', 'Segoe UI Emoji', 'Noto Color Emoji', sans-serif;
  line-height: 1;
}
</style>
