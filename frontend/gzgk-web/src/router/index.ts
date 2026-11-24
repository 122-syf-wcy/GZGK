import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { breadcrumb: '2026 贵州本科志愿' },
    },
    {
      path: '/new-plan',
      name: 'new-plan',
      component: () => import('@/views/NewPlanView.vue'),
      meta: { breadcrumb: '新建志愿方案' },
    },
    {
      path: '/workspace/:planId?',
      name: 'workspace',
      component: () => import('@/views/WorkbenchView.vue'),
      meta: { breadcrumb: '2026 贵州本科志愿 / 方案工作台' },
    },
    {
      path: '/report/:planId?',
      name: 'report',
      component: () => import('@/views/DeepReportView.vue'),
      meta: { breadcrumb: '2026 贵州本科志愿 / 深度诊断报告' },
    },
    {
      path: '/universities',
      name: 'universities',
      component: () => import('@/views/UniversitiesView.vue'),
      meta: { breadcrumb: '院校与专业' },
    },
    {
      path: '/score-lines',
      name: 'score-lines',
      component: () => import('@/views/ScoreLinesView.vue'),
      meta: { breadcrumb: '历年分数线' },
    },
    {
      path: '/policies',
      name: 'policies',
      component: () => import('@/views/PoliciesView.vue'),
      meta: { breadcrumb: '政策与章程' },
    },
  ],
});

export default router;
