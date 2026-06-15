#!/usr/bin/env node
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')

function read(path) {
  return readFileSync(join(root, path), 'utf-8')
}

function assertContains(name, text, needle) {
  if (!text.includes(needle)) {
    console.error(`[volunteer-safety-code][FAIL] ${name}: missing ${needle}`)
    process.exit(2)
  }
}

const api = read('src/api/volunteer.ts')
const form = read('src/views/VolunteerForm.vue')

assertContains('generateVolunteerPlan type', api, 'safetyCode?: string')
assertContains('generateVolunteerPlan year type', api, 'year?: number')
assertContains('VolunteerForm import', form, 'getCurrentSafetyCode')
assertContains('VolunteerForm import', form, 'setCurrentSafetyCode')
assertContains('VolunteerForm state', form, 'const currentSafetyCode = ref(getCurrentSafetyCode())')
// 生成新方案的查看凭证：未手填时自动生成（ensureSafetyCode），不再因空凭证拦截生成，
// 也确保 safetyCode 一定随请求提交（避免“找回历史方案”与“生成新方案”凭证语义混淆）。
assertContains('VolunteerForm ensure safety code', form, 'function ensureSafetyCode()')
assertContains('VolunteerForm ensure safety code call', form, 'const safetyCode = ensureSafetyCode()')
assertContains('VolunteerForm payload', form, 'safetyCode,')
assertContains('VolunteerForm year payload', form, 'year: activeAdmissionYear.value')
assertContains('VolunteerForm localStorage sync', form, 'setCurrentSafetyCode(normalized)')
assertContains('VolunteerForm no query safetyCode', form, "query: { planId: String(plan.id) }")
assertContains('VolunteerForm QUERY_ONLY gate', form, "selectedBatchSupport.value.supportLevel === 'QUERY_ONLY'")
assertContains('VolunteerForm QUERY_ONLY text', form, '当前仅支持政策和数据缺口说明')

console.log('[volunteer-safety-code] OK')
