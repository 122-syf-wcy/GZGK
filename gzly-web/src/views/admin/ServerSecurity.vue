<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchAdminServerSecurityStatus } from '@/api/admin'
import { showToast } from 'vant'
import {
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
  ShieldCheck,
  ShieldAlert,
} from 'lucide-vue-next'

defineOptions({ name: 'AdminServerSecurity' })

interface SecurityStatus {
  checkedAt?: string
  overallLevel?: 'normal' | 'warning' | 'risk' | string
  sshPasswordAuthentication?: string
  sshPubkeyAuthentication?: string
  sshPermitRootLogin?: string
  sshKbdInteractiveAuthentication?: string
  sshChallengeResponseAuthentication?: string
  authorizedKeysExists?: boolean
  authorizedKeysNotEmpty?: boolean
  authorizedKeysPermissionOk?: boolean
  sshDirPermissionOk?: boolean
  authorizedKeysOwnerOk?: boolean
  fail2banActive?: boolean
  fail2banSshdActive?: boolean
  bannedIps?: string[]
  bannedIpCount24h?: number
  firewalldActive?: boolean
  nftablesActive?: boolean
  firewallActive?: boolean
  openPorts?: number[]
  publicListeningPorts?: number[]
  firewallAllowedPorts?: number[]
  requiredPortsOk?: boolean
  unexpectedPublicPorts?: number[]
  blockedByFirewallPorts?: number[]
  nginxLimitReqEnabled?: boolean
  loginFailCount24h?: number
  credentialFailCount24h?: number
  apiRateLimitCount24h?: number
  nginx5xx24h?: number
  appError24h?: number
  sensitiveLogHitCount?: number
  sshFailedIpTop10?: Array<{ ip: string; count: number }>
  fail2banRecentBans?: Array<{ index: number; ip: string }>
  rateLimitTopEndpoints?: Array<{ path: string; count: number }>
  recommendations?: Array<{ level: string; text: string }>
  sections?: Record<string, any>
}

const loading = ref(false)
const status = ref<SecurityStatus>({})

const overviewCards = computed(() => [
  {
    label: 'SSH 密码登录',
    value: status.value.sshPasswordAuthentication || 'unknown',
    ok: status.value.sshPasswordAuthentication === 'no',
  },
  {
    label: 'SSH 密钥登录',
    value: status.value.sshPubkeyAuthentication || 'unknown',
    ok: status.value.sshPubkeyAuthentication === 'yes',
  },
  {
    label: 'fail2ban',
    value: status.value.fail2banActive ? 'active' : 'inactive',
    ok: status.value.fail2banActive,
  },
  {
    label: 'nginx 限流',
    value: status.value.nginxLimitReqEnabled ? 'enabled' : 'unknown',
    ok: status.value.nginxLimitReqEnabled,
  },
])

const metricCards = computed(() => [
  { label: '24h 登录失败', value: num(status.value.loginFailCount24h) },
  { label: '24h 被封禁 IP', value: num(status.value.bannedIpCount24h) },
  { label: '24h API 限流', value: num(status.value.apiRateLimitCount24h) },
  { label: '24h nginx 5xx', value: num(status.value.nginx5xx24h) },
  { label: '24h 应用异常', value: num(status.value.appError24h) },
  { label: '敏感日志命中', value: num(status.value.sensitiveLogHitCount) },
])

async function loadData() {
  loading.value = true
  try {
    const res = await fetchAdminServerSecurityStatus()
    status.value = res.data?.data || {}
  } catch (error: any) {
    showToast(error?.message || '服务器安全状态加载失败')
  } finally {
    loading.value = false
  }
}

function num(value: unknown) {
  return typeof value === 'number' ? value : 0
}

function boolText(value: unknown) {
  return value ? '是' : '否'
}

function listText(values?: Array<string | number>) {
  return values && values.length ? values.join(' / ') : '无'
}

function levelLabel(level?: string) {
  if (level === 'normal') return '正常'
  if (level === 'warning') return '警告'
  if (level === 'risk') return '风险'
  return '未知'
}

function levelClass(level?: string) {
  if (level === 'normal') return 'is-ok'
  if (level === 'warning') return 'is-warning'
  if (level === 'risk') return 'is-risk'
  return 'is-unknown'
}

function rowClass(level?: string) {
  if (level === 'ok') return 'row-ok'
  if (level === 'risk') return 'row-risk'
  if (level === 'warning') return 'row-warning'
  return ''
}

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <div class="page-inner">
      <div class="page-head">
        <div>
          <p class="eyebrow">Server Security</p>
          <h1 class="page-title">服务器安全防护</h1>
          <p class="page-desc">
            只读展示 sshd、密钥登录、fail2ban、防火墙、nginx 限流和近 24h 安全指标。
            页面不会返回 authorized_keys 内容、密钥、访问码或环境变量。
          </p>
        </div>
        <button class="refresh-btn" :disabled="loading" @click="loadData">
          <RefreshCw :size="15" />
          {{ loading ? '读取中...' : '刷新状态' }}
        </button>
      </div>

      <section class="overview gz-card">
        <div class="overall" :class="levelClass(status.overallLevel)">
          <component :is="status.overallLevel === 'risk' ? ShieldAlert : ShieldCheck" :size="24" />
          <div>
            <span>当前安全等级</span>
            <strong>{{ levelLabel(status.overallLevel) }}</strong>
          </div>
        </div>
        <div class="overview-grid">
          <div v-for="card in overviewCards" :key="card.label" class="overview-item">
            <span>{{ card.label }}</span>
            <strong :class="card.ok ? 'text-ok' : 'text-risk'">{{ card.value }}</strong>
          </div>
        </div>
        <div class="checked-at">
          巡检时间：{{ status.checkedAt ? status.checkedAt.replace('T', ' ').slice(0, 19) : '-' }}
        </div>
      </section>

      <section class="metric-grid">
        <div v-for="metric in metricCards" :key="metric.label" class="metric-card gz-card">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
        </div>
      </section>

      <section class="section-grid">
        <div class="gz-card panel">
          <div class="panel-head">
            <h2>SSH 防护</h2>
            <span class="panel-badge" :class="status.sshPasswordAuthentication === 'no' ? 'badge-ok' : 'badge-risk'">
              密码登录 {{ status.sshPasswordAuthentication || 'unknown' }}
            </span>
          </div>
          <dl class="kv-list">
            <div><dt>PasswordAuthentication</dt><dd>{{ status.sshPasswordAuthentication || '-' }}</dd></div>
            <div><dt>PubkeyAuthentication</dt><dd>{{ status.sshPubkeyAuthentication || '-' }}</dd></div>
            <div><dt>PermitRootLogin</dt><dd>{{ status.sshPermitRootLogin || '-' }}</dd></div>
            <div><dt>KbdInteractiveAuthentication</dt><dd>{{ status.sshKbdInteractiveAuthentication || '-' }}</dd></div>
            <div><dt>authorized_keys 存在</dt><dd>{{ boolText(status.authorizedKeysExists) }}</dd></div>
            <div><dt>authorized_keys 非空</dt><dd>{{ boolText(status.authorizedKeysNotEmpty) }}</dd></div>
            <div><dt>authorized_keys 权限安全</dt><dd>{{ boolText(status.authorizedKeysPermissionOk) }}</dd></div>
            <div><dt>/root/.ssh 权限安全</dt><dd>{{ boolText(status.sshDirPermissionOk) }}</dd></div>
            <div><dt>fail2ban sshd jail</dt><dd>{{ status.fail2banSshdActive ? 'active' : 'inactive' }}</dd></div>
          </dl>
          <div class="sub-block">
            <h3>当前封禁 IP</h3>
            <p>{{ listText(status.bannedIps) }}</p>
          </div>
        </div>

        <div class="gz-card panel">
          <div class="panel-head">
            <h2>防火墙</h2>
            <span class="panel-badge" :class="status.requiredPortsOk ? 'badge-ok' : 'badge-risk'">
              22/80/443 {{ status.requiredPortsOk ? '正常' : '需复核' }}
            </span>
          </div>
          <dl class="kv-list">
            <div><dt>firewalld</dt><dd>{{ status.firewalldActive ? 'active' : 'inactive' }}</dd></div>
            <div><dt>nftables</dt><dd>{{ status.nftablesActive ? 'active' : 'inactive' }}</dd></div>
            <div><dt>防火墙状态</dt><dd>{{ status.firewallActive ? 'active' : 'unknown' }}</dd></div>
            <div><dt>公网允许端口</dt><dd>{{ listText(status.firewallAllowedPorts) }}</dd></div>
            <div><dt>本机监听端口</dt><dd>{{ listText(status.publicListeningPorts) }}</dd></div>
            <div><dt>疑似额外放行</dt><dd>{{ listText(status.unexpectedPublicPorts) }}</dd></div>
            <div><dt>监听但被防火墙阻断</dt><dd>{{ listText(status.blockedByFirewallPorts) }}</dd></div>
          </dl>
        </div>
      </section>

      <section class="section-grid">
        <div class="gz-card panel">
          <div class="panel-head">
            <h2>攻击与封禁记录</h2>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>SSH 登录失败 IP</th><th>次数</th></tr></thead>
              <tbody>
                <tr v-for="item in status.sshFailedIpTop10 || []" :key="item.ip">
                  <td>{{ item.ip }}</td>
                  <td>{{ item.count }}</td>
                </tr>
                <tr v-if="!(status.sshFailedIpTop10 || []).length"><td colspan="2">暂无数据</td></tr>
              </tbody>
            </table>
          </div>
          <div class="sub-block">
            <h3>最近 fail2ban 封禁</h3>
            <p>{{ listText((status.fail2banRecentBans || []).map(item => item.ip)) }}</p>
          </div>
          <div class="sub-block">
            <h3>最近触发 429 的接口</h3>
            <p v-if="!(status.rateLimitTopEndpoints || []).length">暂无数据</p>
            <ul v-else class="inline-list">
              <li v-for="item in status.rateLimitTopEndpoints" :key="item.path">{{ item.path }}：{{ item.count }}</li>
            </ul>
          </div>
        </div>

        <div class="gz-card panel">
          <div class="panel-head">
            <h2>安全建议</h2>
          </div>
          <ul class="recommend-list">
            <li
              v-for="item in status.recommendations || []"
              :key="item.text"
              :class="rowClass(item.level)"
            >
              <CheckCircle2 v-if="item.level === 'ok'" :size="16" />
              <AlertTriangle v-else :size="16" />
              <span>{{ item.text }}</span>
            </li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100%; }
.page-inner { max-width: 1180px; margin: 0 auto; padding: 20px 16px; }
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}
.eyebrow {
  margin: 0 0 5px;
  font-size: 12px;
  color: #64748b;
  font-weight: 800;
  text-transform: uppercase;
}
.page-title { font-size: 24px; font-weight: 800; color: #111827; }
.page-desc { margin-top: 6px; font-size: 13px; line-height: 1.7; color: #64748b; max-width: 760px; }
.refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(15,23,42,0.1);
  border-radius: 8px;
  background: #0f172a;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}
.refresh-btn:disabled { opacity: 0.55; cursor: wait; }
.overview {
  display: grid;
  grid-template-columns: 230px 1fr;
  gap: 16px;
  align-items: center;
  padding: 16px;
  margin-bottom: 14px;
}
.overall {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
}
.overall span { display: block; font-size: 12px; color: #64748b; }
.overall strong { display: block; margin-top: 2px; font-size: 20px; }
.overall.is-ok { background: #ecfdf5; color: #047857; }
.overall.is-warning { background: #fffbeb; color: #b45309; }
.overall.is-risk { background: #fef2f2; color: #b91c1c; }
.overall.is-unknown { background: #f1f5f9; color: #475569; }
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.overview-item {
  padding: 12px;
  border: 1px solid rgba(15,23,42,0.08);
  border-radius: 8px;
  background: #fff;
}
.overview-item span { display: block; font-size: 12px; color: #64748b; }
.overview-item strong { display: block; margin-top: 4px; font-size: 15px; color: #0f172a; }
.checked-at { grid-column: 1 / -1; font-size: 12px; color: #94a3b8; }
.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}
.metric-card { padding: 13px 14px; }
.metric-card span { display: block; font-size: 12px; color: #64748b; }
.metric-card strong { display: block; margin-top: 5px; font-size: 22px; color: #0f172a; }
.section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}
.panel { padding: 16px; min-width: 0; }
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.panel-head h2 { margin: 0; font-size: 17px; font-weight: 800; color: #0f172a; }
.panel-badge {
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}
.badge-ok { background: #dcfce7; color: #166534; }
.badge-risk { background: #fee2e2; color: #991b1b; }
.kv-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}
.kv-list div {
  min-width: 0;
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
}
.kv-list dt { font-size: 12px; color: #64748b; }
.kv-list dd {
  margin: 4px 0 0;
  font-size: 14px;
  color: #0f172a;
  font-weight: 800;
  word-break: break-word;
}
.sub-block { margin-top: 14px; }
.sub-block h3 { margin: 0 0 6px; font-size: 13px; color: #334155; font-weight: 800; }
.sub-block p { margin: 0; font-size: 13px; line-height: 1.6; color: #64748b; word-break: break-word; }
.table-wrap { overflow-x: auto; }
table { width: 100%; min-width: 360px; border-collapse: collapse; font-size: 13px; }
th, td { padding: 9px 10px; text-align: left; border-bottom: 1px solid #e2e8f0; }
th { color: #64748b; font-weight: 800; background: #f8fafc; }
td { color: #0f172a; }
.recommend-list,
.inline-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.recommend-list { display: flex; flex-direction: column; gap: 9px; }
.recommend-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
  font-size: 13px;
  line-height: 1.5;
}
.row-ok { color: #166534; background: #f0fdf4 !important; }
.row-warning { color: #92400e; background: #fffbeb !important; }
.row-risk { color: #991b1b; background: #fef2f2 !important; }
.inline-list { display: flex; flex-direction: column; gap: 6px; color: #64748b; font-size: 13px; }
.text-ok { color: #166534 !important; }
.text-risk { color: #991b1b !important; }
@media (max-width: 980px) {
  .overview { grid-template-columns: 1fr; }
  .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .metric-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .section-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .page-inner { padding: 68px 12px 18px; }
  .page-head { flex-direction: column; align-items: stretch; }
  .refresh-btn { justify-content: center; }
  .overview-grid,
  .metric-grid,
  .kv-list { grid-template-columns: 1fr; }
}
</style>
