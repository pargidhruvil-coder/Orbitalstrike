package com.orbitalstrike.network;

import com.orbitalstrike.OrbitalCannonItem;
import com.orbitalstrike.OrbitalStrikeMod;
import com.orbitalstrike.StrikeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class ModNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OrbitalStrikeMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SetModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetModePacket::encode).decoder(SetModePacket::new).consumerMainThread(SetModePacket::handle).add();
        CHANNEL.messageBuilder(SkipPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SkipPacket::encode).decoder(SkipPacket::new).consumerMainThread(SkipPacket::handle).add();
        CHANNEL.messageBuilder(StrikeFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StrikeFxPacket::encode).decoder(StrikeFxPacket::new).consumerMainThread(StrikeFxPacket::handle).add();
    }

    public static void sendTo(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    // ------------------------------------------------------------ C2S: मोड बदलो
    public static class SetModePacket {
        final int mode;
        public SetModePacket(int mode) { this.mode = mode; }
        public SetModePacket(FriendlyByteBuf b) { this.mode = b.readVarInt(); }
        public void encode(FriendlyByteBuf b) { b.writeVarInt(mode); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
                    if (s.getItem() instanceof OrbitalCannonItem) OrbitalCannonItem.setMode(s, mode);
                }
            }
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------ C2S: सिनेमैटिक स्किप
    public static class SkipPacket {
        public SkipPacket() {}
        public SkipPacket(FriendlyByteBuf b) {}
        public void encode(FriendlyByteBuf b) {}
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) StrikeManager.skip(p);
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------ S2C: स्ट्राइक के इफ़ेक्ट / सिनेमैटिक शुरू
    public static class StrikeFxPacket {
        public final int strikeId;
        public final double x, y, z;
        public final int mode;
        public final boolean shooter;
        public final int age;   // >0 = स्किप होकर आगे बढ़ा

        public StrikeFxPacket(int strikeId, double x, double y, double z, int mode, boolean shooter, int age) {
            this.strikeId = strikeId; this.x = x; this.y = y; this.z = z; this.mode = mode; this.shooter = shooter; this.age = age;
        }
        public StrikeFxPacket(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt(), b.readBoolean(), b.readVarInt());
        }
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(strikeId); b.writeDouble(x); b.writeDouble(y); b.writeDouble(z);
            b.writeVarInt(mode); b.writeBoolean(shooter); b.writeVarInt(age);
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.orbitalstrike.client.ClientFx.onStrikePacket(this));
            ctx.get().setPacketHandled(true);
        }
    }
}
