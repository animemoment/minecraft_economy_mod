package com.economymod.registry;

import com.economymod.EconomyMod;
import com.economymod.attachment.PlayerEconomyAttachment;
import com.economymod.attachment.VillagerAttachment;
import com.economymod.attachment.VillagerAttachmentSerializer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, EconomyMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerEconomyAttachment>>
            PLAYER_ECONOMY = ATTACHMENT_TYPES.register("player_economy",
            () -> AttachmentType.builder((Supplier<PlayerEconomyAttachment>) PlayerEconomyAttachment::new)
                    .serialize(PlayerEconomyAttachment.CODEC)
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<VillagerAttachment>>
            VILLAGER = ATTACHMENT_TYPES.register("villager",
            () -> AttachmentType.builder(() -> new VillagerAttachment(null))
                    .serialize(new VillagerAttachmentSerializer())
                    .build());
}