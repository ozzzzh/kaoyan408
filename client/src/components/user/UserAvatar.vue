<script setup lang="ts">
import { computed } from 'vue'

/**
 * 账号头像。
 *
 * 没有图片上传：头像只有三种来源——
 *   1. `auto`：品牌绿底 + 昵称首字（默认）；
 *   2. `color:#rrggbb`：用户自选底色 + 昵称首字；
 *   3. `emoji:🦊`：自选图案（emoji 显示在底色上）。
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

/**
 * 默认底色 = 站点品牌绿（与 --k-accent 一致）。
 *
 * 原来是按昵称哈希从六种颜色里挑一个，于是同一个页面里会出现玫红、橙、紫各种头像，
 * 和整体青绿主题不搭（实测昵称「111111」被分到了玫红）。现在默认统一成品牌绿，
 * 想区分的人去「编辑资料」里自选颜色。
 */
const BRAND_COLOR = '#0b7c73'
const COLOR_PREFIX = 'color:'
const HEX = /^#[0-9a-fA-F]{6}$/

const emoji = computed(() => {
  const value = props.avatar ?? 'auto'
  return value.startsWith('emoji:') ? value.slice('emoji:'.length) : ''
})

/** 用户自选颜色；非法值（历史脏数据）回落到品牌绿，不抛错。 */
const color = computed(() => {
  const value = props.avatar ?? 'auto'
  if (!value.startsWith(COLOR_PREFIX)) return BRAND_COLOR
  const hex = value.slice(COLOR_PREFIX.length).trim()
  return HEX.test(hex) ? hex : BRAND_COLOR
})

const initial = computed(() => (props.name || '?').trim().slice(0, 1).toUpperCase())

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
