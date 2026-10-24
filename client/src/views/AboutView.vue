<script setup lang="ts">
/**
 * 数据说明页：承载 CC BY 4.0 要求的内容来源声明。
 *
 * 为什么单独做一页而不是放在页脚：CC BY 4.0 要求「以合理方式署名」，
 * 并未要求每个页面都展示完整声明。把完整声明集中在此页、
 * 页脚仅保留入口，既满足许可义务，也不干扰正常阅读界面。
 */
</script>

<template>
  <div class="mx-auto max-w-2xl space-y-5">
    <header class="space-y-1">
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">数据说明</h1>
      <p class="text-sm text-[var(--k-text-muted)]">本站内容的来源、许可与使用范围。</p>
    </header>

    <section
      class="space-y-4 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-6 py-6 text-sm leading-relaxed text-[var(--k-text-muted)]"
    >
      <div>
        <h2 class="mb-1.5 text-sm font-bold text-[var(--k-text)]">内容来源</h2>
        <p>
          本站的知识正文、历年真题题面、选项、答案与解析等内容，来源于开源项目
          <a
            class="text-[var(--k-accent)] underline underline-offset-2"
            href="https://github.com/liangbohan/postgraduate-exam-website"
            target="_blank"
            rel="noreferrer"
            >simple408</a
          >，采用
          <a
            class="text-[var(--k-accent)] underline underline-offset-2"
            href="https://creativecommons.org/licenses/by/4.0/"
            target="_blank"
            rel="noreferrer"
            >Creative Commons Attribution 4.0 International（CC BY 4.0）</a
          >
          许可。真题内容同时以 408os 作为参照进行校准。
        </p>
      </div>

      <div>
        <h2 class="mb-1.5 text-sm font-bold text-[var(--k-text)]">本站做了什么</h2>
        <p>
          本站对上述内容进行了结构化二次组织：拆分为题库与知识块、
          建立题目与知识点的关联、补充公式渲染与交互动画，并实现刷题、错题重做、
          间隔重复背诵与学习看板等功能。内容本身未作改写，仅作组织与呈现。
        </p>
      </div>

      <div>
        <h2 class="mb-1.5 text-sm font-bold text-[var(--k-text)]">使用范围</h2>
        <p>
          本站为个人学习用途的复习工具，不用于商业目的。
          依据 CC BY 4.0，任何人在保留来源署名与许可声明的前提下，
          均可再分发或改编其中的内容。
        </p>
      </div>

      <div>
        <h2 class="mb-1.5 text-sm font-bold text-[var(--k-text)]">已知数据问题</h2>
        <p>
          源数据中 2012、2021、2026 三卷的综合应用题分值合计不等于 408 规范的 70 分
          （分别为 68、68、76 分）。本站保持与来源一致，未作人工修正，
          并在对应试卷页面上标注提示。
        </p>
      </div>

      <div class="border-t border-[var(--k-border)] pt-4">
        <p class="text-xs text-[var(--k-text-faint)]">
          完整许可条款见仓库根目录的 LICENSE 文件，或访问
          <a
            class="text-[var(--k-accent)] underline underline-offset-2"
            href="https://creativecommons.org/licenses/by/4.0/legalcode"
            target="_blank"
            rel="noreferrer"
            >creativecommons.org/licenses/by/4.0/legalcode</a
          >。
        </p>
      </div>
    </section>
  </div>
</template>