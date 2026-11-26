package com.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * एलियन क्रूज़र "स्टिंग-रे": चपटा हीरे जैसा ढाँचा, दो बड़े पंख, ऊपर पूँछ-पंख, हरी लाइटें, नीचे बड़ी तोप।
 * नाक +Z की तरफ़ है, नाक में खुलने वाला दरवाज़ा (हैंगर)। 1 यूनिट = 1 ब्लॉक (scale से बड़ा-छोटा)।
 */
public final class ShipModel {
    private ShipModel() {}

    private static void b(PoseStack ps, MultiBufferSource buf, Block bl, float x, float y, float z, float sx, float sy, float sz) {
        FxDraw.box(ps, buf, bl, x, y, z, sx, sy, sz);
    }

    /**
     * @param door   0 = बंद, 1 = पूरा खुला
     * @param glow   0..1 तोप की चमक
     * @param blink  लाइटें जलें/बुझें
     */
    public static void render(PoseStack ps, MultiBufferSource buf, float scale, float door, float glow, boolean blink) {
        ps.pushPose();
        ps.scale(scale, scale, scale);
        Block hull = Blocks.BLACK_CONCRETE, hull2 = Blocks.PURPLE_CONCRETE, trim = Blocks.CRYING_OBSIDIAN,
                light = Blocks.VERDANT_FROGLIGHT, eng = Blocks.SEA_LANTERN, glass = Blocks.TINTED_GLASS;

        // मुख्य ढाँचा: पीछे से आगे पतला होता हुआ (सीढ़ीनुमा)
        for (int i = 0; i < 8; i++) {
            float z = -8 + i * 2;
            float w = 9 - Math.abs(i - 3) * 1.3F;
            float h = 2.4F - Math.abs(i - 3) * 0.22F;
            b(ps, buf, i % 2 == 0 ? hull : hull2, -w / 2, -h / 2, z, w, h, 2.0F);
        }
        // ऊपर कॉकपिट गुंबद
        b(ps, buf, glass, -1.5F, 1.0F, 0, 3, 0.9F, 4);
        b(ps, buf, trim, -1.7F, 0.9F, -0.2F, 3.4F, 0.2F, 4.4F);
        // पंख (बाएँ-दाएँ), पीछे की ओर झुके हुए
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 3.5F, -0.2F, -2.0F);
            ps.mulPose(Axis.YP.rotationDegrees(s * 28));
            b(ps, buf, hull, s > 0 ? 0 : -9, -0.25F, -2.5F, 9, 0.5F, 5);
            b(ps, buf, hull2, s > 0 ? 0 : -9, 0.25F, -1.5F, 9, 0.12F, 3);
            b(ps, buf, trim, s > 0 ? 8.5F : -9.0F, -0.4F, -2.6F, 0.6F, 0.8F, 5.2F);
            if (blink) b(ps, buf, light, s > 0 ? 8.7F : -9.1F, -0.15F, 2.2F, 0.4F, 0.3F, 0.4F);
            ps.popPose();
        }
        // पूँछ-पंख (ऊपर)
        b(ps, buf, hull2, -0.25F, 1.0F, -8.5F, 0.5F, 3.2F, 3.0F);
        b(ps, buf, trim, -0.3F, 4.0F, -8.6F, 0.6F, 0.3F, 2.0F);
        if (blink) b(ps, buf, light, -0.2F, 4.2F, -8.0F, 0.4F, 0.4F, 0.4F);
        // इंजन (पीछे)
        for (int s = -1; s <= 1; s++) {
            b(ps, buf, Blocks.IRON_BLOCK, s * 2.2F - 0.8F, -0.8F, -9.0F, 1.6F, 1.6F, 1.0F);
            b(ps, buf, eng, s * 2.2F - 0.6F, -0.6F, -9.2F, 1.2F, 1.2F, 0.3F);
        }
        // हरी लाइट-पट्टियाँ किनारों पर
        for (int i = 0; i < 6; i++) {
            float z = -6 + i * 2.2F;
            if (blink || i % 2 == 0) {
                b(ps, buf, light, -4.4F + Math.abs(i - 3) * 0.6F, -0.1F, z, 0.25F, 0.25F, 0.6F);
                b(ps, buf, light, 4.15F - Math.abs(i - 3) * 0.6F, -0.1F, z, 0.25F, 0.25F, 0.6F);
            }
        }
        // नाक का हैंगर दरवाज़ा (दो पल्ले, खुलते हुए अलग होते हैं)
        float open = door * 1.3F;
        b(ps, buf, Blocks.POLISHED_BLACKSTONE, -1.4F - open, -0.9F, 7.9F, 1.4F, 1.8F, 0.25F);
        b(ps, buf, Blocks.POLISHED_BLACKSTONE, 0.0F + open, -0.9F, 7.9F, 1.4F, 1.8F, 0.25F);
        b(ps, buf, light, -1.5F, 0.95F, 7.95F, 3.0F, 0.12F, 0.2F);
        if (door > 0.05F) b(ps, buf, eng, -1.2F, -0.8F, 7.0F, 2.4F, 0.1F, 0.8F);   // अंदर की रोशनी
        // नीचे की बड़ी तोप
        b(ps, buf, hull, -1.2F, -2.4F, -1.5F, 2.4F, 1.4F, 3.0F);
        b(ps, buf, trim, -0.9F, -3.2F, -1.2F, 1.8F, 0.8F, 2.4F);
        b(ps, buf, glow > 0.3F ? Blocks.SHROOMLIGHT : Blocks.AMETHYST_BLOCK, -0.6F, -3.6F, -0.9F, 1.2F, 0.4F, 1.8F);
        ps.popPose();
    }
}
