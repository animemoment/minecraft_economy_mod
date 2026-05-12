package com.economymod.attachment;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

public class VillagerAttachmentSerializer implements IAttachmentSerializer<CompoundTag, VillagerAttachment> {

    @Override
    public VillagerAttachment read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
        VillagerAttachment attachment;
        if (holder instanceof net.minecraft.world.entity.npc.Villager villager) {
            attachment = new VillagerAttachment(villager);
        } else {
            attachment = new VillagerAttachment(null);
        }
        if (tag != null && !tag.isEmpty()) {
            attachment.deserializeNBT(provider, tag);
        }
        return attachment;
    }

    @Override
    public CompoundTag write(VillagerAttachment attachment, HolderLookup.Provider provider) {
        return attachment.serializeNBT(provider);
    }
}