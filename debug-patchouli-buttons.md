# Patchouli 分类按钮不显示调试记录

状态：[OPEN]
会话：patchouli-buttons

## 问题

Patchouli 书籍可以打开，但“物品”“武器”“拓展附魔”三个分类按钮没有显示。

## 可验证假设

1. Patchouli 实际加载的仍是旧构建资源，资源预加载数量为 0。
2. 书籍 NBT 指向的 ID 与实际 book.json ID 不一致。
3. 分类 JSON 被识别了，但字段或目录格式不符合 Patchouli 1.20.1。
4. 当前打开的是旧版本书籍缓存，未触发资源重载。
5. 书籍内容加载成功，但分类排序或可见性字段导致按钮未渲染。

## 当前证据

- `run/logs/latest.log:46`：`BookContentResourceListenerLoader preloaded 0 jsons`。
- 分类和条目当前位于 `data/enchantment_expansion/patchouli_books/...`。
- Patchouli 1.20.1 书籍属于客户端资源包，正确根路径应为 `assets/<modid>/patchouli_books/<book_id>/...`。

## 分析结论

最终根因（依据 Patchouli 1.20.1-85 反编译源码，而非猜测）：

Patchouli 1.20.1 的加载机制是**两路分离**的：

1. **书籍定义注册**（`BookRegistry.init` / `lambda$init$2`）：
   字节码常量 `String data/%s/%s` + `String patchouli_books` 证明扫描路径为
   `data/<modid>/patchouli_books/<book_id>/book.json`。
   `book.json` 放在 `assets` 时书籍根本不会注册 → 物品提示“无效的书: enchantment_expansion:enchantment_expansion”。

2. **内容加载**（`use_resource_pack: true` 时走 `BookContentResourceListenerLoader`）：
   通过客户端资源管理器读取 `assets/<modid>/patchouli_books/<book_id>/<lang>/categories|entries/*.json`。
   内容放在 `data` 时预加载为 0（`preloaded 0 jsons`）→ 书能打开但“类别”页为空。

## 证据时间线

- 全部在 `data`：`latest.log:46` `BookContentResourceListenerLoader preloaded 0 jsons`，书可打开、类别页空白。
- 全部迁到 `assets`：`latest.log:46` `preloaded 132 jsons`，但书籍未注册，物品提示“无效的书”。
- 拆分后（最终方案）：`data` 仅 `book.json`（1 个文件），`assets` 放三个语言内容目录（132 个文件）。
  构建产物验证：`build/resources/main/data/.../book.json` 1 个文件 + `build/resources/main/assets/.../{en_us,zh_cn,zh_tw}` 132 个文件。

## 附带修复

- 清理了 `data/.../enchantment_expansion/categories` 残留目录（早期结构遗留）。

## 验证状态

待用户启动新客户端进入游戏确认：物品提示显示“终界之书”（而非“无效的书”），打开后显示三个分类按钮。
