/**
 * 导出保存。
 *
 * 出图和出表都在服务端完成（/export-long-image、/export-excel 返回二进制），
 * 客户端只负责接收 ArrayBuffer 并按平台落地：
 *   H5   —— Blob + <a download>，交给浏览器下载
 *   App  —— 写入应用文档目录：长图存入系统相册，Excel 用系统文档打开器打开
 *
 * 不做客户端渲染兜底（html2canvas / xlsx 库不进包），服务端失败就明确报错让用户重试。
 */

import { exportExcel, exportLongImage } from '@/api/volunteer'

export interface ExportResult {
  /** 展示给用户的结果说明 */
  message: string
}

// #ifdef APP-PLUS
/** 把 ArrayBuffer 写进应用私有目录，返回本地绝对路径 */
function writeToDoc(buffer: ArrayBuffer, filename: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const b64 = uni.arrayBufferToBase64(buffer)
    plus.io.requestFileSystem(
      plus.io.PRIVATE_DOC,
      (fs) => {
        fs.root!.getFile(
          filename,
          { create: true },
          (entry) => {
            entry.createWriter(
              (writer) => {
                writer.onwrite = () => resolve(plus.io.convertLocalFileSystemURL(entry.fullPath || `_doc/${filename}`))
                writer.onerror = () => reject(new Error('写入本地文件失败'))
                writer.writeAsBinary(b64)
              },
              () => reject(new Error('创建文件写入器失败')),
            )
          },
          () => reject(new Error('创建本地文件失败')),
        )
      },
      () => reject(new Error('访问本地存储失败')),
    )
  })
}

function saveImageToAlbum(filePath: string): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.saveImageToPhotosAlbum({
      filePath,
      success: () => resolve(),
      fail: (err) => reject(new Error(err?.errMsg?.includes('auth') ? '未获得相册权限，请在系统设置中允许' : '保存到相册失败')),
    })
  })
}

function openDocument(filePath: string): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.openDocument({
      filePath,
      showMenu: true,
      success: () => resolve(),
      fail: () => reject(new Error('没有可打开该文件的应用，文件已保存在应用目录')),
    })
  })
}
// #endif

// #ifdef H5
function downloadInBrowser(buffer: ArrayBuffer, filename: string, mime: string) {
  const blob = new Blob([buffer], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 4000)
}
// #endif

export async function saveLongImage(planId: number, safetyCode: string, accessKey: string): Promise<ExportResult> {
  const res = await exportLongImage(planId, safetyCode, accessKey)
  const buffer = res.data
  if (!buffer || buffer.byteLength === 0) throw new Error('服务端返回了空文件')

  // #ifdef H5
  downloadInBrowser(buffer, `gzly-plan-${planId}.png`, 'image/png')
  return { message: '长图已开始下载' }
  // #endif

  // #ifdef APP-PLUS
  const filePath = await writeToDoc(buffer, `gzly-plan-${planId}.png`)
  await saveImageToAlbum(filePath)
  return { message: '长图已保存到相册' }
  // #endif

  // eslint-disable-next-line no-unreachable
  return { message: '当前平台暂不支持导出' }
}

export async function saveExcel(planId: number, safetyCode: string, accessKey: string): Promise<ExportResult> {
  const res = await exportExcel(planId, safetyCode, accessKey)
  const buffer = res.data
  if (!buffer || buffer.byteLength === 0) throw new Error('服务端返回了空文件')

  // #ifdef H5
  downloadInBrowser(
    buffer,
    `gzly-plan-${planId}.xlsx`,
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  )
  return { message: 'Excel 已开始下载' }
  // #endif

  // #ifdef APP-PLUS
  const filePath = await writeToDoc(buffer, `gzly-plan-${planId}.xlsx`)
  await openDocument(filePath)
  return { message: '已用系统应用打开 Excel' }
  // #endif

  // eslint-disable-next-line no-unreachable
  return { message: '当前平台暂不支持导出' }
}
