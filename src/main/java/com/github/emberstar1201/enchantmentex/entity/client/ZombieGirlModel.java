package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 丧尸娘模型：使用「Alex 细手玩家 64x64 皮肤」UV 布局，动画沿用僵尸。
 *
 * 关键点：
 *   1. Alex 皮肤手臂宽度为 3 像素（classic 是 4 像素），手臂根部 Y 偏移 2.5。
 *   2. 夹克 / 袖子 / 裤腿第二层全部作为「活动部件的子部件」挂在
 *      body / right_arm / left_arm / right_leg / left_leg 下，
 *      会随父部件一起旋转，僵尸前伸 / 走路摆臂时不会脱离手臂。
 *   3. 不包含任何盔甲几何：丧尸娘 / 溺尸娘穿戴盔甲时只享受装备属性
 *      （护甲、韧性、击退抗性，由装备槽自动结算），不显示盔甲外观，
 *      渲染器也不注册盔甲渲染层。
 */
public class ZombieGirlModel<T extends Mob> extends HumanoidModel<T> {

    public ZombieGirlModel(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    /** 主体层定义：Alex 细手玩家皮肤 UV，64x64。 */
    public static LayerDefinition createBodyLayer() {
        return createLayer(CubeDeformation.NONE);
    }

    /**
     * 动画：普通人形（走路摆臂）+ 僵尸双臂前伸。
     * animateZombieArms 参数与 AbstractZombieModel 完全一致：
     *   左臂、右臂、是否处于攻击姿态、攻击挥砍进度(attackTime)、实体 tick。
     */
    @Override
    public void prepareMobModel(T entity, float limbSwing, float limbSwingAmount, float partialTick) {
        this.rightArmPose = ArmPose.EMPTY;
        this.leftArmPose = ArmPose.EMPTY;
        ItemStack itemStack = entity.getItemInHand(InteractionHand.MAIN_HAND);
        if (entity instanceof DrownedGirlEntity drownedGirl
                && itemStack.is(Items.TRIDENT) && drownedGirl.isAggressive()) {
            if (drownedGirl.getMainArm() == HumanoidArm.RIGHT) {
                this.rightArmPose = ArmPose.THROW_SPEAR;
            } else {
                this.leftArmPose = ArmPose.THROW_SPEAR;
            }
        }
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // 皮肤第二层（hat，即头发）按实体类型分别处理：
        //   溺尸娘游泳 / 举矛动作中头部大幅倾斜，头发必然穿模，始终隐藏；
        //   普通丧尸娘：盔甲不渲染外观，不存在头盔穿模问题，头发始终显示。
        if (entity instanceof DrownedGirlEntity) {
            this.hat.visible = false;
            animateDrownedPose(ageInTicks);
        } else {
            this.hat.visible = true;
            AnimationUtils.animateZombieArms(this.leftArm, this.rightArm,
                    entity.isAggressive(), this.attackTime, ageInTicks);
        }
    }

    private void animateDrownedPose(float ageInTicks) {
        if (this.leftArmPose == ArmPose.THROW_SPEAR) {
            this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) Math.PI;
            this.leftArm.yRot = 0.0F;
        }
        if (this.rightArmPose == ArmPose.THROW_SPEAR) {
            this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) Math.PI;
            this.rightArm.yRot = 0.0F;
        }
        if (this.swimAmount > 0.0F) {
            this.rightArm.xRot = rotlerpRad(this.swimAmount, this.rightArm.xRot, -2.5132742F)
                    + this.swimAmount * 0.35F * Mth.sin(0.1F * ageInTicks);
            this.leftArm.xRot = rotlerpRad(this.swimAmount, this.leftArm.xRot, -2.5132742F)
                    - this.swimAmount * 0.35F * Mth.sin(0.1F * ageInTicks);
            this.rightArm.zRot = rotlerpRad(this.swimAmount, this.rightArm.zRot, -0.15F);
            this.leftArm.zRot = rotlerpRad(this.swimAmount, this.leftArm.zRot, 0.15F);
            this.leftLeg.xRot -= this.swimAmount * 0.55F * Mth.sin(0.1F * ageInTicks);
            this.rightLeg.xRot += this.swimAmount * 0.55F * Mth.sin(0.1F * ageInTicks);
            this.head.xRot = 0.0F;
        }
    }

    /**
     * 构建 Alex 细手玩家布局网格。
     * UV / 尺寸 / 偏移与 net.minecraft.client.model.PlayerModel.createMesh
     * (CubeDeformation, slim=true) 保持一致。
     */
    private static LayerDefinition createLayer(CubeDeformation deformation) {
        // 基础人形：head(0,0) / hat(32,0，head 的子部件) / body(16,16) / right_leg(0,16)
        MeshDefinition mesh = HumanoidModel.createMesh(deformation, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");

        // —— Alex 细手臂（3 像素宽，根部 Y=2.5），标准 12 像素长度 ——
        // 右臂 UV(40,16)：x 起点 -2
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, deformation),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
        // 左臂 UV(32,48)：x 起点 -1
        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(32, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, deformation),
                PartPose.offset(5.0F, 2.5F, 0.0F));

        // 左腿 UV(16,48)（僵尸默认布局没有此 UV）
        PartDefinition rightLeg = root.getChild("right_leg");
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(16, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, deformation),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        // 第二层外扩量，与玩家模型一致 +0.25。
        // 关键：第二层全部挂在对应活动部件下作为子部件（PartPose.ZERO 继承
        // 父部件的枢轴和全部旋转），前伸 / 摆臂时与皮肤本体完全同步。
        CubeDeformation overlay = deformation.extend(0.25F);

        // —— 夹克（躯干第二层）UV(16,32) ——
        body.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(16, 32)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, overlay),
                PartPose.ZERO);
        // —— 右袖 UV(40,32)，右臂子部件 ——
        rightArm.addOrReplaceChild("right_sleeve",
                CubeListBuilder.create().texOffs(40, 32)
                        .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, overlay),
                PartPose.ZERO);
        // —— 左袖 UV(48,48)，左臂子部件 ——
        leftArm.addOrReplaceChild("left_sleeve",
                CubeListBuilder.create().texOffs(48, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, overlay),
                PartPose.ZERO);
        // —— 右裤腿 UV(0,32)，右腿子部件 ——
        rightLeg.addOrReplaceChild("right_pants",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, overlay),
                PartPose.ZERO);
        // —— 左裤腿 UV(0,48)，左腿子部件 ——
        leftLeg.addOrReplaceChild("left_pants",
                CubeListBuilder.create().texOffs(0, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, overlay),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }
}
