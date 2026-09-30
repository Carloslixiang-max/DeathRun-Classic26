# Classic26.3：出生点修复与中文升级

旧版出生点选择只检查可站立，Death 搜索进入火蛇 B 的草地坑，Runner 范围越过起跑门两侧。新版把 Runner 限于 x=85..92、y=25、z=79..85 的原始草地起跑区并朝西；Death 改为 (75.5,25,56.5)、(76.5,25,57.5) 的原始控制平台。23 个机关传送使用逐项核对的控制落点，不再跳到屋顶按钮附近。它们是基于归档几何的可玩位置，不能据此宣称原服坐标完全一致。

## 已安装地图的升级

1. 所有参赛者 `/dr leave`，OP `/dr stop to-bee-or-not-to-bee`，离开 `tobee` 世界。
2. 停服，将旧 JAR 移出 plugins，安装 `DeathRun-Classic26-1.4.1-classic26.3.jar`，完整启动。
3. 从控制台或其他世界执行：

```text
/dr map backup to-bee-or-not-to-bee
/dr tobee repair tobee
/dr tobee readiness tobee
/dr map backup to-bee-or-not-to-bee
```

`repair` 会先保留旧 `map.yml.before-spawn-fix.bak`，再升级出生点、出生朝向、检查点复活位置/名称、地图名称和 46 块原说明牌；保留机关、积分、人数、模式和编辑状态。地图必须等待且无人；落点检查失败时拒绝保存出生点。若 readiness 说明编辑模式仍开启，执行 `/dr map disable to-bee-or-not-to-bee` 后再检查。

旧 language.yml 首次启动会备份为 `language.yml.before-chinese-v3.bak`，然后升级文字到中文，保留计分板开关和刷新间隔。后续启动不会覆盖你的中文自定义文字。

## 试玩

至少两名玩家 `/dr join to-bee-or-not-to-bee`，OP `/dr start to-bee-or-not-to-bee`。
起跑倒计时前聊天会说明规则和各角色操作。跑酷者：4/5/6 格羽毛右键为左/后/右冲刺，各自冷却60秒。死神：1/9格选择机关，5格传送，2–4/6–8格激活。退出 `/dr leave`，强制结束 `/dr stop to-bee-or-not-to-bee`。

冲刺、机关激活、检查点、生命扣除在起跑屏障打开后才开始处理。检查点忽略已取消的移动/传送；普通移动不会把传送轨迹误认为跑过检查点。插件伤害处理在 HIGHEST 优先级取消原版伤害，避免原版伤害继续结算。

真人多人完整通关、断线重连和视觉体验仍需实测；自动化验证不能替代这些检查。
