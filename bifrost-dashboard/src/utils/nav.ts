/**
 * 侧边栏导航激活判定（纯函数，便于单测）。
 *
 * 为什么不能简单地 `path.startsWith(to)`：`/books` 会把 `/books/roots`、`/books/progress`
 * 一起点亮——而这两个是**独立的菜单项**（同理音乐侧没有这种父子菜单，所以以前没暴露）。
 * 但简单改成"只认全等"又会让图书详情 `/books/12` 没有任何菜单项高亮。
 *
 * 规则（与本项目路由约定一致：**详情页一律是 `<分区>/:id(\d+)`**）：
 * 1. 路径完全相同 → 激活；
 * 2. `exact` 项只认第 1 条（如总览 `/`）；
 * 3. 其余项：路径等于 `to + '/' + 数字 id`（可再跟子路径）→ 激活，即"详情页仍属于该分区"。
 *
 * 这样无需给每个菜单项手工维护"我覆盖哪些路由名"，新增详情页自动生效。
 */
export interface NavItem {
  label: string
  to: string
  icon: unknown
  /** 只认精确路径（连详情页也不算），如总览 `/` */
  exact?: boolean
}

export function isNavItemActive(item: Pick<NavItem, 'to' | 'exact'>, path: string): boolean {
  if (path === item.to) {
    return true
  }
  if (item.exact) {
    return false
  }
  if (!path.startsWith(`${item.to}/`)) {
    return false
  }
  const rest = path.slice(item.to.length + 1)
  return /^\d+(\/|$)/.test(rest)
}
