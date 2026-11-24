(function () {
  const messages = {
    '质量管理插件': 'Quality Management Plugin', '质量管理': 'Quality management',
    '独立插件示例 · 版本 1.0.0': 'Standalone sample · v1.0.0',
    '设备点检': 'Equipment inspection', '独立测试插件 · v1.0.0': 'Standalone test plugin · v1.0.0',
    '等待平台连接': 'Waiting for platform', '正在等待主系统建立安全消息桥。': 'Waiting for the secure platform bridge.',
    '质量问题记录': 'Quality issues', '暂无数据': 'No records', '新增问题': 'New issue',
    '问题描述': 'Issue', '例如：首件尺寸偏差': 'For example: First-piece dimensions out of tolerance',
    '状态': 'Status', '待处理': 'Pending', '处理中': 'In progress', '已关闭': 'Closed',
    '备注': 'Notes', '补充处理说明': 'Add handling notes', '无备注': 'No notes',
    '平台连接尚未建立': 'Platform connection is not ready', '插件请求超时': 'Plugin request timed out',
    '保存问题': 'Save issue', '保存点检': 'Save inspection',
    '插件已通过主系统安全代理连接。': 'Connected through the secure platform gateway.',
    '服务不可用': 'Service unavailable', '插件请求失败': 'Plugin request failed',
    '质量问题已保存。': 'Quality issue saved.', '当前工厂': 'Factory', '当前角色': 'Role',
    '点检周期': 'Inspection interval', '最近点检': 'Recent inspections', '暂无点检记录': 'No inspections',
    '登记点检': 'New inspection', '设备编号': 'Equipment code', '例如 EQ-001': 'For example: EQ-001',
    '点检结果': 'Result', '正常': 'Normal', '需维护': 'Maintenance needed', '停机': 'Stopped',
    '记录温度、振动或异常说明': 'Record temperature, vibration or other issues',
    '未配置': 'Not configured', '插件连接失败': 'Plugin connection failed', '点检记录已保存。': 'Inspection saved.',
    '保存失败': 'Save failed', '刷新': 'Refresh', '重试': 'Retry', '加载中...': 'Loading...',
    '连接中...': 'Connecting...', '保存中...': 'Saving...',
    '管理员': 'Administrator', '生产人员': 'Production worker'
  };
  let locale = new URL(location.href).searchParams.get('lang') === 'en-US' ? 'en-US' : 'zh-CN';
  const originals = new WeakMap();
  const attributeOriginals = new WeakMap();
  function t(value) {
    if (locale !== 'en-US') return value;
    const text = value.trim();
    if (messages[text]) return messages[text];
    if (/^\d+\s*条$/.test(text)) return text.match(/^\d+/)[0] + ' records';
    if (/^\d+\s*天$/.test(text)) return text.match(/^\d+/)[0] + ' days';
    if (text.startsWith('服务正常 · ')) return 'Service ready · ' + text.slice('服务正常 · '.length);
    if (text.startsWith('已保存，列表刷新失败：')) return 'Saved. List refresh failed: ' + t(text.slice('已保存，列表刷新失败：'.length));
    return value;
  }
  function localize() {
    document.documentElement.lang = locale;
    // Option labels may change language; their business values must not.
    document.querySelectorAll('option:not([value])').forEach(option => { option.value = option.textContent; });
    const walker = document.createTreeWalker(document.documentElement, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) {
      const parent = node.parentElement;
      if (!parent || parent.closest('script,style,textarea,#factory') || parent.closest('.record') && !parent.closest('.tag,[data-ui-copy]')) continue;
      const old = originals.get(node);
      const source = old && node.nodeValue === old.last ? old.source : node.nodeValue;
      const translated = t(source);
      node.nodeValue = translated;
      originals.set(node, { source, last: translated });
    }
    document.querySelectorAll('[placeholder],[aria-label],[title]').forEach(element => {
      const saved = attributeOriginals.get(element) || new Map();
      for (const name of ['placeholder', 'aria-label', 'title']) {
        if (!element.hasAttribute(name)) continue;
        const current = element.getAttribute(name);
        const old = saved.get(name);
        const source = old && current === old.last ? old.source : current;
        const translated = t(source);
        element.setAttribute(name, translated);
        saved.set(name, { source, last: translated });
      }
      attributeOriginals.set(element, saved);
    });
  }
  window.PluginUiLocale = {
    setLocale(value) { locale = value === 'en-US' ? 'en-US' : 'zh-CN'; localize(); },
    localize, t
  };
  localize();
})();
