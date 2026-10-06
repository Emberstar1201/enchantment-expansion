package com.github.emberstar1201.enchantmentex.sound;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组音效注册器。
 *
 * 丧尸娘 / 溺尸娘共用一套语音（音频文件为 husk_girl_*.ogg）：
 *   - husk_girl.idle     闲置语音：sounds.json 中配置 3 个音频，播放时随机三选一
 *   - husk_girl.hust     受伤语音：2 个音频随机二选一（hust 为既定命名，不是拼写错误）
 *   - husk_girl.farewell 死亡遗言：仅 1 个音频
 *
 * 注册名（husk_girl.idle 等）必须与 assets/enchantment_expansion/sounds.json
 * 中的事件键完全一致，否则游戏内只会播放静音并在日志报缺失音效警告。
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EnchantmentExpansion.MODID);

    /** 闲置环境音（随机 idle_1 / idle_2 / idle_3）。 */
    public static final RegistryObject<SoundEvent> HUSK_GIRL_IDLE =
            register("husk_girl.idle");

    /** 受伤音（随机 hust_1 / hust_2）。 */
    public static final RegistryObject<SoundEvent> HUSK_GIRL_HUST =
            register("husk_girl.hust");

    /** 死亡遗言（farewell_1，仅此一个）。 */
    public static final RegistryObject<SoundEvent> HUSK_GIRL_FAREWELL =
            register("husk_girl.farewell");

    /** 啃食生肉（eat_1，仅此一个）：非满血自动消耗背包生肉回血时播放。 */
    public static final RegistryObject<SoundEvent> HUSK_GIRL_EAT =
            register("husk_girl.eat");

    /**
     * 注册可变传播距离的音效（传播范围由播放时的音量参数决定，与原版生物音效一致）。
     */
    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(EnchantmentExpansion.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
