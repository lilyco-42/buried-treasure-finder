# Buried Treasure Finder — Minecraft Fabric Mod

自动计算并高亮显示埋藏的宝藏位置。手持藏宝图时，在宝藏精确位置渲染金色信标光束。

## 功能

- 🗺️ 手持藏宝图时自动读取地图数据，定位红色 "×" 标记
- 📍 在宝藏精确位置渲染**金色信标光束** — 从远处就能看到
- 🖥️ HUD 显示宝藏坐标 (X, Z) 和距离
- ⌨️ 按 `K` 键切换开/关 (可在"控制"设置中修改)
- 🌐 中英文双语支持

## 原理

### 埋藏的宝藏生成规则 (Java 版)

- 生成于**沙滩**及其变种生物群系
- 在区块内部 **X=9, Z=9** 坐标位置
- 位于石头、砂岩、闪长岩、花岗岩、安山岩的**最高方块**上方
- 可以替换该位置的任意方块
- 通过沉船/海底废墟中找到的藏宝图定位

### Mod 工作原理

1. **Mixin 注入** `MapState.addDecoration` — 拦截客户端地图装饰数据
2. 检测到 `minecraft:target_x` 类型（红色 "×"）时，计算世界坐标：
   ```
   worldX = mapState.centerX + decoration.x
   worldZ = mapState.centerZ + decoration.z
   ```
3. 根据玩家手持的地图 ID 匹配对应宝藏位置
4. 通过 `WorldRenderEvents` 在世界中渲染金色信标
5. 通过 `HudRenderCallback` 在屏幕上显示坐标和距离

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) (1.21.4+)
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)
3. 将 `buried-treasure-finder-1.0.0.jar` 放入 `.minecraft/mods/` 文件夹

## 使用

1. 获得一张**藏宝图**（从沉船/海底废墟的箱子中找到）
2. 手持藏宝图
3. 金色信标光束会自动出现在宝藏位置
4. 按 `K` 键可以开关此功能
5. 走到信标位置向下挖掘即可找到宝藏！

## 开发

```bash
# 构建
./gradlew build

# 开发测试（自动启动 Minecraft）
./gradlew runClient

# 产物位置
ls build/libs/buried-treasure-finder-1.0.0.jar
```

## 版本支持

| Minecraft | Mod 版本 |
|-----------|---------|
| 1.21.4    | 1.0.0   |

## 许可

MIT License
