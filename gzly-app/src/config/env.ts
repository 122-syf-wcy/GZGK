/**
 * 后端 API 基地址配置。
 *
 * - H5：返回同源 `/api`，配合 vite.config.ts 的 devServer 代理（或同域 nginx 部署）。
 * - App / 小程序：必须使用绝对地址。默认直连公网后端，方便不上架时直接打包侧载。
 *   生产域名 gzly.dongsiwei.com 目前 ICP 备案受限，故 App 默认走公网 IP。
 *   如需切换到自己的服务器，改 APP_API_BASE 即可。
 */
const APP_API_BASE = 'http://39.97.232.141/api'

function detectIsH5(): boolean {
  try {
    const info = uni.getSystemInfoSync()
    // uniPlatform: 'web'(H5) / 'app' / 'mp-weixin' ...
    return info.uniPlatform === 'web'
  } catch {
    return false
  }
}

export const IS_H5 = detectIsH5()

/** 是否运行在 Capacitor 原生壳（打包 APK）里——壳内是本地页面，须用后端绝对地址 */
function isCapacitorShell(): boolean {
  try {
    if (typeof window === 'undefined') return false
    if ((window as unknown as { Capacitor?: unknown }).Capacitor) return true
    // 兜底：Capacitor(androidScheme=http) 跑在 http://localhost 且无端口；
    // 区别于 dev(:5180) 与线上(IP/域名)，避免 window.Capacitor 注入时机问题。
    const loc = window.location
    return loc.protocol === 'http:' && loc.hostname === 'localhost' && (loc.port === '' || loc.port === '80')
  } catch {
    return false
  }
}

/**
 * - H5 网页（开发/部署到服务器）：同源 /api（dev 走 vite 代理，线上走 nginx 反代），无跨域。
 * - Capacitor APK 原生壳：页面来自本地，使用后端绝对地址（后端已开 CORS）。
 * - App / 小程序：使用后端绝对地址。
 */
export const BASE_URL = IS_H5 && !isCapacitorShell() ? '/api' : APP_API_BASE

/** 请求默认超时（ms）。志愿生成 / AI 解读等长任务可单独传更大值。 */
export const DEFAULT_TIMEOUT = 20000
