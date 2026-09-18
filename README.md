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
4. **敌对生物伤害翻倍**：敌对生物沿用困难模式的伤害计算，但最终伤害为原版结果的 2 倍；不再反伤或直接秒杀敌人。
5. **玩家攻击上限**：玩家使用原版 1 倍攻击伤害，每次攻击最多扣除目标最大生命的 25%；低于该上限时使用原本伤害。
6. **装备耐久减半**：工具和盔甲的有效耐久减半，即每次耐久消耗翻倍；金制工具与金制盔甲不受影响。

### 吸血附魔

- 可附加在剑和斧上，最高正常等级为 III，且不与其它附魔冲突。
- I / II / III 分别将本次攻击经过护甲减免后的伤害的 20% / 30% / 40% 转化为玩家生命值；IV 级为 50%，之后每级增加 10%，IX 级达到 100% 后不再增加。
- 本次攻击伤害仍受 Ultra Hard 的单次 25% 目标最大生命上限限制。
- 每次吸血最少恢复 1 点生命值，每秒最多触发一次；同一时间攻击多个敌人时，取最大有效伤害计算。
- 沙漠神殿宝箱会额外尝试生成吸血附魔书：I 级 5%、II 级 4%、III 级 3%。
- 可通过 `/give @p minecraft:enchanted_book[stored_enchantments={levels:{"ultrahard:lifesteal":4}}]` 获取 IV 级；将等级数字改为更高值即可获取更高等级。

7. **敌人装备**：Ultra Hard 下敌人生成护甲的概率为原版的 2 倍。

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
