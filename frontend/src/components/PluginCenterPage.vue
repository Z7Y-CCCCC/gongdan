<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  activatePluginVersion,
  createPlugin,
  disablePlugin,
  enablePlugin,
  fetchPlugins,
  refreshPluginHealth,
  registerPluginVersion,
  rollbackPlugin,
  updatePluginConfig,
  type PluginCatalogInfo,
  type PluginRuntimeMode
} from '../api'
import UiIcon from './UiIcon.vue'
import DeleteConfirmDialog from './DeleteConfirmDialog.vue'
import { useDeleteConfirm } from '../composables/useDeleteConfirm'

const props = defineProps<{ token: string }>()
const emit = defineEmits<{
  (e: 'notice', message: string): void
  (e: 'error', message: string): void
  (e: 'plugins-changed'): void
}>()

const loading = ref(false)
const saving = ref(false)
const plugins = ref<PluginCatalogInfo[]>([])
const selectedKey = ref('')
const showCreateForm = ref(false)
const showVersionForm = ref(false)
const {
  visible: confirmVisible,
  title: confirmTitle,
  message: confirmMessage,
  loading: confirmLoading,
  confirm: requestConfirm,
  handleConfirm,
  handleCancel
} = useDeleteConfirm()

const createForm = reactive({
  pluginKey: '',
  name: '',
  description: '',
  pluginType: 'CUSTOM',
  runtimeMode: 'IFRAME' as PluginRuntimeMode,
  minCoreVersion: '1.0.0',
  manifestJson: '{\n  "frontendEntry": "",\n  "backendUrl": "",\n  "menus": [],\n  "requiredRoles": ["ADMIN"]\n}'
})

const versionForm = reactive({
  version: '',
  imageName: '',
  artifactUrl: '',
  checksum: '',
  releaseNotes: '',
  manifestJson: ''
})

const configJson = ref('{}')
const selectedPlugin = computed(() => plugins.value.find(item => item.pluginKey === selectedKey.value) || null)

function selectPlugin(plugin: PluginCatalogInfo) {
  selectedKey.value = plugin.pluginKey
  configJson.value = plugin.configJson || '{}'
}

async function loadPlugins(keepSelection = true) {
  loading.value = true
  try {
    plugins.value = await fetchPlugins(props.token)
    if (!keepSelection || !plugins.value.some(item => item.pluginKey === selectedKey.value)) {
      selectedKey.value = plugins.value[0]?.pluginKey || ''
    }
    configJson.value = plugins.value.find(item => item.pluginKey === selectedKey.value)?.configJson || '{}'
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '加载插件列表失败')
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  if (!createForm.pluginKey.trim() || !createForm.name.trim()) {
    emit('error', '请填写插件标识和插件名称')
    return
  }
  saving.value = true
  try {
    const created = await createPlugin(props.token, {
      pluginKey: createForm.pluginKey.trim(),
      name: createForm.name.trim(),
      description: createForm.description.trim(),
      pluginType: createForm.pluginType.trim() || 'CUSTOM',
      runtimeMode: createForm.runtimeMode,
      minCoreVersion: createForm.minCoreVersion.trim(),
      manifestJson: createForm.manifestJson
    })
    showCreateForm.value = false
    selectedKey.value = created.pluginKey
    emit('notice', `插件「${created.name}」已加入目录`)
    await loadPlugins()
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '创建插件失败')
  } finally {
    saving.value = false
  }
}

async function submitVersion() {
  if (!selectedPlugin.value || !versionForm.version.trim()) {
    emit('error', '请填写插件版本')
    return
  }
  saving.value = true
  try {
    await registerPluginVersion(props.token, selectedPlugin.value.pluginKey, {
      version: versionForm.version.trim(),
      imageName: versionForm.imageName.trim(),
      artifactUrl: versionForm.artifactUrl.trim(),
      checksum: versionForm.checksum.trim(),
      releaseNotes: versionForm.releaseNotes,
      manifestJson: versionForm.manifestJson.trim() || undefined
    })
    showVersionForm.value = false
    emit('notice', '插件版本已登记为 READY')
    await loadPlugins()
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '登记插件版本失败')
  } finally {
    saving.value = false
  }
}

async function runAction(action: () => Promise<unknown>, message: string) {
  if (!selectedPlugin.value) return
  saving.value = true
  try {
    await action()
    emit('notice', message)
    emit('plugins-changed')
    await loadPlugins()
  } catch (error) {
    emit('error', error instanceof Error ? error.message : '插件操作失败')
  } finally {
    saving.value = false
  }
}

function enableSelected() {
  const plugin = selectedPlugin.value
  if (!plugin) return
  const version = plugin.currentStableVersion || plugin.versions.find(item => item.status === 'READY')?.version
  void runAction(() => enablePlugin(props.token, plugin.pluginKey, version), '插件已启用')
}

async function disableSelected() {
  const plugin = selectedPlugin.value
  if (!plugin) return
  const accepted = await requestConfirm({ title: '下线插件', message: `确定下线插件「${plugin.name}」吗？下线后只影响当前工厂。` })
  confirmVisible.value = false
  if (!accepted) return
  void runAction(() => disablePlugin(props.token, plugin.pluginKey), '插件已下线')
}

async function rollbackSelected() {
  const plugin = selectedPlugin.value
  if (!plugin) return
  const accepted = await requestConfirm({ title: '回滚插件版本', message: `确定将「${plugin.name}」回滚到上一版吗？` })
  confirmVisible.value = false
  if (!accepted) return
  void runAction(() => rollbackPlugin(props.token, plugin.pluginKey), '插件已回滚')
}

function refreshHealthSelected() {
  const plugin = selectedPlugin.value
  if (!plugin) return
  void runAction(() => refreshPluginHealth(props.token, plugin.pluginKey), '健康状态已刷新')
}

function activateVersion(version: string) {
  const plugin = selectedPlugin.value
  if (!plugin || version === plugin.enabledVersion) return
  void runAction(() => activatePluginVersion(props.token, plugin.pluginKey, version), `已切换到 ${version}`)
}

async function saveConfig() {
  const plugin = selectedPlugin.value
  if (!plugin) return
  await runAction(() => updatePluginConfig(props.token, plugin.pluginKey, configJson.value), '插件配置已保存')
}

function prettyManifest(plugin: PluginCatalogInfo | null) {
  if (!plugin?.manifestJson) return '{}'
  try { return JSON.stringify(JSON.parse(plugin.manifestJson), null, 2) } catch { return plugin.manifestJson }
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    NOT_INSTALLED: '未安装', ENABLED: '启用中', DISABLED: '已下线',
    UP: '正常', DOWN: '异常', UNKNOWN: '未知', READY: '可上线'
  }
  return labels[status] || status || '-'
}

onMounted(() => { void loadPlugins(false) })
</script>

<template>
  <section class="content plugin-center-page">
    <div class="plugin-page-header">
      <div>
        <h2>插件中心</h2>
        <p>按工厂独立启停和切换客户定制功能。插件异常时可先下线，核心工单与库存业务不受影响。</p>
      </div>
      <div class="plugin-header-actions">
        <button class="ghost-btn" type="button" :disabled="loading" @click="loadPlugins(false)"><UiIcon name="refresh" />刷新</button>
        <button class="primary-btn" type="button" @click="showCreateForm = true"><UiIcon name="plus" />登记插件</button>
      </div>
    </div>

    <div class="plugin-layout">
      <div class="plugin-list-panel">
        <div class="plugin-panel-title"><span>插件目录</span><small>{{ plugins.length }} 个</small></div>
        <div v-if="loading" class="plugin-empty">加载中...</div>
        <button
          v-for="plugin in plugins"
          :key="plugin.pluginKey"
          type="button"
          class="plugin-list-item"
          :class="{ selected: selectedKey === plugin.pluginKey }"
          @click="selectPlugin(plugin)"
        >
          <span class="plugin-list-icon"><UiIcon name="layers" /></span>
          <span class="plugin-list-copy"><strong>{{ plugin.name }}</strong><small>{{ plugin.pluginKey }}</small></span>
          <span :class="['plugin-dot', plugin.factoryStatus === 'ENABLED' ? 'is-on' : '']"></span>
        </button>
        <div v-if="!loading && plugins.length === 0" class="plugin-empty">还没有登记插件</div>
      </div>

      <div v-if="selectedPlugin" class="plugin-detail-panel">
        <div class="plugin-detail-header">
          <div>
            <div class="plugin-title-line"><h3>{{ selectedPlugin.name }}</h3><span class="plugin-key">{{ selectedPlugin.pluginKey }}</span></div>
            <p>{{ selectedPlugin.description || '暂无插件描述' }}</p>
          </div>
          <div class="plugin-actions">
            <button class="ghost-btn" type="button" :disabled="saving" @click="refreshHealthSelected"><UiIcon name="activity" />健康检查</button>
            <button v-if="selectedPlugin.factoryStatus === 'ENABLED'" class="danger-btn" type="button" :disabled="saving" @click="disableSelected"><UiIcon name="close" />下线</button>
            <button v-else class="primary-btn" type="button" :disabled="saving || selectedPlugin.versions.length === 0" @click="enableSelected"><UiIcon name="play" />启用</button>
            <button class="secondary-btn" type="button" :disabled="saving || selectedPlugin.factoryStatus !== 'ENABLED'" @click="rollbackSelected"><UiIcon name="rework" />回滚</button>
          </div>
        </div>

        <div class="plugin-stat-grid">
          <div><span>运行模式</span><strong>{{ selectedPlugin.runtimeMode }}</strong></div>
          <div><span>当前状态</span><strong>{{ statusLabel(selectedPlugin.factoryStatus) }}</strong></div>
          <div><span>运行版本</span><strong>{{ selectedPlugin.enabledVersion || '-' }}</strong></div>
          <div><span>健康状态</span><strong :class="`health-${selectedPlugin.healthStatus.toLowerCase()}`">{{ statusLabel(selectedPlugin.healthStatus) }}</strong></div>
        </div>

        <div class="plugin-section">
          <div class="plugin-section-heading"><h4>版本管理</h4><button class="ghost-btn" type="button" @click="showVersionForm = true"><UiIcon name="plus" />登记版本</button></div>
          <div class="plugin-version-table-wrap">
            <table class="data-table plugin-version-table">
              <thead><tr><th>版本</th><th>状态</th><th>登记时间</th><th>说明</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="version in selectedPlugin.versions" :key="version.id">
                  <td><strong>{{ version.version }}</strong><span v-if="version.version === selectedPlugin.enabledVersion" class="current-version">当前</span></td>
                  <td><span class="plugin-status-badge">{{ statusLabel(version.status) }}</span></td>
                  <td>{{ version.createdAt || '-' }}</td>
                  <td class="release-notes">{{ version.releaseNotes || '-' }}</td>
                  <td><button class="action-link" type="button" :disabled="saving || version.version === selectedPlugin.enabledVersion" @click="activateVersion(version.version)">切换</button></td>
                </tr>
                <tr v-if="selectedPlugin.versions.length === 0"><td colspan="5" class="plugin-table-empty">尚未登记版本，登记后才能启用插件。</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="plugin-config-grid">
          <div class="plugin-section">
            <div class="plugin-section-heading"><h4>插件配置</h4><button class="ghost-btn" type="button" :disabled="saving" @click="saveConfig"><UiIcon name="save" />保存</button></div>
            <textarea v-model="configJson" class="plugin-json-editor" spellcheck="false" placeholder="请输入当前工厂的 JSON 配置"></textarea>
          </div>
          <div class="plugin-section">
            <div class="plugin-section-heading"><h4>运行清单</h4><span class="plugin-section-hint">版本发布时固定</span></div>
            <pre class="plugin-manifest">{{ prettyManifest(selectedPlugin) }}</pre>
          </div>
        </div>
      </div>
      <div v-else class="plugin-detail-panel plugin-empty-detail">选择一个插件查看版本和工厂配置</div>
    </div>

    <div v-if="showCreateForm" class="plugin-modal-mask modal-backdrop" @click.self="showCreateForm = false">
      <form class="plugin-modal modal" @submit.prevent="submitCreate">
        <div class="plugin-modal-header modal-header"><h3>登记插件</h3><button type="button" class="icon-btn" @click="showCreateForm = false"><UiIcon name="close" /></button></div>
        <div class="plugin-form-grid">
          <label><span>插件标识</span><input v-model="createForm.pluginKey" placeholder="例如 quality-management" maxlength="64" /></label>
          <label><span>插件名称</span><input v-model="createForm.name" placeholder="例如质量管理" maxlength="128" /></label>
          <label><span>插件类型</span><input v-model="createForm.pluginType" placeholder="CUSTOM" maxlength="32" /></label>
          <label><span>运行模式</span><select v-model="createForm.runtimeMode"><option value="IFRAME">IFRAME 隔离页面</option><option value="REMOTE_SERVICE">独立服务</option><option value="BUILT_IN">核心内置</option></select></label>
          <label class="full"><span>描述</span><input v-model="createForm.description" placeholder="说明插件解决的业务问题" maxlength="500" /></label>
          <label class="full"><span>插件清单 JSON</span><textarea v-model="createForm.manifestJson" class="plugin-json-editor" spellcheck="false"></textarea></label>
        </div>
        <div class="plugin-modal-footer modal-footer"><button class="ghost-btn" type="button" @click="showCreateForm = false">取消</button><button class="primary-btn" type="submit" :disabled="saving">登记</button></div>
      </form>
    </div>

    <div v-if="showVersionForm" class="plugin-modal-mask modal-backdrop" @click.self="showVersionForm = false">
      <form class="plugin-modal modal" @submit.prevent="submitVersion">
        <div class="plugin-modal-header modal-header"><h3>登记插件版本</h3><button type="button" class="icon-btn" @click="showVersionForm = false"><UiIcon name="close" /></button></div>
        <div class="plugin-form-grid">
          <label><span>版本号</span><input v-model="versionForm.version" placeholder="例如 1.0.0" maxlength="64" /></label>
          <label><span>镜像名称</span><input v-model="versionForm.imageName" placeholder="quality-management:1.0.0" /></label>
          <label class="full"><span>制品地址</span><input v-model="versionForm.artifactUrl" placeholder="可选，填写插件包或镜像仓库地址" /></label>
          <label class="full"><span>SHA-256 校验值</span><input v-model="versionForm.checksum" placeholder="可选，生产环境建议填写" /></label>
          <label class="full"><span>发布说明</span><textarea v-model="versionForm.releaseNotes" placeholder="本次版本变更内容"></textarea></label>
          <label class="full"><span>版本清单 JSON</span><textarea v-model="versionForm.manifestJson" class="plugin-json-editor" spellcheck="false" placeholder="留空则沿用插件目录清单"></textarea></label>
        </div>
        <div class="plugin-modal-footer modal-footer"><button class="ghost-btn" type="button" @click="showVersionForm = false">取消</button><button class="primary-btn" type="submit" :disabled="saving">登记并设为可上线</button></div>
      </form>
    </div>

    <DeleteConfirmDialog
      v-model:visible="confirmVisible"
      :title="confirmTitle"
      :message="confirmMessage"
      :loading="confirmLoading"
      @confirm="handleConfirm"
      @update:visible="(value: boolean) => { if (!value) handleCancel() }"
    />
  </section>
</template>

<style scoped>
.plugin-center-page { display: flex; flex-direction: column; gap: 16px; }
.plugin-page-header, .plugin-detail-header, .plugin-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.plugin-page-header h2, .plugin-detail-header h3, .plugin-section-heading h4 { margin: 0; color: #20302c; }
.plugin-page-header h2 { font-size: 20px; }
.plugin-page-header p, .plugin-detail-header p { margin: 6px 0 0; color: #71807a; font-size: 13px; }
.plugin-header-actions, .plugin-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.plugin-layout { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 16px; min-height: 570px; }
.plugin-list-panel, .plugin-detail-panel { min-width: 0; border: 1px solid #e1eae6; border-radius: 8px; background: #fff; }
.plugin-panel-title { display: flex; justify-content: space-between; padding: 16px; border-bottom: 1px solid #e9efed; font-size: 14px; font-weight: 700; color: #24332f; }
.plugin-panel-title small, .plugin-section-hint { color: #87958f; font-size: 12px; font-weight: 400; }
.plugin-list-item { width: 100%; display: flex; align-items: center; gap: 10px; padding: 13px 14px; border: 0; border-bottom: 1px solid #eef3f1; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.plugin-list-item:hover, .plugin-list-item.selected { background: #f1f8f5; }
.plugin-list-icon { width: 30px; height: 30px; display: grid; place-items: center; color: #148b6c; background: #e5f5ef; border-radius: 5px; }
.plugin-list-copy { min-width: 0; display: grid; gap: 3px; flex: 1; }
.plugin-list-copy strong { overflow: hidden; color: #263632; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.plugin-list-copy small { overflow: hidden; color: #8b9893; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.plugin-dot { width: 7px; height: 7px; border-radius: 50%; background: #c7d1cd; }
.plugin-dot.is-on { background: #16ae83; box-shadow: 0 0 0 3px rgba(22,174,131,.13); }
.plugin-detail-panel { padding: 20px; }
.plugin-title-line { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.plugin-key { padding: 3px 7px; color: #6d7d77; background: #f1f5f3; border-radius: 4px; font-family: Consolas, monospace; font-size: 11px; }
.plugin-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin: 20px 0; }
.plugin-stat-grid > div { padding: 12px; border: 1px solid #edf2f0; border-radius: 6px; background: #f8faf9; }
.plugin-stat-grid span { display: block; margin-bottom: 6px; color: #83908b; font-size: 11px; }
.plugin-stat-grid strong { color: #283934; font-size: 14px; }
.health-up { color: #0c9a70 !important; }.health-down { color: #d14e55 !important; }.health-unknown { color: #a07a22 !important; }
.plugin-section { min-width: 0; margin-top: 18px; padding-top: 18px; border-top: 1px solid #edf2f0; }
.plugin-section-heading h4 { font-size: 14px; }
.plugin-version-table-wrap { margin-top: 12px; overflow-x: auto; }
.plugin-version-table { min-width: 680px; }
.plugin-version-table td, .plugin-version-table th { white-space: nowrap; }
.plugin-version-table .release-notes { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.current-version { margin-left: 6px; padding: 2px 5px; color: #0c9a70; background: #e6f7f0; border-radius: 3px; font-size: 10px; font-weight: 400; }
.plugin-status-badge { color: #0c8b67; font-size: 12px; }.plugin-table-empty, .plugin-empty { padding: 36px 16px; color: #899690; text-align: center; font-size: 13px; }
.plugin-config-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.plugin-json-editor, .plugin-manifest { width: 100%; min-height: 170px; box-sizing: border-box; padding: 11px; border: 1px solid #dce7e2; border-radius: 5px; background: #fbfcfc; color: #34453f; font: 12px/1.55 Consolas, monospace; resize: vertical; white-space: pre-wrap; overflow: auto; }
.plugin-manifest { margin: 12px 0 0; min-height: 170px; }
.plugin-empty-detail { display: grid; place-items: center; color: #86938e; }
.plugin-modal-mask { position: fixed; inset: 0; z-index: 1200; display: grid; place-items: center; padding: 20px; background: rgba(18, 29, 26, .46); }
.plugin-modal { width: min(620px, calc(100vw - 32px)); max-height: calc(100vh - 40px); overflow: auto; padding: 20px; border: 1px solid #dce7e2; border-radius: 8px; background: #fff; box-shadow: 0 20px 60px rgba(14, 35, 29, .25); }
.plugin-modal-header, .plugin-modal-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }.plugin-modal-header { padding-bottom: 14px; border-bottom: 1px solid #edf2f0; }.plugin-modal-header h3 { margin: 0; color: #24332f; font-size: 17px; }.plugin-modal-footer { justify-content: flex-end; margin-top: 16px; padding-top: 14px; border-top: 1px solid #edf2f0; }
.icon-btn { width: 30px; height: 30px; display: grid; place-items: center; border: 0; border-radius: 4px; background: transparent; color: #6e7c77; cursor: pointer; }.icon-btn:hover { background: #f0f5f2; }
.plugin-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; padding-top: 16px; }.plugin-form-grid label { display: grid; gap: 7px; color: #53635d; font-size: 12px; }.plugin-form-grid label.full { grid-column: 1 / -1; }.plugin-form-grid input, .plugin-form-grid select, .plugin-form-grid textarea { width: 100%; box-sizing: border-box; min-height: 35px; padding: 7px 9px; border: 1px solid #d9e4df; border-radius: 5px; color: #2d3c37; background: #fff; font: inherit; }.plugin-form-grid textarea { min-height: 74px; resize: vertical; }
.danger-btn { min-height: 34px; display: inline-flex; align-items: center; gap: 6px; padding: 0 12px; border: 1px solid #efcaca; border-radius: 5px; color: #c44950; background: #fff7f7; cursor: pointer; }.danger-btn:hover { background: #ffeded; }
@media (max-width: 900px) { .plugin-layout { grid-template-columns: 1fr; }.plugin-list-panel { min-height: 0; }.plugin-stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .plugin-page-header, .plugin-detail-header { align-items: flex-start; flex-direction: column; }.plugin-config-grid, .plugin-form-grid { grid-template-columns: 1fr; }.plugin-form-grid label.full { grid-column: auto; }.plugin-detail-panel { padding: 14px; } }
:global(.admin-shell.dark-theme) .plugin-page-header h2,
:global(.admin-shell.dark-theme) .plugin-detail-header h3,
:global(.admin-shell.dark-theme) .plugin-section-heading h4,
:global(.admin-shell.dark-theme) .plugin-panel-title,
:global(.admin-shell.dark-theme) .plugin-list-copy strong,
:global(.admin-shell.dark-theme) .plugin-stat-grid strong { color: #e0e8ec; }
:global(.admin-shell.dark-theme) .plugin-page-header p,
:global(.admin-shell.dark-theme) .plugin-detail-header p,
:global(.admin-shell.dark-theme) .plugin-panel-title small,
:global(.admin-shell.dark-theme) .plugin-section-hint,
:global(.admin-shell.dark-theme) .plugin-list-copy small { color: #95a5ad; }
:global(.admin-shell.dark-theme) .plugin-list-panel,
:global(.admin-shell.dark-theme) .plugin-detail-panel,
:global(.admin-shell.dark-theme) .plugin-modal { background: #171d23; border-color: #303c45; }
:global(.admin-shell.dark-theme) .plugin-panel-title,
:global(.admin-shell.dark-theme) .plugin-list-item,
:global(.admin-shell.dark-theme) .plugin-section { border-color: #2c3740; }
:global(.admin-shell.dark-theme) .plugin-list-item:hover,
:global(.admin-shell.dark-theme) .plugin-list-item.selected { background: #202e32; }
:global(.admin-shell.dark-theme) .plugin-list-icon,
:global(.admin-shell.dark-theme) .plugin-stat-grid > div { background: #202a30; border-color: #35424a; }
:global(.admin-shell.dark-theme) .plugin-key,
:global(.admin-shell.dark-theme) .current-version { color: #b9c8ce; background: #26343b; }
:global(.admin-shell.dark-theme) .plugin-json-editor,
:global(.admin-shell.dark-theme) .plugin-manifest { color: #d1dce1; background: #11171b; border-color: #3a4851; }
:global(.admin-shell.dark-theme) .plugin-form-grid input,
:global(.admin-shell.dark-theme) .plugin-form-grid select,
:global(.admin-shell.dark-theme) .plugin-form-grid textarea { color: #d7e1e5; background: #11171b; border-color: #3a4851; }
:global(.admin-shell.dark-theme) .plugin-modal-header,
:global(.admin-shell.dark-theme) .plugin-modal-footer { border-color: #303c45; }
</style>
