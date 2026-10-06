package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
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
 *   2. 头发 / 夹克 / 袖子 / 裤腿第二层全部作为「活动部件的子部件」挂在
 *      head / body / right_arm / left_arm / right_leg / left_leg 下，
 *      会随父部件一起旋转，僵尸前伸 / 走路摆臂时不会脱离；幼体分头、身两遍
 *      渲染（AgeableListModel）时，头发也能正确罩在被放大的头上。
 *   3. 不包含任何盔甲几何：丧尸娘 / 溺尸娘穿戴盔甲时只享受装备属性
 *      （护甲、韧性、击退抗性，由装备槽自动结算），不显示盔甲外观，
 *      渲染器也不注册盔甲渲染层。
 */
public class ZombieGirlModel<T extends Mob> extends HumanoidModel<T> {

    /**
     * 真正的头发第二层。
     *
     * <p>父类 HumanoidModel 构造器硬性要求根节点下存在名为 "hat" 的部件
     * （{@code root.getChild("hat")}），但幼体渲染时 AgeableListModel 把根 hat
     * 归入「身体遍」（0.5 倍、降到胸口位置），头发会脱离被放大的头部。
     * 因此网格里根 hat 只是一个空占位，真正的头发方块挂在 head 下作为子部件，
     * 由这里的引用控制显隐。</p>
     */
    private final net.minecraft.client.model.geom.ModelPart hair;

    /** 水面低于此值（相对脚底，脖子/下巴高度）→ 头部完整露出水面，显示头发。 */
    private static final double HAIR_SHOW_SURFACE = 1.6D;
    /** 水面高于此值（相对脚底，约头顶过半）→ 只剩约半个头在水面之上，立刻隐藏头发。 */
    private static final double HAIR_HIDE_SURFACE = 1.78D;

    /** 第二层衣物（夹克 / 双袖 / 双裤腿）：溺尸娘完全没入水中时整体隐藏，只渲染一层本体皮肤。 */
    private final net.minecraft.client.model.geom.ModelPart jacket;
    private final net.minecraft.client.model.geom.ModelPart rightSleeve;
    private final net.minecraft.client.model.geom.ModelPart leftSleeve;
    private final net.minecraft.client.model.geom.ModelPart rightPants;
    private final net.minecraft.client.model.geom.ModelPart leftPants;

    public ZombieGirlModel(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
        this.hair = this.head.getChild("hat");
        // 第二层衣物挂在对应活动部件下（createLayer 中的命名），取出引用控制显隐
        this.jacket = this.body.getChild("jacket");
        this.rightSleeve = this.rightArm.getChild("right_sleeve");
        this.leftSleeve = this.leftArm.getChild("left_sleeve");
        this.rightPants = this.rightLeg.getChild("right_pants");
        this.leftPants = this.leftLeg.getChild("left_pants");
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
        // 头发第二层（head 的子部件）按实体类型分别处理：
        //   溺尸娘：头发按头部露出水面的程度动态显隐（见下方逻辑）；
        //   普通丧尸娘：盔甲不渲染外观，不存在头盔穿模问题，头发始终显示。
        // 注意不能再操作 this.hat —— 它现在是根节点上的空占位部件。
        if (entity instanceof DrownedGirlEntity drownedGirl) {
            // 头发按「头部露出水面的程度」动态切换（不再是恒为隐藏）：
            //   半个身子出水、头和半个身体都在空气中 → 渲染头发；
            //   水面漫到头顶过半（只剩约半个头在水面之上）→ 立刻隐藏；
            //   两个阈值之间保持上一次状态，防止随水面上下浮动时闪烁。
            // 完全上岸（周围无水）时视为水面极低，头发恒定显示。
            double surfaceAboveFeet = waterSurfaceHeight(drownedGirl) - drownedGirl.getY();
            if (surfaceAboveFeet < HAIR_SHOW_SURFACE) {
                this.hair.visible = true;
            } else if (surfaceAboveFeet > HAIR_HIDE_SURFACE) {
                this.hair.visible = false;
            }
            // 衣物第二层维持原规则：眼睛没入水中（完全没入）时只渲染一层本体皮肤
            boolean submerged = entity.isUnderWater();
            this.jacket.visible = !submerged;
            this.rightSleeve.visible = !submerged;
            this.leftSleeve.visible = !submerged;
            this.rightPants.visible = !submerged;
            this.leftPants.visible = !submerged;
            animateDrownedPose(ageInTicks);
        } else {
            this.hair.visible = true;
            // 丧尸娘的第二层衣物恒为显示（防止共享模型状态时被上一帧残留隐藏）
            this.jacket.visible = true;
            this.rightSleeve.visible = true;
            this.leftSleeve.visible = true;
            this.rightPants.visible = true;
            this.leftPants.visible = true;
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
     * 计算实体脚部所在竖直水柱的水面世界高度。
     * 从头顶上沿所在方块（脚底 +2）向下扫到脚底方块，取最高含水方块，
     * 水面高度 = 方块底边 Y + 方块内液面自身高度（0~1，满格水源约 0.89）。
     * 周围完全无水时返回负无穷大（视为头发完全露出，恒定显示）。
     * 每帧每只个体仅 3 次流体查询，开销可忽略。
     */
    private static double waterSurfaceHeight(DrownedGirlEntity girl) {
        Level level = girl.level();
        int x = girl.getBlockX();
        int z = girl.getBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = girl.getBlockY() + 2; y >= girl.getBlockY(); y--) {
            pos.set(x, y, z);
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty()) {
                return y + fluid.getOwnHeight();
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /**
     * 构建 Alex 细手玩家布局网格。
     * UV / 尺寸 / 偏移与 net.minecraft.client.model.PlayerModel.createMesh
     * (CubeDeformation, slim=true) 保持一致。
     */
    private static LayerDefinition createLayer(CubeDeformation deformation) {
        // 基础人形：head(0,0) / hat(32,0) / body(16,16) / right_leg(0,16)
        MeshDefinition mesh = HumanoidModel.createMesh(deformation, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");
        PartDefinition body = root.getChild("body");

        // —— 头发第二层 ——
        // 原版 mesh 的 hat 挂在根节点；AgeableListModel 幼体渲染时把根 hat 当作
        // 「身体部件」以 0.5 倍渲染在胸口高度，导致小丧尸娘头上没有第二层。
        // 处理：根 hat 替换为空 CubeListBuilder 占位（HumanoidModel 构造器会
        // root.getChild("hat")，不能缺失），真正的头发方块改为 head 的子部件：
        // 成体随头部转动，幼体则随「头部遍」以 0.75 倍正确罩在放大的头上。
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, deformation.extend(0.5F)),
                PartPose.ZERO);

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
