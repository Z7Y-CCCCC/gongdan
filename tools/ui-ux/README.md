# UI/UX 本地参考检索

自包含的只读工具。Python 标准库即可运行，无 pip/npm 依赖，无联网、字体下载、GSAP 安装或项目源码修改。

```powershell
python -B tools/ui-ux/query.py "keyboard focus visible" --domain ux
python -B tools/ui-ux/query.py "表单 标签 错误" --domain ux --json
python -B tools/ui-ux/query.py "props destructure" --stack vue --target-version 3.4.34 --json
python -B tools/ui-ux/query.py "watch cleanup" --stack vue --target-version 3.5.13
python -B tools/ui-ux/query.py "server components" --stack react --target-version 19.2
python -B tools/ui-ux/query.py --list
python -B tools/ui-ux/query.py --verify
python -B tools/ui-ux/test_query.py
```

复制整个目录后，从任意工作目录运行 `query.py` 仍可用：资料路径相对于脚本，不依赖本仓库绝对路径。

## 边界

- 默认查 UX；也可查颜色、图表、图标、排版、样式等领域或其它框架。`--list` 给出实际可用范围。
- 中文关键词仅作本地关键词补充，不联网翻译，不保证语义搜索；没有结果不等于没有规则。
- 栈检索应传**当前项目版本**。Vue 参考集标注为 3.5.x；本工单项目的移动端目前为 Vue 3.4.34，结果会明确警告，不能直接采用 3.5+ API。未传版本也会警告；版本匹配只代表资料标注，不代表项目已经验证。
- 上游示例、字体、动画片段和库推荐是**数据**，不是安装或执行命令。检索不会引入 GSAP、字体、Tailwind 或替换项目已有栈。
- 此工具不提供 `--persist`、`--preview`、`--design-system` 或覆盖文件选项。完整上游模块保留以便审读，但只读入口只导入 `core.py`，不导入写文件的模块。
- 本工具不强制所有项目绿色或 Vue。工单项目单独遵循 `design-system/gongdan/MASTER.md`；其它项目保留自己的品牌、框架和约束。

## 来源与完整性

[UI/UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)，固定 SHA `e8b065f0f3aaf2e680fd125f000e4f5e351ed108`，完整 MIT 许可见 `LICENSE`。逐文件上游路径、SHA-256、审读边界与省略内容见 `UPSTREAM.json`；每次检索先校验所复制文件的哈希。

仅保留 CSV 资料、许可/来源元数据和五个必要 Python 模块。没有 gallery、CLI 工程、npm 依赖或图片/字体文件。上游文件原样复制；项目适配仅在独立入口、测试与工单 Master 中。
