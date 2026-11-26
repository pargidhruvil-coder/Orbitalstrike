package com.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * एलियन "महल-जहाज़": सबसे आगे बड़ा एलियन चेहरा (चमकती सफ़ेद आँखें, दाँत, बैंगनी सींग),
 * पीछे खंभों वाले दो डेक, चमकती बैंगनी खिड़कियाँ, दो मीनारें, पीछे इंजन, नीचे तोप।
 * चेहरा +Z (आगे) की तरफ़। दरवाज़ा दाईं (+X) तरफ़ आगे के पास। 1 यूनिट = 1 ब्लॉक।
 */
public final class ShipModel {
    private ShipModel() {}

    public static final float CANNON_Y = -7.4F;
    public static final float CANNON_Z = -4.0F;

    private static void b(PoseStack ps, MultiBufferSource buf, Block bl, float x, float y, float z, float sx, float sy, float sz) {
        FxDraw.box(ps, buf, bl, x, y, z, sx, sy, sz);
    }

    public static void render(PoseStack ps, MultiBufferSource buf, float scale, float door, float glow, boolean blink) {
        ps.pushPose();
        ps.scale(scale, scale, scale);
        Block K = Blocks.BLACK_CONCRETE, G = Blocks.GRAY_CONCRETE, Q = Blocks.SMOOTH_QUARTZ, QP = Blocks.QUARTZ_PILLAR,
                A = Blocks.AMETHYST_BLOCK, P = Blocks.PURPLE_CONCRETE, WIN = Blocks.PURPLE_STAINED_GLASS, W = Blocks.WHITE_CONCRETE;
        Block win = blink ? A : P;

        // ---- निचला ढाँचा
        b(ps, buf, K, -5.5F, -4, -22, 11, 4, 34);
        b(ps, buf, K, -4, -5.5F, -20, 8, 1.5F, 30);
        b(ps, buf, A, -3, -5.7F, -18, 6, 0.25F, 26);
        for (float z = -20; z < 11; z += 2) {
            b(ps, buf, win, -5.62F, -3, z + 0.4F, 0.15F, 1.6F, 1.2F);
            b(ps, buf, win, 5.47F, -3, z + 0.4F, 0.15F, 1.6F, 1.2F);
        }
        // ---- पहला डेक: खंभे + अंदर की दीवार + खिड़कियाँ
        b(ps, buf, Q, -5.8F, 0, -22.3F, 11.6F, 0.5F, 34.6F);
        b(ps, buf, K, -4.2F, 0.5F, -21, 8.4F, 3, 32);
        for (float z = -20.5F; z < 11; z += 1.6F) {
            b(ps, buf, QP, -5.3F, 0.5F, z, 0.6F, 3, 0.6F);
            b(ps, buf, QP, 4.7F, 0.5F, z, 0.6F, 3, 0.6F);
            b(ps, buf, WIN, -4.27F, 1.1F, z + 0.1F, 0.1F, 1.9F, 1.0F);
            b(ps, buf, WIN, 4.17F, 1.1F, z + 0.1F, 0.1F, 1.9F, 1.0F);
        }
        b(ps, buf, Q, -5.8F, 3.5F, -22.3F, 11.6F, 0.6F, 34.6F);
        b(ps, buf, A, -5.85F, 3.65F, -22.35F, 11.7F, 0.22F, 34.7F);
        b(ps, buf, K, -5.5F, 4.1F, -22, 11, 0.5F, 34);
        // ---- दूसरा डेक
        b(ps, buf, K, -3, 4.6F, -18, 6, 2.4F, 26);
        for (float z = -17.5F; z < 7; z += 1.6F) {
            b(ps, buf, QP, -3.6F, 4.6F, z, 0.5F, 2.4F, 0.5F);
            b(ps, buf, QP, 3.1F, 4.6F, z, 0.5F, 2.4F, 0.5F);
            b(ps, buf, win, -3.05F, 5.1F, z + 0.1F, 0.1F, 1.5F, 0.9F);
            b(ps, buf, win, 2.95F, 5.1F, z + 0.1F, 0.1F, 1.5F, 0.9F);
        }
        b(ps, buf, Q, -3.9F, 7, -18.3F, 7.8F, 0.5F, 26.6F);
        b(ps, buf, A, -3.95F, 7.1F, -18.35F, 7.9F, 0.2F, 26.7F);
        // आगे का सीढ़ीनुमा छज्जा
        for (int i = 0; i < 4; i++) {
            b(ps, buf, i == 3 ? A : Q, -3.8F + i * 0.9F, 7.5F + i * 0.5F, 6.4F, 7.6F - i * 1.8F, 0.5F, 1.6F);
        }
        // ---- मीनारें
        spire(ps, buf, 0, 7.5F, 2, K, Q, A);
        spire(ps, buf, 0, 7.5F, -15, K, Q, A);
        // ---- सबसे आगे बड़ा एलियन चेहरा (जहाज़ से ऊँचा)
        b(ps, buf, K, -6, -6, 12, 12, 15, 9);                              // सिर
        b(ps, buf, K, -6.4F, 4.6F, 20.6F, 12.8F, 1.3F, 0.8F);              // भौंहें
        b(ps, buf, W, -4.6F, 2.8F, 21.0F, 3.4F, 1.5F, 0.35F);              // चमकती आँखें
        b(ps, buf, W, 1.2F, 2.8F, 21.0F, 3.4F, 1.5F, 0.35F);
        b(ps, buf, K, -1.0F, 0.2F, 21.0F, 2.0F, 2.4F, 0.6F);               // नाक
        for (int i = -5; i < 5; i++) {                                     // दाँत / मुँह
            b(ps, buf, (i & 1) == 0 ? W : G, i + 0.1F, (i & 1) == 0 ? -2.2F : -1.8F, 21.0F, 0.8F, 0.9F, 0.3F);
        }
        b(ps, buf, G, -6.3F, -1.5F, 18.0F, 0.5F, 3.0F, 2.6F);              // गाल
        b(ps, buf, G, 5.8F, -1.5F, 18.0F, 0.5F, 3.0F, 2.6F);
        b(ps, buf, A, -6.35F, 3.5F, 14.0F, 0.4F, 1.8F, 1.8F);              // किनारे की बैंगनी चमक
        b(ps, buf, A, 5.95F, 3.5F, 14.0F, 0.4F, 1.8F, 1.8F);
        b(ps, buf, A, -6.35F, 6.5F, 16.5F, 0.4F, 1.2F, 1.2F);
        b(ps, buf, A, 5.95F, 6.5F, 16.5F, 0.4F, 1.2F, 1.2F);
        // गर्दन पर सफ़ेद-ग्रे कॉलर (ज़िगज़ैग)
        for (int i = -6; i < 6; i++) {
            b(ps, buf, (i & 1) == 0 ? W : G, i, (i & 1) == 0 ? -7.2F : -6.6F, 19.6F, 1, 1.2F, 1.6F);
        }
        b(ps, buf, K, -5, -8.4F, 13, 10, 2.4F, 7);
        // सिर के ऊपर नुकीले कांटे और सींग
        for (int i = -5; i <= 4; i += 3) {
            b(ps, buf, (i & 1) == 0 ? G : W, i, 9, 15 + (i & 1), 1.2F, 1.3F, 1.2F);
            b(ps, buf, K, i + 0.2F, 10.3F, 15.3F + (i & 1), 0.8F, 0.9F, 0.8F);
        }
        b(ps, buf, P, -7.0F, 7.0F, 14.5F, 1.4F, 1.4F, 1.4F);
        b(ps, buf, A, -7.6F, 8.2F, 15.2F, 1.1F, 1.3F, 1.1F);
        b(ps, buf, P, 5.6F, 7.0F, 14.5F, 1.4F, 1.4F, 1.4F);
        b(ps, buf, A, 6.5F, 8.2F, 15.2F, 1.1F, 1.3F, 1.1F);
        // ---- पीछे इंजन
        for (int s = -1; s <= 1; s++) {
            b(ps, buf, G, s * 3.2F - 1.1F, -3.2F, -23, 2.2F, 2.2F, 1);
            b(ps, buf, A, s * 3.2F - 0.8F, -2.9F, -23.25F, 1.6F, 1.6F, 0.3F);
        }
        // ---- दाईं तरफ़ का दरवाज़ा (दो पल्ले आगे-पीछे खिसकते हैं)
        float open = door * 1.6F;
        b(ps, buf, Q, 5.45F, -3.4F, 3.6F, 0.25F, 3.3F, 4.0F);
        if (door > 0.05F) b(ps, buf, Blocks.SEA_LANTERN, 5.3F, -3.2F, 3.9F, 0.1F, 2.8F, 3.4F);
        b(ps, buf, Blocks.POLISHED_BLACKSTONE, 5.6F, -3.2F, 3.85F - open, 0.15F, 2.8F, 1.7F);
        b(ps, buf, Blocks.POLISHED_BLACKSTONE, 5.6F, -3.2F, 5.55F + open, 0.15F, 2.8F, 1.7F);
        b(ps, buf, A, 5.6F, -0.35F, 3.7F, 0.2F, 0.2F, 3.8F);
        // ---- नीचे तोप
        b(ps, buf, K, -2, -6.2F, CANNON_Z - 2, 4, 1.4F, 4);
        b(ps, buf, G, -1.4F, -7, CANNON_Z - 1.4F, 2.8F, 0.8F, 2.8F);
        b(ps, buf, glow > 0.3F ? Blocks.SHROOMLIGHT : A, -0.8F, CANNON_Y, CANNON_Z - 0.8F, 1.6F, 0.4F, 1.6F);
        ps.popPose();
    }

    private static void spire(PoseStack ps, MultiBufferSource buf, float x, float y, float z, Block K, Block Q, Block A) {
        b(ps, buf, K, x - 0.9F, y, z - 0.9F, 1.8F, 1.5F, 1.8F);
        b(ps, buf, Q, x - 0.7F, y + 1.5F, z - 0.7F, 1.4F, 0.4F, 1.4F);
        b(ps, buf, K, x - 0.5F, y + 1.9F, z - 0.5F, 1.0F, 1.6F, 1.0F);
        b(ps, buf, Q, x - 0.35F, y + 3.5F, z - 0.35F, 0.7F, 0.3F, 0.7F);
        b(ps, buf, K, x - 0.25F, y + 3.8F, z - 0.25F, 0.5F, 1.2F, 0.5F);
        b(ps, buf, A, x - 0.2F, y + 5.0F, z - 0.2F, 0.4F, 0.6F, 0.4F);
    }

    /** चमक (ब्लूम): आँखें, मीनारों की बैंगनी लौ, इंजन, तोप — कैमरे की तरफ़ वाले बिलबोर्ड */
    public static void renderGlow(PoseStack ps, Camera cam, Vec3 origin, float yawDeg, float scale, float t, float cannonGlow) {
        float yr = (float) Math.toRadians(yawDeg);
        FxDraw.begin(true);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 eye = origin.add(new Vec3(s * 2.9, 3.55, 21.6).scale(scale).yRot(yr));
            FxDraw.billboard(ps, cam, eye, 2.8F * scale, FxDraw.fx("glow"), 1F, 1F, 1F, 0.95F, 0);
        }
        float flick = 0.8F + 0.2F * (float) Math.sin(t * 0.7);
        for (float z : new float[]{2, -15}) {
            Vec3 tip = origin.add(new Vec3(0, 13.4 + Math.sin(t * 0.3 + z) * 0.15, z).scale(scale).yRot(yr));
            FxDraw.billboard(ps, cam, tip, 1.6F * scale * flick, FxDraw.fx("glow"), 0.75F, 0.3F, 1F, 0.95F, 0);
            FxDraw.billboard(ps, cam, tip.add(0, 0.9 * scale, 0), 0.9F * scale * flick, FxDraw.fx("glow"), 0.9F, 0.6F, 1F, 0.7F, 0);
        }
        for (int s = -1; s <= 1; s++) {
            Vec3 eng = origin.add(new Vec3(s * 3.2, -1.9, -23.5).scale(scale).yRot(yr));
            FxDraw.billboard(ps, cam, eng, 1.6F * scale, FxDraw.fx("glow"), 0.7F, 0.35F, 1F, 0.8F, 0);
        }
        if (cannonGlow > 0) {
            Vec3 gun = origin.add(new Vec3(0, CANNON_Y - 0.3, CANNON_Z).scale(scale).yRot(yr));
            FxDraw.billboard(ps, cam, gun, (1 + cannonGlow * 6) * scale, FxDraw.fx("glow"), 0.75F, 0.4F, 1F, 0.3F + cannonGlow * 0.7F, 0);
            FxDraw.billboard(ps, cam, gun, (0.5F + cannonGlow * 2.5F) * scale, FxDraw.fx("glow"), 1F, 1F, 1F, cannonGlow, 0);
        }
        FxDraw.end();
    }

    /** दुनिया में तोप की जगह */
    public static Vec3 cannonWorld(Vec3 origin, float yawDeg, float scale) {
        return origin.add(new Vec3(0, CANNON_Y, CANNON_Z).scale(scale).yRot((float) Math.toRadians(yawDeg)));
    }
}
