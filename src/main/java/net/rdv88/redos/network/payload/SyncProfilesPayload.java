package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record SyncProfilesPayload(List<ProfileData> profiles) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncProfilesPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "sync_profiles"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProfilesPayload> CODEC = StreamCodec.composite(
            ProfileData.CODEC.apply(ByteBufCodecs.list()), SyncProfilesPayload::profiles,
            SyncProfilesPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public record ProfileData(String name, String id) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileData> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ProfileData::name,
                ByteBufCodecs.STRING_UTF8, ProfileData::id,
                ProfileData::new
        );
    }
}
