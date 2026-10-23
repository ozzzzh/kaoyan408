<template>
  <div class="mx-auto max-w-md space-y-4 py-16 text-center">
    <p class="text-5xl font-bold text-[#dbe4ef]">404</p>
    <h1 class="text-lg font-semibold text-[#0b1f45]">页面不存在</h1>
    <p class="text-sm text-[#5c6f94]">链接可能已失效，或该功能尚未实现。</p>
    <RouterLink
      to="/dashboard"
      class="inline-block rounded-xl bg-[#1f49ba] px-4 py-2 text-sm font-semibold text-white no-underline hover:bg-[#1a3fa0]"
    >
      返回工作台
    </RouterLink>
  </div>
</template>