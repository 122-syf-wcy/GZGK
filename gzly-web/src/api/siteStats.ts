import http from './request'
import type { Result } from '@/types'

export interface OnlineStats {
  activeUsers: number
  windowSeconds: number
  totalViews?: number
  todayViews?: number
}

export function fetchOnlineStats() {
  return http.get<Result<OnlineStats>>('/site-stats/online')
}
