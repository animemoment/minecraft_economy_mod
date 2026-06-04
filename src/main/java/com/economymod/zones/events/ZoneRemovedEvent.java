package com.economymod.zones.events;

import com.economymod.zones.ZoneInstance;
import net.minecraft.server.level.ServerLevel;

/**
 * Вызывается на стороне сервера перед тем, как зона будет окончательно стерта из реестра.
 */
public class ZoneRemovedEvent extends ZoneEvent {
    public ZoneRemovedEvent(ServerLevel level, ZoneInstance zone) {
        super(level, zone);
    }
}