package dev.cosmonauticsroll;

import com.mojang.logging.LogUtils;

import dev.cosmonauticsroll.debug.DebugCommand;
import dev.cosmonauticsroll.region.RegionDebugTicker;
import dev.cosmonauticsroll.rot.RotationTicker;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;

/**
 * Cosmonautics Roll 主模组类。
 *
 * <p>第一版为单人模组：在适用区域（主世界 Y &gt;= 8000 或
 * {@code rocketnautics:deep_space}）内，让玩家身体与视角跟随脚部接触表面
 * （地面/墙面/天花板）旋转，并与 Do a Barrel Roll、Cosmonautics、Aeronautics
 * 兼容。本模组不改变重力大小，只负责方向、姿态和旋转表现。</p>
 *
 * <p>阶段 1：项目骨架 + DEBUG 调试基础设施（开关、日志、游戏内命令）。
 * 事件、Mixin 与网络注册在后续阶段加入。</p>
 */
@Mod(CosmonauticsRoll.MODID)
public class CosmonauticsRoll {

    /** 模组 id，与 {@code META-INF/neoforge.mods.toml} 中的 modId 一致。 */
    public static final String MODID = "cosmonautics_roll";

    /** 模组日志。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组加载入口。FML 会自动传入 mod 事件总线。
     *
     * @param modEventBus 模组事件总线，后续阶段用于注册 DeferredRegister 与事件监听
     */
    public CosmonauticsRoll(IEventBus modEventBus) {
        // DEBUG 调试命令注册（/cosmonauticsroll debug on|off|status）。
        DebugCommand.register();
        // 阶段 2 调试观察挂接：游戏内输出区域状态日志（仅调试开启时），
        // 用于 PRD 3.1 的游戏内验收。
        RegionDebugTicker.register();
        // 阶段 4 正式旋转逻辑：适用区域内脚部检测 → 平滑站立旋转，
        // 离开区域平滑恢复竖直（PRD 3.3）。
        RotationTicker.register();
    }
}
