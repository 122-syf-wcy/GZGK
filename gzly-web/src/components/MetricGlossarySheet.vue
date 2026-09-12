<script setup lang="ts">
/**
 * 指标白话说明底部弹层：解释志愿列表里的专业术语。
 * 供结果页、对比页等复用；文案集中在这里维护。
 */
defineProps<{
  show: boolean
}>()

defineEmits<{
  'update:show': [value: boolean]
}>()

const GLOSSARY: Array<{ term: string; desc: string }> = [
  { term: '位次', desc: '你在全省同科类考生中的排名。位次比分数更稳定，是志愿参考的核心依据，可在省考试院一分一段表查询。' },
  { term: '位次差', desc: '参考年份该志愿最低录取位次减去你的位次。为正表示历史录取线比你的位次宽松，数值越大通常越稳；为负表示历史门槛更高，属于冲刺。' },
  { term: '参考匹配', desc: '按历史位次和数据质量估算的匹配程度（较高 / 中等 / 偏低），只是历史数据参考，不是录取承诺。' },
  { term: '置信度', desc: '该条志愿历史数据的完整度与可解释性评分。分数越高代表数据越完整、参考价值越高；偏低时请务必人工复核。' },
  { term: '计划趋势 / 扩招指数', desc: '招生计划数相对近年的变化。扩招通常意味着录取机会相对改善，缩招则相反。' },
  { term: '招生指数', desc: '院校层面的招生供给强度评分（0-100），综合计划规模与变化。' },
  { term: '精度', desc: '位次数据、招生计划、供给与置信度的综合可靠性评分，用于提示这条推荐可以信到什么程度。' },
  { term: '冲 / 稳 / 保 / 垫', desc: '按位次区间划分的梯度档位。冲=历史门槛略高于你的位次；稳=与你相当；保=明显宽松；垫=兜底志愿，在极端波动下保护整张志愿表的安全。' },
]
</script>

<template>
  <van-popup
    :show="show"
    position="bottom"
    round
    closeable
    :style="{ maxHeight: '78%' }"
    @update:show="$emit('update:show', $event)"
  >
    <div class="glossary-sheet">
      <h3 class="glossary-sheet__title">指标说明</h3>
      <p class="glossary-sheet__sub">列表里的数据都是历史参考口径，帮助你比较志愿，不构成录取承诺。</p>
      <dl class="glossary-sheet__list">
        <div v-for="entry in GLOSSARY" :key="entry.term" class="glossary-sheet__item">
          <dt>{{ entry.term }}</dt>
          <dd>{{ entry.desc }}</dd>
        </div>
      </dl>
    </div>
  </van-popup>
</template>

<style scoped>
.glossary-sheet {
  padding: 22px 18px 30px;
}

.glossary-sheet__title {
  font-family: var(--gz-font-display);
  font-size: 17px;
  font-weight: 700;
  color: #17181c;
}

.glossary-sheet__sub {
  margin-top: 6px;
  font-size: 12px;
  color: #6a6c72;
  line-height: 1.6;
}

.glossary-sheet__list {
  margin-top: 14px;
  display: grid;
  gap: 12px;
}

.glossary-sheet__item {
  padding: 10px 12px;
  border-radius: 12px;
  background: #fafaf8;
  border: 1px solid rgba(23, 24, 28, 0.05);
}

.glossary-sheet__item dt {
  font-size: 13px;
  font-weight: 700;
  color: #22242a;
}

.glossary-sheet__item dd {
  margin: 4px 0 0;
  font-size: 12px;
  color: #4b4d54;
  line-height: 1.65;
}
</style>
