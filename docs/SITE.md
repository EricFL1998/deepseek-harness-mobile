# 文档站点（GitHub Pages）

`docs/index.html` 是本仓库的静态文档页，单文件、无构建依赖，可直接部署到 GitHub Pages。

## 部署

1. 仓库 Settings → Pages；
2. Build and deployment → Source: **Deploy from a branch**；
3. Branch: `main`（或 `master`），Folder: **/docs**；
4. 保存后访问 `https://<owner>.github.io/<repo>/`。

仓库根目录的 `.nojekyll` 已放在 `docs/.nojekyll`，避免 GitHub 对带下划线/特殊目录做 Jekyll 处理。

## 本地预览

```bash
python -m http.server 8080 --directory docs
# 打开 http://127.0.0.1:8080/
```

## 内容维护

- 页面章节与 `docs/source-driven-native-ui.md` 保持一致；
- 扫描器命令、slot 数量、实现方式变化时同步更新 HTML；
- 不需要 Node/npm 或构建产物，直接改 `docs/index.html` 即可。
