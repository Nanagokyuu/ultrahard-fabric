# Ultra Hard（超困难）

适用于 **Minecraft 26.2** 的 Fabric + Kotlin 模组。新增真正的难度枚举值 **Ultra Hard（超困难）**，与和平/简单/普通/困难并列，基于困难并附加更严苛规则。

## 依赖

请一并安装：

- [Fabric Loader](https://fabricmc.net/use/) ≥ 0.19.5
- [Fabric API](https://modrinth.com/mod/fabric-api)（`0.160.0+26.2` 或更高的 26.2 兼容版本）
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) ≥ `1.13.12+kotlin.2.4.0`
- Java 25+

## 安装

1. 安装 Fabric Loader（Minecraft 26.2）。
2. 将本模组 jar 与上述依赖放入 `.minecraft/mods`。
3. 启动游戏。

## 构建

需要 **JDK 25**。

```bash
./gradlew build
```

产物位于：`build/libs/ultrahard-1.0.0.jar`（不含 `-sources`）。

## 启用超困难

与原版难度相同，可在以下位置选择：

### 创建新世界（开始游戏）

在「创建新世界」→ 游戏选项页的 **难度** 循环按钮中，与和平 / 简单 / 普通 / 困难并列出现 **超困难 / Ultra Hard**。选中后会写入世界的 `LevelSettings` / `WorldData`（序列化键为 `ultrahard`），开局即为超困难。

### 游戏内命令（需要权限等级 2 / OP）

```text
/difficulty ultrahard
```

也可使用 `/difficulty peaceful|easy|normal|hard` 切回其它难度。

### 游戏内选项菜单

在世界选项中的难度循环按钮里选择 **超困难 / Ultra Hard**。

## 效果说明（服务端权威）

当世界难度为 **Ultra Hard** 时：

1. **基于困难**：原版 Hard 相关行为均生效（僵尸增援、蜘蛛增益、饥饿致死、袭击波次等）。
2. **无自然回血**：取消饱食度/饱和度带来的自然恢复（类似 `naturalRegeneration false`）；药水等其它治疗仍可用。
3. **饥饿加快 50%**：玩家饥饿值消耗（exhaustion）× 1.5。
4. **敌对生物反伤**：普通 `Monster` / `Enemy` 攻击玩家时，会将攻击反射回敌人并直接秒杀敌人。
5. **特殊攻击与不死图腾**：末影龙、凋零、坚守者、远古守卫者的攻击会正常伤害玩家，并将本次伤害的 2 倍反射给攻击者；不死图腾仍可按原版机制抵御一次死亡，但**只要图腾效果被触发，图腾一定会被消耗消失**（含创造等边缘情况）。普通敌人的攻击会被反向秒杀，通常不会触发玩家图腾。
6. **盾牌**：举盾可以正常格挡伤害，但盾牌最大耐久固定为 10。
7. **玩家攻击强化**：该难度下玩家对任意 `LivingEntity` 的攻击伤害为原版计算结果的 5 倍，仍会正常计算护甲、盾牌和其他减伤效果。

## 版本信息

| 组件 | 版本 |
|------|------|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric Loom | 1.17-SNAPSHOT |
| Fabric API | 0.160.0+26.2 |
| Fabric Language Kotlin | 1.13.12+kotlin.2.4.0 |
| Gradle | 9.5.1 |
| Java | 25 |

## 许可证

见仓库内 `LICENSE`。
