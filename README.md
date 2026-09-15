# Ultra Hard（超困难）

适用于 **Minecraft 26.2** 的 Fabric + Kotlin 模组。开启后为世界启用「超困难」规则：无自然回血、饥饿消耗加快、敌对生物与玩家攻击皆为一击必杀。

## 依赖

请一并安装：

- [Fabric Loader](https://fabricmc.net/use/) ≥ 0.19.5
- [Fabric API](https://modrinth.com/mod/fabric-api)（`0.160.0+26.2` 或同版本兼容构建）
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

默认关闭。任选其一：

### 命令（推荐，需要权限等级 2 / OP）

```text
/ultrahard on      # 开启
/ultrahard off     # 关闭
/ultrahard status  # 查看状态
/ultrahard         # 同 status
```

### 游戏规则

```text
/gamerule ultrahard:ultraHard true
/gamerule ultrahard:ultraHard false
```

也可在「编辑游戏规则」界面的杂项分类中修改。

## 效果说明（服务端权威）

当 `ultrahard:ultraHard` 为 `true` 时：

1. **无自然回血**：取消饱食度/饱和度带来的自然恢复（类似 `naturalRegeneration false`）；药水等其它治疗仍可用。
2. **饥饿加快 50%**：玩家饥饿值消耗（exhaustion）× 1.5。
3. **敌对生物一击必杀**：来自 `Monster` / `Enemy` 的伤害会立刻击杀玩家。
4. **玩家攻击一击必杀**：开启超困难时，该玩家对任意 `LivingEntity` 的攻击会一击必杀。

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
