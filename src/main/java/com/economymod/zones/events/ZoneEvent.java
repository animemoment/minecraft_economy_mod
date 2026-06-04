package com.economymod.zones.events;

import com.economymod.zones.ZoneInstance;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public abstract class ZoneEvent extends Event {
    private final ServerLevel level;
    private final ZoneInstance zone;

    protected ZoneEvent(ServerLevel level, ZoneInstance zone) {
        this.level = level;
        this.zone = zone;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ZoneInstance getZone() {
        return zone;
    }
}