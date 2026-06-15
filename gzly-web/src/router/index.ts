import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/Home.vue'),
      meta: { title: '高考志愿辅助系统' },
    },
    {
      path: '/region/:provinceCode',
      name: 'RegionHome',
      component: () => import('@/views/RegionHome.vue'),
      meta: { title: '地区工作台' },
    },
    {
      path: '/university',
      name: 'UniversitySearch',
      component: () => import('@/views/UniversitySearch.vue'),
      meta: { title: '院校查询' },
    },
    {
      path: '/university/:id',
      name: 'UniversityDetail',
      component: () => import('@/views/UniversityDetail.vue'),
      meta: { title: '院校详情' },
    },
    {
      path: '/score-line',
      name: 'ScoreLineQuery',
      component: () => import('@/views/ScoreLineQuery.vue'),
      meta: { title: '历年分数线' },
    },
    {
      path: '/special-admissions',
      name: 'SpecialAdmissions',
      component: () => import('@/views/SpecialAdmissions.vue'),
      meta: { title: '特殊类型招生' },
    },
    {
      path: '/major-planner',
      name: 'MajorPlanner',
      component: () => import('@/views/MajorPlanner.vue'),
      meta: { title: '专业选择规划' },
    },
    {
      path: '/major-planner/result',
      name: 'MajorPlannerResult',
      component: () => import('@/views/MajorPlannerResult.vue'),
      meta: { title: '专业规划结果' },
    },
    {
      path: '/encouragement',
      name: 'EncouragementWall',
      component: () => import('@/views/EncouragementWall.vue'),
      meta: { title: '考生加油墙' },
    },
    {
      path: '/volunteer',
      name: 'VolunteerForm',
      component: () => import('@/views/VolunteerForm.vue'),
      meta: { title: '志愿填报' },
    },
    {
      path: '/volunteer/result',
      name: 'VolunteerResult',
      component: () => import('@/views/VolunteerResult.vue'),
      meta: { title: '志愿方案' },
    },
    {
      path: '/volunteer/compare',
      name: 'VolunteerCompare',
      component: () => import('@/views/VolunteerCompare.vue'),
      meta: { title: '院校对比' },
    },
    {
      path: '/volunteer/ai',
      name: 'AiAnalysis',
      component: () => import('@/views/AiAnalysis.vue'),
      meta: { title: 'AI深度解读' },
    },
    {
      path: '/ai-chat',
      name: 'AiChat',
      component: () => import('@/views/AiChat.vue'),
      meta: { title: '未上线地区 AI 志愿问答' },
    },
    {
      path: '/ai-chat/drafts',
      name: 'AiChatDrafts',
      component: () => import('@/views/AiChatDrafts.vue'),
      meta: { title: '找回 AI 问答对话' },
    },
    {
      path: '/disclaimer',
      name: 'Disclaimer',
      component: () => import('@/views/Disclaimer.vue'),
      meta: { title: '免责声明' },
    },
    {
      path: '/auth/login',
      redirect: '/volunteer',
    },
    {
      path: '/auth/activate',
      redirect: '/volunteer',
    },
    {
      path: '/me/plans',
      name: 'MyPlans',
      component: () => import('@/views/MyPlans.vue'),
      meta: { title: '我的志愿空间', requiresAuth: true },
    },
    {
      path: '/poster',
      name: 'PosterExport',
      component: () => import('@/views/PosterExport.vue'),
      meta: { title: '导出志愿表' },
    },
    // ---- Alumni ----
    {
      path: '/alumni/apply',
      name: 'AlumniApply',
      component: () => import('@/views/alumni/Apply.vue'),
      meta: { title: '校友申请' },
    },
    {
      path: '/alumni/login',
      name: 'AlumniLogin',
      component: () => import('@/views/alumni/Login.vue'),
      meta: { title: '管理员登录' },
    },
    {
      path: '/alumni/manage',
      name: 'AlumniManage',
      component: () => import('@/views/alumni/Manage.vue'),
      meta: { title: '院校内容管理' },
    },
    // ---- Admin ----
    {
      path: '/admin',
      component: () => import('@/components/AdminLayout.vue'),
      meta: { title: '管理后台' },
      children: [
        {
          path: '',
          name: 'AdminDashboard',
          component: () => import('@/views/admin/Dashboard.vue'),
          meta: { title: '数据看板 - 管理后台' },
        },
        {
          path: 'users',
          name: 'AdminUsers',
          component: () => import('@/views/admin/Users.vue'),
          meta: { title: '用户管理 - 管理后台' },
        },
        {
          path: 'plans',
          name: 'AdminPlans',
          component: () => import('@/views/admin/Plans.vue'),
          meta: { title: '方案记录 - 管理后台' },
        },
        {
          path: 'card-keys',
          redirect: '/admin/plans',
        },
        {
          path: 'universities',
          name: 'AdminUniversities',
          component: () => import('@/views/admin/Universities.vue'),
          meta: { title: '院校管理 - 管理后台' },
        },
        {
          path: 'score-lines',
          name: 'AdminScoreLines',
          component: () => import('@/views/admin/ScoreLines.vue'),
          meta: { title: '分数线管理 - 管理后台' },
        },
        {
          path: 'official-links',
          name: 'AdminOfficialLinks',
          component: () => import('@/views/admin/OfficialLinks.vue'),
          meta: { title: '官方入口 - 管理后台' },
        },
        {
          path: 'announcements',
          name: 'AdminAnnouncements',
          component: () => import('@/views/admin/Announcements.vue'),
          meta: { title: '公告管理 - 管理后台' },
        },
        {
          path: 'ai-config',
          name: 'AdminAiConfig',
          component: () => import('@/views/admin/AiConfig.vue'),
          meta: { title: 'AI配置 - 管理后台' },
        },
        {
          path: 'ai-qa',
          name: 'AdminAiQaSessions',
          component: () => import('@/views/admin/AiQaSessions.vue'),
          meta: { title: 'AI问答会话 - 管理后台' },
        },
        {
          path: 'ops',
          name: 'AdminOps',
          component: () => import('@/views/admin/Ops.vue'),
          meta: { title: '巡检状态 - 管理后台' },
        },
        {
          path: 'data-year-readiness',
          name: 'AdminDataYearReadiness',
          component: () => import('@/views/admin/DataYearReadiness.vue'),
          meta: { title: '数据准备进度 - 管理后台' },
        },
        {
          path: 'feedbacks',
          name: 'AdminFeedbacks',
          component: () => import('@/views/admin/Feedbacks.vue'),
          meta: { title: '反馈管理 - 管理后台' },
        },
        {
          path: 'encouragement-messages',
          name: 'AdminEncouragementMessages',
          component: () => import('@/views/admin/EncouragementMessages.vue'),
          meta: { title: '留言管理 - 管理后台' },
        },
        {
          path: 'alumni-review',
          name: 'AdminAlumniReview',
          component: () => import('@/views/admin/AlumniReview.vue'),
          meta: { title: '校友审核 - 管理后台' },
        },
        {
          path: 'qa-review',
          name: 'AdminQaReview',
          component: () => import('@/views/admin/QaReview.vue'),
          meta: { title: '问答审核 - 管理后台' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  document.title = (to.meta.title as string) || '高考志愿辅助系统'
  if (to.meta.requiresAuth) {
    const token = localStorage.getItem('gz_user_token')
    if (!token) {
      return '/volunteer'
    }
  }
})

export default router
