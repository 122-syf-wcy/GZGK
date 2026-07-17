import { IS_H5 } from './env'

/**
 * 院校公开数据 CDN（static-data.gaokao.cn）基地址。
 * - H5：走 vite 的 /cdn-proxy 代理，规避浏览器跨域。
 * - App / 小程序：直连 CDN 绝对地址。
 * 注意：图片本身（<image src>）可跨域直接渲染，只有抓 info.json（XHR）需要代理。
 */
export const CDN_BASE = IS_H5 ? '/cdn-proxy' : 'https://static-data.gaokao.cn'
