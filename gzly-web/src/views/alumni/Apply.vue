<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { applyAlumni } from '@/api/alumni'
import request from '@/api/request'
import { showToast, showSuccessToast } from 'vant'
import { GraduationCap, UserPlus, ArrowLeft } from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const submitting = ref(false)

const form = ref({
  schoolId: (route.query.schoolId as string) || '',
  schoolName: (route.query.schoolName as string) || '',
  nickname: '',
  phone: '',
  email: '',
  graduationYear: undefined as number | undefined,
  major: '',
  bio: '',
  password: '',
  confirmPassword: '',
})

const schoolSearch = ref(form.value.schoolName)
const schoolResults = ref<any[]>([])
const showSchoolPicker = ref(false)

let searchTimer: ReturnType<typeof setTimeout>
watch(schoolSearch, (val) => {
  if (!val || val.length < 1) { schoolResults.value = []; return }
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    try {
      const res = await request.get('/university/list', { params: { keyword: val, page: 1, size: 10 } })
      schoolResults.value = res.data?.data?.items || res.data?.data?.records || []
    } catch { schoolResults.value = [] }
  }, 300)
})

function selectSchool(school: any) {
  form.value.schoolId = school.schoolId
  form.value.schoolName = school.name
  schoolSearch.value = school.name
  showSchoolPicker.value = false
  schoolResults.value = []
}

const canSubmit = computed(() =>
  form.value.schoolId && form.value.nickname && form.value.phone && form.value.password
  && form.value.password === form.value.confirmPassword && form.value.password.length >= 6
)

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    await applyAlumni({
      schoolId: form.value.schoolId,
      nickname: form.value.nickname,
      phone: form.value.phone,
      email: form.value.email,
      graduationYear: form.value.graduationYear,
      major: form.value.major,
      bio: form.value.bio,
      password: form.value.password,
    })
    showSuccessToast('申请已提交，请等待审核')
    setTimeout(() => router.push('/'), 1500)
  } catch (e: any) {
    showToast(e.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="apply-page">
    <header class="apply-header">
      <button class="back-btn" @click="router.back()"><ArrowLeft :size="20" /></button>
      <h1>校友申请</h1>
    </header>

    <div class="apply-hero">
      <div class="hero-icon"><UserPlus :size="32" /></div>
      <h2>成为校园信息维护员</h2>
      <p>为母校补充照片、视频和介绍，帮助学弟学妹了解真实的校园生活</p>
    </div>

    <div class="form-card">
      <van-form @submit="submit">
        <van-cell-group inset>
          <van-field
            v-model="schoolSearch"
            label="搜索学校"
            placeholder="输入学校名称搜索"
            required
            @focus="showSchoolPicker = true"
          />
          <div v-if="showSchoolPicker && schoolResults.length" class="school-dropdown">
            <div
              v-for="s in schoolResults" :key="s.schoolId"
              class="school-option"
              @click="selectSchool(s)"
            >
              <span class="school-name">{{ s.name }}</span>
              <span class="school-meta">{{ s.province }} · {{ s.typeName || '' }}</span>
            </div>
          </div>
          <van-field v-if="form.schoolName" :model-value="form.schoolName + ' (ID:' + form.schoolId + ')'" label="已选学校" readonly />
          <van-field v-model="form.nickname" label="昵称" placeholder="你的昵称" required />
          <van-field v-model="form.phone" label="手机号" type="tel" placeholder="用于登录和联系" required />
          <van-field v-model="form.email" label="邮箱" placeholder="可选，优先edu邮箱" />
          <van-field v-model="form.graduationYear" label="毕业年份" type="number" placeholder="如 2024" />
          <van-field v-model="form.major" label="就读专业" placeholder="如 计算机科学与技术" />
          <van-field v-model="form.bio" label="个人简介" type="textarea" rows="2" placeholder="简要介绍你自己" />
          <van-field v-model="form.password" label="设置密码" type="password" placeholder="至少6位" required />
          <van-field v-model="form.confirmPassword" label="确认密码" type="password" placeholder="再次输入" required
            :error-message="form.confirmPassword && form.password !== form.confirmPassword ? '密码不一致' : ''" />
        </van-cell-group>

        <div class="submit-wrap">
          <van-button round block type="primary" :loading="submitting" :disabled="!canSubmit" native-type="submit">
            <GraduationCap :size="16" /> 提交申请
          </van-button>
          <p class="submit-note">审核通过后即可管理母校信息</p>
        </div>
      </van-form>
    </div>
  </div>
</template>

<style scoped>
.apply-page { min-height: 100dvh; background: var(--gz-bg); }
.apply-header { display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: linear-gradient(135deg, #1e3a8a, #2563eb); color: #fff; }
.apply-header h1 { font-size: 17px; font-weight: 700; }
.back-btn { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border: none; border-radius: 8px; background: rgba(255,255,255,0.15); color: #fff; cursor: pointer; }

.apply-hero { text-align: center; padding: 32px 24px 24px; }
.hero-icon { display: inline-flex; align-items: center; justify-content: center; width: 64px; height: 64px; border-radius: 20px; background: linear-gradient(135deg, #2563eb, #7c3aed); color: #fff; margin-bottom: 16px; }
.apply-hero h2 { font-size: 20px; font-weight: 800; color: var(--gz-text-primary); margin-bottom: 8px; }
.apply-hero p { font-size: 14px; color: var(--gz-text-secondary); line-height: 1.5; max-width: 320px; margin: 0 auto; }

.form-card { max-width: 500px; margin: 0 auto; padding: 0 16px; position: relative; }
.school-dropdown { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; max-height: 200px; overflow-y: auto; margin: -8px 16px 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); z-index: 10; position: relative; }
.school-option { padding: 10px 14px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #f3f4f6; }
.school-option:hover { background: #eff6ff; }
.school-option:last-child { border-bottom: none; }
.school-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.school-meta { font-size: 12px; color: #94a3b8; }
.submit-wrap { padding: 24px 16px; }
.submit-note { text-align: center; font-size: 12px; color: var(--gz-text-tertiary); margin-top: 10px; }

@media (min-width: 768px) {
  .apply-header { padding: 16px 48px; }
  .apply-header h1 { font-size: 20px; }
  .apply-hero { padding: 48px 32px 32px; }
  .hero-icon { width: 80px; height: 80px; border-radius: 24px; }
  .apply-hero h2 { font-size: 26px; }
  .apply-hero p { font-size: 16px; max-width: 480px; }
  .form-card { max-width: 600px; padding: 0 32px; }
  .form-card :deep(.van-cell-group--inset) { border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.06); }
  .submit-wrap { padding: 32px 32px; }
}
</style>
