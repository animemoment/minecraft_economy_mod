package com.economymod.zones;

public enum ZoneType {
    TREE("tree"),               // Деревья (брёвна + листья)
    VILLAGE("village"),         // Территория деревни
    CAVE("cave"),               // Пещеры и полости под землей (под Y=40)
    WATER("water"),             // Водоемы (озера, реки, океаны)
    MOUNTAIN("mountain"),       // Горные массивы и высоты
    AIR("air"),                 // Открытое воздушное пространство на поверхности
    UNDERGROUND("underground"); // Сплошная подземная порода (камень, земля, руды)

    private final String name;

    ZoneType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}