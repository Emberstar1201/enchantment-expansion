package com.github.emberstar1201.enchantmentex.client;

import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import com.github.emberstar1201.enchantmentex.entity.SurvivorGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import com.github.emberstar1201.enchantmentex.network.GirlInteractionPacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** 少女交互面板，固定在屏幕右侧。 */
public final class GirlInteractionScreen extends Screen {
    private final int entityId;
    private final boolean tamed;
    private boolean chatExpanded;
    private boolean menuExpanded;
    private Boolean requestedSitting;

    public GirlInteractionScreen(int entityId, boolean tamed) {
        super(Component.translatable("gui.enchantment_expansion.girl_interaction.title"));
        this.entityId = entityId;
        this.tamed = tamed;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int x = this.width - 150;
        int y = this.height / 2 - 48;
        this.addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> {
                            this.menuExpanded = !this.menuExpanded;
                            this.init(this.minecraft, this.width, this.height);
                        })
                .bounds(x - 24, y, 20, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.girl_interaction.chat"),
                        button -> {
                            this.chatExpanded = !this.chatExpanded;
                            this.init(this.minecraft, this.width, this.height);
                        })
                .bounds(x, y, 130, 20).build());
        y += 24;
        if (this.menuExpanded) {
            this.addRenderableWidget(Button.builder(
                            Component.translatable("gui.enchantment_expansion.girl_interaction.event_book"),
                            button -> openEventBook())
                    .bounds(x + 10, y, 120, 20).build());
            y += 24;
            this.addRenderableWidget(Button.builder(
                            Component.translatable("gui.enchantment_expansion.girl_interaction.ai_config"),
                            button -> this.minecraft.setScreen(new AiConfigurationScreen(this, currentPersonaKey())))
                    .bounds(x + 10, y, 120, 20).build());
            y += 24;
        }
        if (this.chatExpanded) {
            this.addRenderableWidget(Button.builder(
                            Component.translatable("gui.enchantment_expansion.girl_interaction.ai_dialogue"),
                            button -> openAiDialogue())
                    .bounds(x + 10, y, 120, 20).build());
            y += 24;
        }
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.girl_interaction.gift"),
                        button -> this.minecraft.setScreen(new GiftScreen(this, this.entityId)))
                .bounds(x, y, 130, 20).build());
        y += 24;
        boolean sitting = this.requestedSitting != null
                ? this.requestedSitting
                : this.minecraft.level != null
                && this.minecraft.level.getEntity(this.entityId) instanceof FriendlyGirlInventory girl
                && girl.isOrderedToSit();
        Button followButton = Button.builder(
                        stateLabel("gui.enchantment_expansion.girl_interaction.follow", !sitting),
                        button -> setFollowing(true))
                .bounds(x, y, 130, 20).build();
        followButton.active = this.tamed;
        this.addRenderableWidget(followButton);
        y += 24;
        Button stayButton = Button.builder(
                        stateLabel("gui.enchantment_expansion.girl_interaction.stay", sitting),
                        button -> setFollowing(false))
                .bounds(x, y, 130, 20).build();
        stayButton.active = this.tamed;
        this.addRenderableWidget(stayButton);
    }

    private void send(int action) {
        NetworkHandler.CHANNEL.sendToServer(new GirlInteractionPacket(this.entityId, action));
        this.minecraft.setScreen(null);
    }

    private void setFollowing(boolean following) {
        this.requestedSitting = !following;
        NetworkHandler.CHANNEL.sendToServer(new GirlInteractionPacket(
                this.entityId,
                following ? GirlInteractionPacket.FOLLOW : GirlInteractionPacket.STAY));
        this.init(this.minecraft, this.width, this.height);
    }

    private Component stateLabel(String key, boolean enabled) {
        return Component.translatable(key)
                .copy()
                .append(Component.literal("："))
                .append(Component.translatable(enabled
                        ? "gui.enchantment_expansion.girl_interaction.enabled"
                        : "gui.enchantment_expansion.girl_interaction.disabled"));
    }

    private String currentPersonaKey() {
        if (this.minecraft.level == null) {
            return "zombie_girl";
        }
        Entity entity = this.minecraft.level.getEntity(this.entityId);
        if (entity instanceof SurvivorGirlEntity survivor) {
            return survivor.getOriginPersonaKey();
        }
        if (entity instanceof DrownedGirlEntity) {
            return "drowned_girl";
        }
        return "zombie_girl";
    }

    private void openEventBook() {
        if (this.minecraft.level != null) {
            Entity entity = this.minecraft.level.getEntity(this.entityId);
            if (entity != null) {
                this.minecraft.setScreen(new AiMemoryScreen(this, entity.getUUID(), entity.getName().getString()));
            }
        }
    }

    private void openAiDialogue() {
        if (this.minecraft.level == null) {
            this.minecraft.player.displayClientMessage(Component.translatable(
                    "gui.enchantment_expansion.ai.dialogue.unavailable"), false);
            return;
        }
        Entity entity = this.minecraft.level.getEntity(this.entityId);
        String personaKey;
        boolean curedSurvivor = entity instanceof SurvivorGirlEntity;
        if (entity instanceof SurvivorGirlEntity survivor) {
            personaKey = survivor.getOriginPersonaKey();
        } else if (entity instanceof DrownedGirlEntity) {
            personaKey = "drowned_girl";
        } else if (entity instanceof ZombieGirlEntity) {
            personaKey = "zombie_girl";
        } else {
            this.minecraft.player.displayClientMessage(Component.translatable(
                    "gui.enchantment_expansion.ai.dialogue.unavailable"), false);
            return;
        }

        AiConfigurationStore.AiConfiguration configuration = AiConfigurationStore.get();
        AiConfigurationStore.Persona configuredPersona = configuration.personas.get(personaKey);
        if (configuredPersona == null) {
            this.minecraft.player.displayClientMessage(Component.translatable(
                    "gui.enchantment_expansion.ai.dialogue.unavailable"), false);
            return;
        }
        AiConfigurationStore.Persona persona = configuredPersona.copy();
        persona.facts.add("当前形态：" + (curedSurvivor ? "幸存者少女（由治愈变回人类）" : persona.name));
        // 名字存在实体 CustomName 上：每只少女独立，且随存档保存、不跨存档共享。
        String configuredName = entity.getCustomName() == null
                ? null
                : entity.getCustomName().getString();
        if (configuredName != null && !configuredName.isBlank()) {
            this.minecraft.setScreen(new AiDialogueScreen(
                    this, entity.getUUID(), configuration.text, persona, configuredName,
                    entity instanceof FriendlyGirlInventory girl ? girl.getAffection() : 0));
            return;
        }
        String defaultName = persona.name == null || persona.name.isBlank() ? "少女" : persona.name;
        this.minecraft.setScreen(new AiNameScreen(this, defaultName, name -> {
            // 通过服务端写入实体 CustomName，随实体 NBT 持久化。
            NetworkHandler.CHANNEL.sendToServer(new GirlInteractionPacket(
                    this.entityId, GirlInteractionPacket.RENAME, name));
            this.minecraft.setScreen(new AiDialogueScreen(
                    this, entity.getUUID(), configuration.text, persona, name,
                    entity instanceof FriendlyGirlInventory girl ? girl.getAffection() : 0));
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int x = this.width - 150;
        int y = this.height / 2 - 70;
        graphics.drawString(this.font, this.title, x, y, 0xFFFFFF);
        if (this.minecraft.level != null
                && this.minecraft.level.getEntity(this.entityId) instanceof FriendlyGirlInventory girl) {
            Component affection = Component.translatable(
                    "gui.enchantment_expansion.girl_interaction.affection",
                    girl.getAffection());
            graphics.drawString(this.font, affection, x, y + 14, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
