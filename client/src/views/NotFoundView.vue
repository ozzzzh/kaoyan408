<template>
  <div class="mx-auto max-w-md space-y-4 py-16 text-center">
    <p class="text-5xl font-bold text-[#dbe4ef]">404</p>
    <h1 class="text-lg font-semibold text-[var(--k-text)]">页面不存在</h1>
    <p class="text-sm text-[var(--k-text-muted)]">链接可能已失效，或该功能尚未实现。</p>
    <RouterLink
      to="/dashboard"
      class="inline-block rounded-xl bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-white no-underline hover:bg-[var(--k-accent-hover)]"
    >
      返回工作台
    </RouterLink>
  </div>
</template>