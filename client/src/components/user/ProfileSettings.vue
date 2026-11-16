<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, ApiError } from '@/api/client'
import { useUserStore } from '@/store/user'
import UserAvatar from './UserAvatar.vue'

/**
 * 账号资料与安全设置。
 *
 * 三条边界（都在服务端兜底，这里只是提前禁用 + 说明）：
 *   1. 演示账号（demo / demo-admin）的资料与密码都不可改 —— 它们是公开账号；
 *   2. username 是登录名（JWT subject），只读；能改的是昵称；
 *   3. 改密码必须邮箱验证码，成功后**其它设备全部掉线**，当前会话换用新 token。
 */
const userStore = useUserStore()
const user = computed(() => userStore.user)

const EMOJIS = ['🦊', '🐼', '🐧', '🦉', '🐳', '🌱', '🍀', '⭐', '🚀', '📘', '🧠', '☕']

const editing = ref(false)
const draftName = ref('')
const draftAvatar = ref('auto')
const saving = ref(false)
const message = ref<string | null>(null)
const error = ref<string | null>(null)

const showPassword = ref(false)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const code = ref('')
const sending = ref(false)
const cooldown = ref(0)
const changing = ref(false)

const canChangePassword = computed(() => Boolean(user.value && !user.value.demo && user.value.hasEmail))

function startEdit() {
  draftName.value = user.value?.displayName ?? user.value?.username ?? ''
  draftAvatar.value = user.value?.avatar ?? 'auto'
  editing.value = true
  message.value = null
  error.value = null
}

function pickEmoji(emoji: string) {
  draftAvatar.value = `emoji:${emoji}`
}

async function save() {
  if (saving.value) return
  saving.value = true
  error.value = null
  message.value = null
  try {
    const updated = await api.updateProfile({
      displayName: draftName.value.trim(),
      avatar: draftAvatar.value,
    })
    userStore.applyUser({
      displayName: updated.displayName,
      avatar: updated.avatar,
    })
    editing.value = false
    message.value = '资料已更新'
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function sendCode() {
  if (!user.value?.email || sending.value || cooldown.value > 0) return
  sending.value = true
  error.value = null
  try {
    const result = await api.sendMailCode(user.value.email, 'reset-password')
    cooldown.value = result.cooldownSeconds ?? 60
    message.value = result.mock
      ? '验证码已生成（当前为演示模式，验证码打印在服务端日志里）'
      : '验证码已发送到你的邮箱'
    const timer = setInterval(() => {
      cooldown.value -= 1
      if (cooldown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '发送验证码失败'
  } finally {
    sending.value = false
  }
}

async function submitPassword() {
  if (changing.value) return
  error.value = null
  message.value = null
  if (!currentPassword.value || !newPassword.value) {
    error.value = '请填写当前密码与新密码。'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致。'
    return
  }
  if (!code.value.trim()) {
    error.value = '请填写邮箱验证码。'
    return
  }
  changing.value = true
  try {
    const result = await api.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
      code: code.value.trim(),
    })
    // 旧 token 已失效，换上新 token 这把才不会被踢下线
    userStore.applyToken(result.token)
    userStore.applyUser({ displayName: result.user.displayName, avatar: result.user.avatar })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    code.value = ''
    showPassword.value = false
    message.value = '密码已修改，其它设备已全部下线'
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '修改密码失败'
  } finally {
    changing.value = false
  }
}

const field =
  'mt-1 w-full rounded-lg border border-[var(--k-border)] bg-[var(--k-surface)] px-3 py-2 text-sm text-[var(--k-text)] outline-none focus:border-[var(--k-accent)]'
</script>

<template>
  <div v-if="user" class="space-y-4">
    <!-- 提示 -->
    <div
      v-if="message || error"
      class="flex flex-wrap items-center justify-between gap-2 rounded-lg border px-4 py-2.5 text-sm"
      :class="
        error
          ? 'border-[#8a6d1f]/40 bg-[var(--k-warn-soft)] text-[var(--k-warn)]'
          : 'border-[var(--k-accent)]/40 bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]'
      "
    >
      <span class="min-w-0 break-all">{{ error ?? message }}</span>
      <button type="button" class="shrink-0 text-xs font-semibold underline" @click="message = null; error = null">
        知道了
      </button>
    </div>

    <!-- 账号与资料 -->
    <section id="profile" class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)]">
      <div class="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--k-border)] px-5 py-3">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">账号与资料</h2>
        <button
          v-if="!editing"
          type="button"
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs font-semibold text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="user.demo"
          :title="user.demo ? '演示账号的资料已锁定' : '修改昵称与头像'"
          @click="startEdit"
        >
          编辑资料
        </button>
      </div>

      <div class="space-y-3 px-5 py-4">
        <div class="flex items-center gap-3">
          <UserAvatar :name="user.displayName || user.username" :avatar="user.avatar" :size="52" />
          <div class="min-w-0">
            <p class="flex flex-wrap items-center gap-2 text-base font-semibold text-[var(--k-text)]">
              <span class="truncate">{{ user.displayName || user.username }}</span>
              <span
                v-if="user.isAdmin"
                class="rounded bg-[var(--k-accent-soft)] px-1.5 py-0.5 text-[10px] font-bold text-[var(--k-accent-text)]"
              >管理员</span>
              <span
                v-if="user.demo"
                class="rounded bg-[var(--k-warn-soft)] px-1.5 py-0.5 text-[10px] font-bold text-[var(--k-warn)]"
              >演示账号</span>
            </p>
            <p class="mt-0.5 truncate text-xs text-[var(--k-text-faint)]">
              {{ user.email || '未绑定邮箱' }}
            </p>
          </div>
        </div>

        <dl class="grid gap-2 text-xs sm:grid-cols-2">
          <div class="rounded-lg bg-[var(--k-surface-soft)] px-3 py-2">
            <dt class="text-[11px] text-[var(--k-text-faint)]">用户名（登录用，不可修改）</dt>
            <dd class="mt-0.5 font-mono text-[var(--k-text)]">{{ user.username }}</dd>
          </div>
          <div class="rounded-lg bg-[var(--k-surface-soft)] px-3 py-2">
            <dt class="text-[11px] text-[var(--k-text-faint)]">邮箱</dt>
            <dd class="mt-0.5 truncate text-[var(--k-text)]">{{ user.email || '未绑定' }}</dd>
          </div>
        </dl>

        <!-- 编辑区 -->
        <div v-if="editing" class="space-y-3 rounded-lg border border-[var(--k-border)] p-3">
          <label class="block text-sm">
            <span class="text-[var(--k-text-muted)]">昵称</span>
            <input v-model="draftName" :class="field" maxlength="20" placeholder="2-20 个字符" />
          </label>

          <div>
            <p class="text-sm text-[var(--k-text-muted)]">头像</p>
            <div class="mt-1.5 flex flex-wrap items-center gap-1.5">
              <button
                type="button"
                class="rounded-lg border px-2.5 py-1 text-xs transition"
                :class="
                  draftAvatar === 'auto'
                    ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]'
                    : 'border-[var(--k-border)] text-[var(--k-text-muted)]'
                "
                @click="draftAvatar = 'auto'"
              >
                首字母（自动配色）
              </button>
              <button
                v-for="emoji in EMOJIS"
                :key="emoji"
                type="button"
                class="grid h-8 w-8 place-items-center rounded-lg border text-base transition"
                :class="
                  draftAvatar === `emoji:${emoji}`
                    ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)]'
                    : 'border-[var(--k-border)] hover:bg-[var(--k-surface-soft)]'
                "
                @click="pickEmoji(emoji)"
              >
                {{ emoji }}
              </button>
            </div>
            <div class="mt-2 flex items-center gap-2 text-xs text-[var(--k-text-faint)]">
              预览：<UserAvatar :name="draftName || user.username" :avatar="draftAvatar" :size="34" />
            </div>
          </div>

          <div class="flex gap-2">
            <button
              type="button"
              class="rounded-lg bg-[var(--k-accent)] px-3 py-1.5 text-xs font-bold text-[var(--k-on-accent)] disabled:opacity-60"
              :disabled="saving"
              @click="save"
            >
              {{ saving ? '保存中…' : '保存' }}
            </button>
            <button
              type="button"
              class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)]"
              @click="editing = false"
            >
              取消
            </button>
          </div>
        </div>

        <p v-if="user.demo" class="rounded-lg bg-[var(--k-warn-soft)] px-3 py-2 text-[11px] leading-5 text-[var(--k-warn)]">
          这是<b>公开展示账号</b>，昵称与头像已锁定：所有人都能用它登录，允许修改会让页面被人随意改掉。
          想拥有自己的资料，请注册一个账号。
        </p>
      </div>
    </section>

    <!-- 安全 -->
    <section id="security" class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)]">
      <div class="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--k-border)] px-5 py-3">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">安全</h2>
        <button
          v-if="!showPassword"
          type="button"
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs font-semibold text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="!canChangePassword"
          :title="user.demo ? '演示账号不允许修改密码' : !user.hasEmail ? '该账号未绑定邮箱，请联系管理员重置' : '修改密码'"
          @click="showPassword = true"
        >
          修改密码
        </button>
      </div>

      <div class="space-y-3 px-5 py-4 text-xs leading-5 text-[var(--k-text-muted)]">
        <p>修改密码需要邮箱验证码；成功后<b>其它设备会全部下线</b>，当前页面会自动续用新凭证。</p>

        <!-- 演示账号的声明：明确说清为什么不能改 -->
        <p
          v-if="user.demo"
          class="rounded-lg bg-[var(--k-warn-soft)] px-3 py-2 text-[11px] leading-5 text-[var(--k-warn)]"
        >
          <b>演示账号不支持修改密码。</b>
          它的用户名和密码是公开的，任何人都能用它登录；一旦被改掉，
          所有访客都会立刻登不进去，而且没人能把它改回来。
          需要长期保存自己的学习记录，请注册一个账号（或让管理员帮你重置密码）。
        </p>
        <p
          v-else-if="!user.hasEmail"
          class="rounded-lg bg-[var(--k-warn-soft)] px-3 py-2 text-[11px] leading-5 text-[var(--k-warn)]"
        >
          该账号没有绑定邮箱，无法接收验证码，因此不能自助修改密码。请联系管理员重置。
        </p>

        <form v-if="showPassword && canChangePassword" class="space-y-3 pt-1" @submit.prevent="submitPassword">
          <label class="block text-sm">
            <span class="text-[var(--k-text-muted)]">当前密码</span>
            <input v-model="currentPassword" type="password" :class="field" autocomplete="current-password" />
          </label>

          <label class="block text-sm">
            <span class="text-[var(--k-text-muted)]">邮箱验证码</span>
            <span class="mt-1 flex gap-2">
              <input v-model="code" :class="field" class="mt-0" maxlength="6" inputmode="numeric" placeholder="6 位数字" />
              <button
                type="button"
                class="shrink-0 rounded-lg border border-[var(--k-border)] px-3 text-xs font-semibold text-[var(--k-text-muted)] disabled:opacity-50"
                :disabled="sending || cooldown > 0"
                @click="sendCode"
              >
                {{ cooldown > 0 ? `${cooldown}s` : sending ? '发送中…' : '发送验证码' }}
              </button>
            </span>
            <span class="mt-1 block text-[11px] text-[var(--k-text-faint)]">
              验证码会发到 {{ user.email }}
            </span>
          </label>

          <label class="block text-sm">
            <span class="text-[var(--k-text-muted)]">新密码</span>
            <input v-model="newPassword" type="password" :class="field" autocomplete="new-password" placeholder="至少 8 位，不能是纯数字" />
          </label>

          <label class="block text-sm">
            <span class="text-[var(--k-text-muted)]">确认新密码</span>
            <input v-model="confirmPassword" type="password" :class="field" autocomplete="new-password" />
          </label>

          <div class="flex gap-2">
            <button
              type="submit"
              class="rounded-lg bg-[var(--k-accent)] px-3 py-1.5 text-xs font-bold text-[var(--k-on-accent)] disabled:opacity-60"
              :disabled="changing"
            >
              {{ changing ? '提交中…' : '确认修改' }}
            </button>
            <button
              type="button"
              class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)]"
              @click="showPassword = false"
            >
              取消
            </button>
          </div>
        </form>
      </div>
    </section>
  </div>
</template>
