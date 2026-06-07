# Buried Treasure Finder — Minecraft Fabric Mod

通过 **F3 调试 / 饼图原理** 自动检测埋藏的宝藏位置。无需藏宝图即可定位！

## 原理

### 埋藏的宝藏生成规则 (Java 版)

- 生成于 **沙滩** (`minecraft:beach`) 和 **积雪沙滩** (`minecraft:snowy_beach`) 生物群系
- 箱子**始终**在区块内部 **X=9, Z=9** 坐标位置
- 位于石头、砂岩、闪长岩、花岗岩、安山岩的最高方块上方
- 基岩版生成于区块 X=8, Z=8

### Mod 检测原理

利用类似 F3 调试屏幕（Shift+F3 饼图）的思路：

1. **Mixin 注入** `WorldChunk.addBlockEntity` — 拦截客户端区块加载时的方块实体
2. 箱子 (`ChestBlockEntity`) 被加入区块时检测：
   - 区块内坐标是否为 `(9, 9)`？
   - 生物群系是否为沙滩/积雪沙滩？
3. 满足条件 → 记录宝藏精确坐标
4. 在宝藏位置渲染**金色信标光束**
5. HUD 显示扫描统计和最近宝藏信息

```
客户端收到区块数据 → WorldChunk.addBlockEntity(箱子)
  → 检查: pos & 15 == (9, 9) ?
  → 检查: Biome == beach / snowy_beach ?
  → ✓ 记录坐标 → 渲染信标 + HUD
```

## 功能

- 🗺️ **无需藏宝图** — 直接通过区块数据检测箱子
- 📍 金色信标光束标记宝藏精确位置
- 📊 HUD 扫描面板：渲染距离、沙滩区块数、检测到的箱子数
- ⌨️ `K` 键开关（可在"控制"设置中修改）
- 🌐 中英文双语

## HUD 显示

```
◆ Scanner | RD:12 | Beach:3 | Chests:1
★ Treasure (201, 58, -75) 45m
```

- `RD` — 当前渲染距离（区块数）
- `Beach` — 渲染距离内沙滩生物群系区块数
- `Chests` — 已检测到的宝藏箱子数
- 第二行显示最近宝藏坐标和距离

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) (1.21.4+)
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)
3. 将 JAR 放入 `.minecraft/mods/`

## 使用

1. 前往沙滩生物群系附近
2. 按 `K` 键开启（默认开启）
3. HUD 会显示扫描状态
4. 金色信标出现时 → 走到信标位置向下挖掘！
5. 如果没有检测到，在沙滩区块间移动让箱子所在区块进入渲染距离

## 开发

```bash
./gradlew build
./gradlew runClient
```

## 技术细节

| Mixin 目标 | 注入点 | 用途 |
|-----------|--------|------|
| `WorldChunk.addBlockEntity` | TAIL | 检测箱子方块实体 |
| `WorldRenderEvents.LAST` | — | 渲染金色信标 |
| `HudRenderCallback` | — | HUD 信息面板 |

## 许可

MIT
