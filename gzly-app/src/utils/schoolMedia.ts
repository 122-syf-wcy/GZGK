import { CDN_BASE } from '@/config/cdn'

/** 校园相册：按 schoolId → 图片 URL 数组（来自打包内 school_photos.json，懒加载 + 缓存）。 */
let photoCache: Record<string, string[]> | null = null
let photoLoading: Promise<Record<string, string[]>> | null = null

export async function getSchoolPhotos(schoolId?: string, max = 9): Promise<string[]> {
  if (!schoolId) return []
  if (!photoCache) {
    if (!photoLoading) {
      photoLoading = import('@/data/school_photos.json')
        .then((m) => {
          photoCache = ((m as unknown as { default?: Record<string, string[]> }).default ||
            (m as unknown as Record<string, string[]>)) as Record<string, string[]>
          return photoCache
        })
        .catch(() => {
          photoCache = {}
          return photoCache
        })
    }
    await photoLoading
  }
  const arr = (photoCache && photoCache[schoolId]) || []
  return arr.slice(0, max)
}

export interface SchoolCdnInfo {
  motto?: string
  videoPoster?: string
  videoUrl?: string
  ruankeRank?: string
  usRank?: string
  numAcademician?: string
  numDoctor?: string
  numMaster?: string
  xuekeRank?: Record<string, string>
}

function pickStr(v: unknown): string {
  const s = v == null ? '' : String(v)
  return s && s !== '0' ? s : ''
}

/** 拉取院校公开档案 info.json（校训 / 宣传片封面 / 排名 / 学科评估）。失败返回 null。 */
export function getSchoolCdnInfo(schoolId?: string): Promise<SchoolCdnInfo | null> {
  return new Promise((resolve) => {
    if (!schoolId) {
      resolve(null)
      return
    }
    uni.request({
      url: `${CDN_BASE}/www/2.0/school/${schoolId}/info.json`,
      method: 'GET',
      timeout: 8000,
      success: (res) => {
        const body = res.data as Record<string, unknown> | null
        if (!body || typeof body !== 'object') {
          resolve(null)
          return
        }
        const inner = body.data as Record<string, unknown> | undefined
        const d = inner && (inner.school_id || inner.name) ? inner : body
        const vid = ((d.video as Record<string, unknown>) || (d.video_pc as Record<string, unknown>) || {}) as Record<
          string,
          unknown
        >
        const xk = d.xueke_rank as Record<string, string> | undefined
        resolve({
          motto: pickStr(d.motto),
          videoPoster: pickStr(vid.img_url),
          videoUrl: pickStr(vid.url),
          ruankeRank: pickStr(d.ruanke_rank),
          usRank: pickStr(d.us_rank),
          numAcademician: pickStr(d.num_academician),
          numDoctor: pickStr(d.num_doctor),
          numMaster: pickStr(d.num_master),
          xuekeRank: xk && typeof xk === 'object' && Object.keys(xk).length ? xk : undefined,
        })
      },
      fail: () => resolve(null),
    })
  })
}
