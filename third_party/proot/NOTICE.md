# Third-party: proot for Android (16 KB page size)

`app/src/main/jniLibs/{arm64-v8a,x86_64}/` 下的 proot 二进制来自 Termux 的
`proot` 包，替换了原先 4 KB 对齐的预编译库，以满足 Android 16 KB page size
要求。

| 文件 | Termux 包 | 版本 | 许可证 |
| --- | --- | --- | --- |
| `libproot.so` | `proot` | 5.1.107.98 | GPL-2.0-or-later |
| `libproot-loader.so` | `proot`（`usr/libexec/proot/loader`） | 5.1.107.98 | GPL-2.0-or-later |
| `libtalloc.so` | `libtalloc` | 2.5.0 | LGPL-3.0-or-later |
| `libandroid-shmem.so` | `libandroid-shmem` | 0.7 | Apache-2.0 |

发布源：

- https://packages.termux.dev/apt/termux-main/pool/main/p/proot/
- https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/
- https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/
- https://github.com/termux/proot
- https://github.com/termux/libandroid-shmem

本地处理：

- 将 proot 的 `DT_NEEDED libtalloc.so.2` 字符串改为 `libtalloc.so`，以匹配
  Android APK 只解压 `lib*.so` 的命名；
- 删除旧版自定义 `libproot-userland.so`（Termux loader 不需要）；
- 运行时设置 `PROOT_LOADER` 与 `LD_LIBRARY_PATH=<nativeLibraryDir>`。

构建/替换脚本记录在提交历史中；Termux 源码可从上述链接获取。
