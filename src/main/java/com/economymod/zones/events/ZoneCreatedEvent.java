package com.economymod.zones.events;

import com.economymod.zones.ZoneInstance;
import net.minecraft.server.level.ServerLevel;

/**
 * Вызывается на стороне сервера, когда новая зона успешно зарегистрирована в реестре.
 */
public class ZoneCreatedEvent extends ZoneEvent {
    public ZoneCreatedEvent(ServerLevel level, ZoneInstance zone) {
        super(level, zone);
    }
}