import http from './request'
import type { Announcement, Result } from '@/types'

export function fetchCurrentAnnouncement() {
  return http.get<Result<Announcement | null>>('/announcement/current')
}
