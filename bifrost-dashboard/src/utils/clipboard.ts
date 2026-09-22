/**
 * 复制到剪贴板（同步账号页要把地址/口令/设备 ID 抄进阅读设备）。
 * 优先 navigator.clipboard（需安全上下文：https 或 localhost），
 * 失败时退回 textarea + execCommand（局域网 http 访问时的唯一可行路径）。
 */
export async function copyText(text: string): Promise<boolean> {
  if (!text) return false
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // 落到下面的兜底
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.left = '-9999px'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}
