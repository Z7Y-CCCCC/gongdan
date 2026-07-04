const navigationBar = (titleText: string) => ({
  titleText,
  type: 'default',
  titleSize: '16px',
  titleColor: '#202926',
  backgroundColor: '#f6f7f7'
})

const page = (route: string, titleText: string, component: () => Promise<unknown>, isEntry = false) => {
  const routeConfig: Record<string, unknown> = {
    path: `/${route}`,
    component,
    meta: {
    route,
    isEntry,
    isQuit: isEntry,
    isTabBar: false,
    navigationBar: navigationBar(titleText),
    maxWidth: 640
  }
  }
  if (isEntry) {
    routeConfig.alias = '/'
  }
  return routeConfig
}

const routes = [
  page('pages/bind/index', '账号绑定', () => import('./pages/bind/index.vue'), true),
  page('pages/home/index', '黑湖小工单', () => import('./pages/home/index.vue')),
  page('pages/work-orders/index', '工单', () => import('./pages/work-orders/index.vue')),
  page('pages/work-order-detail/index', '工单详情', () => import('./pages/work-order-detail/index.vue')),
  page('pages/report-form/index', '报工', () => import('./pages/report-form/index.vue')),
  page('pages/report-list/index', '报工列表', () => import('./pages/report-list/index.vue')),
  page('pages/field-events/index', '现场事件', () => import('./pages/field-events/index.vue')),
  page('pages/tasks/index', '任务', () => import('./pages/tasks/index.vue')),
  page('pages/board/index', '看板', () => import('./pages/board/index.vue'))
]

const globalConfig = {
  router: { mode: 'hash', base: '/', routerBase: '/', strict: false },
  routes,
  locales: {},
  globalStyle: {
    maxWidth: 640,
    navigationBarTextStyle: 'black',
    navigationBarTitleText: '黑湖小工单',
    navigationBarBackgroundColor: '#f6f7f7',
    backgroundColor: '#f3f5f5'
  },
  nvue: { 'flex-direction': 'column' },
  tabBar: {
    shown: false,
    height: '0px',
    list: []
  }
}

Object.assign(globalThis, {
  __uniRoutes: routes,
  __uniConfig: {
    ...((globalThis as any).__uniConfig || {}),
    ...globalConfig
  }
})
