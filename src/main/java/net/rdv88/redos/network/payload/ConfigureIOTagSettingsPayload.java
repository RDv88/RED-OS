package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.rdv88.redos.Redos;

public record ConfigureIOTagSettingsPayload(BlockPos pos, ItemStack filterItem) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigureIOTagSettingsPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "configure_io_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureIOTagSettingsPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureIOTagSettingsPayload::pos,
            ItemStack.STREAM_CODEC, ConfigureIOTagSettingsPayload::filterItem,
            ConfigureIOTagSettingsPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}