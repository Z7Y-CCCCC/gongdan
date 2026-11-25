import { createBlock, createVNode, defineAsyncComponent, openBlock, withCtx } from 'vue'
import { getApp, PageComponent, setupPage } from '@dcloudio/uni-h5'

const navigationBar = (titleText: string) => ({
  // Existing H5 screens render their own headers and mobile tab bar.
  style: 'custom',
  titleText,
  type: 'default',
  titleSize: '16px',
  titleColor: '#202926',
  backgroundColor: '#f6f7f7'
})

const page = (route: string, titleText: string, component: () => Promise<unknown>, isEntry = false, isTabBar = false) => {
  // Match the uni H5 pages generator: setupPage registers the public page,
  // lifecycle hooks and navigation queued during app launch. A bare
  // Vue-router component renders but never becomes a uni page.
  const loader = () => component().then(module => setupPage((module as any).default || module))
  const pageComponent = defineAsyncComponent({ loader, delay: 0, suspensible: false })
  const routeConfig: Record<string, unknown> = {
    path: `/${route}`,
    component: {
      setup() {
        return () => {
          const app = getApp() as { $route?: { query?: Record<string, unknown> } }
          const query = app?.$route?.query || {}
          openBlock()
          return createBlock(PageComponent, null, {
            // setupPage delivers route options through onLoad. Query keys are
            // data, not arbitrary HTML attributes on the page's root element.
            page: withCtx(() => [createVNode(pageComponent, { __pageQuery: query, ref: 'page' }, null, 512)]),
            _: 1
          })
        }
      }
    },
    loader,
    meta: {
    route,
    isEntry,
    isQuit: isEntry || isTabBar,
    isTabBar,
    navigationBar: navigationBar(titleText),
    maxWidth: 640
  }
  }
  if (isEntry) {
    routeConfig.alias = '/'
  }
  return routeConfig
}

export const routes = [
  page('pages/bind/index', '账号绑定', () => import('./pages/bind/index.vue'), true),
  page('pages/home/index', '黑湖小工单', () => import('./pages/home/index.vue'), false, true),
  page('pages/work-orders/index', '工单', () => import('./pages/work-orders/index.vue'), false, true),
  page('pages/work-order-detail/index', '工单详情', () => import('./pages/work-order-detail/index.vue')),
  page('pages/report-form/index', '报工', () => import('./pages/report-form/index.vue')),
  page('pages/report-list/index', '报工列表', () => import('./pages/report-list/index.vue'), false, true),
  page('pages/field-events/index', '现场事件', () => import('./pages/field-events/index.vue')),
  page('pages/tasks/index', '任务', () => import('./pages/tasks/index.vue'), false, true),
  page('pages/board/index', '看板', () => import('./pages/board/index.vue'), false, true)
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
    shown: true,
    color: '#8a9693',
    selectedColor: '#00a982',
    backgroundColor: '#ffffff',
    borderStyle: 'white',
    height: '50px',
    list: [
      { pagePath: 'pages/work-orders/index', text: '工单', iconPath: '/static/tabbar/order.png', selectedIconPath: '/static/tabbar/order.png' },
      { pagePath: 'pages/tasks/index', text: '任务', iconPath: '/static/tabbar/task.png', selectedIconPath: '/static/tabbar/task.png' },
      { pagePath: 'pages/home/index', text: '报工', iconPath: '/static/tabbar/plus.png', selectedIconPath: '/static/tabbar/plus.png' },
      { pagePath: 'pages/report-list/index', text: '记录', iconPath: '/static/tabbar/report.png', selectedIconPath: '/static/tabbar/report.png' },
      { pagePath: 'pages/board/index', text: '看板', iconPath: '/static/tabbar/board.png', selectedIconPath: '/static/tabbar/board.png' }
    ]
  }
}

Object.assign(globalThis, {
  __uniRoutes: routes,
  __uniConfig: {
    ...((globalThis as any).__uniConfig || {}),
    ...globalConfig
  }
})
