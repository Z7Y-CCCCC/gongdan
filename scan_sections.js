/**
 * 给 BaseDataPage.vue 添加分页功能
 * 同时修复工艺路线操作按钮间距
 */
const fs = require('fs')
const path = require('path')

const filePath = path.join(__dirname, 'frontend/src/components/BaseDataPage.vue')
let content = fs.readFileSync(filePath, 'utf-8')

// 1. 添加分页变量（script 部分）

// 单位分页 - 在 "const unitForm = reactive" 之后
content = content.replace(
  "const unitForm = reactive({ name: '', remark: '' })\r\n\r\nasync function loadUnits",
  `const unitForm = reactive({ name: '', remark: '' })\r\nconst unitPage = ref(1)\r\nconst unitPageSize = ref(10)\r\nconst unitTotalPages = computed(() => Math.max(1, Math.ceil(unitFilteredList.value.length / unitPageSize.value)))\r\n\r\nasync function loadUnits`
)

// 不良品项分页 - 在 "const defectForm" 之后
content = content.replace(
  "const defectForm = reactive({ code: '', name: '' })\r\n\r\nasync function loadDefects",
  `const defectForm = reactive({ code: '', name: '' })\r\nconst defectPage = ref(1)\r\nconst defectPageSize = ref(10)\r\nconst defectTotalPages = computed(() => Math.max(1, Math.ceil(defectFilteredList.value.length / defectPageSize.value)))\r\n\r\nasync function loadDefects`
)

// 工序分页 - 在 "collectData: []" 之后，工艺路线之前
content = content.replace(
  "  collectData: []\r\n})\r\n\r\n// ===== 工艺路线管理 =====",
  `  collectData: []\r\n})\r\nconst processPage = ref(1)\r\nconst processPageSize = ref(10)\r\nconst processTotalPages = computed(() => Math.max(1, Math.ceil(processFilteredList.value.length / processPageSize.value)))\r\n\r\n// ===== 工艺路线管理 =====`
)

// 工艺路线分页 - 在 "// ===== 工艺路线管理 =====" 之后
content = content.replace(
  "// ===== 工艺路线管理 =====\r\ninterface RouteStep {",
  `// ===== 工艺路线管理 =====\r\nconst routePage = ref(1)\r\nconst routePageSize = ref(10)\r\nconst routeTotalPages = computed(() => Math.max(1, Math.ceil(routeFilteredList.value.length / routePageSize.value)))\r\ninterface RouteStep {`
)

// 2. 分页模板 HTML
const paginationTemplate = (listName, pageName, totalPagesName, pageSizeName) => `
        <div class="pagination">
          <span class="page-total">共 {{ ${listName}.length }} 条结果</span>
          <button class="page-btn" type="button" :disabled="${pageName} <= 1" @click="${pageName}--">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <span class="page-num">{{ ${pageName} }}</span>
          <button class="page-btn" type="button" :disabled="${pageName} >= ${totalPagesName}" @click="${pageName}++">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
          <select class="size-select" v-model="${pageSizeName}">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
          </select>
        </div>`

// 在单位 section 的 </div>\r\n      </section> 之前加分页
// 使用唯一标记找到正确的位置：单位管理的空数据行
content = content.replace(
  `暂无数据</td>\r\n              </tr>\r\n            </tbody>\r\n          </table>\r\n        </div>\r\n      </section>\r\n\r\n      <!-- 不良品项管理`,
  `暂无数据</td>\r\n              </tr>\r\n            </tbody>\r\n          </table>\r\n        </div>\r\n${paginationTemplate('unitFilteredList', 'unitPage', 'unitTotalPages', 'unitPageSize')}\r\n      </section>\r\n\r\n      <!-- 不良品项管理`
)

// 不良品项 section 的分页
content = content.replace(
  `暂无数据</td>\r\n              </tr>\r\n            </tbody>\r\n          </table>\r\n        </div>\r\n      </section>\r\n\r\n      <!-- 工序管理`,
  `暂无数据</td>\r\n              </tr>\r\n            </tbody>\r\n          </table>\r\n        </div>\r\n${paginationTemplate('defectFilteredList', 'defectPage', 'defectTotalPages', 'defectPageSize')}\r\n      </section>\r\n\r\n      <!-- 工序管理`
)

// 工序列表 section 的分页
content = content.replace(
  `暂无数据</td>\r\n              </tr>\r\n            </tbody>\r\n          </table>\r\n        </div>\r\n      </section>\r\n\r\n      <!-- 工序管理 - 创建/编辑表单`,
  `暂无数据</td>\r\n              </tr>\r\n            </tbody>\r\n          </table>\r\n        </div>\r\n${paginationTemplate('processFilteredList', 'processPage', 'processTotalPages', 'processPageSize')}\r\n      </section>\r\n\r\n      <!-- 工序管理 - 创建/编辑表单`
)

// 工艺路线列表 section 的分页
content = content.replace(
  `暂无工艺路线\r\n              </td>\r\n            </tr>\r\n          </tbody>\r\n        </table>\r\n      </section>\r\n\r\n      <!-- 工艺路线 - 创建/编辑`,
  `暂无工艺路线\r\n              </td>\r\n            </tr>\r\n          </tbody>\r\n        </table>\r\n${paginationTemplate('routeFilteredList', 'routePage', 'routeTotalPages', 'routePageSize')}\r\n      </section>\r\n\r\n      <!-- 工艺路线 - 创建/编辑`
)

// 3. 工艺路线操作按钮间距
content = content.replace(
  `              <td>\r\n                <button class="link-btn" type="button" @click="openEditRoute(r)">编辑</button>\r\n                <button class="link-btn" type="button">复制</button>\r\n                <button class="link-btn danger" type="button" @click="confirmDeleteRoute(r)">删除</button>\r\n              </td>`,
  `              <td style="white-space:nowrap">\r\n                <button class="link-btn" type="button" @click="openEditRoute(r)" style="margin-right:8px">编辑</button>\r\n                <button class="link-btn" type="button" style="margin-right:8px">复制</button>\r\n                <button class="link-btn danger" type="button" @click="confirmDeleteRoute(r)">删除</button>\r\n              </td>`
)

fs.writeFileSync(filePath, content, 'utf-8')
console.log('Done! BaseDataPage.vue updated with pagination and button spacing.')
console.log('Lines:', content.split(/\r?\n/).length)
