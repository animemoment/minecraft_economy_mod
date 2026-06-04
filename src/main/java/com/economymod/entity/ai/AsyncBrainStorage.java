package com.economymod.entity.ai;

import com.economymod.EconomyMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AsyncBrainStorage {

    // Потокобезопасная очередь сохранения: UUID жителя -> Снимок его мозга
    private static final Map<UUID, CompoundTag> saveQueue = new ConcurrentHashMap<>();
    private static boolean saverActive = false;

    static {
        startAsyncSaverThread();
    }

    /**
     * Поставить снимок мозга жителя в очередь асинхронной записи на диск
     */
    public static void queueSave(Level level, UUID uuid, CompoundTag brainTag) {
        saveQueue.put(uuid, brainTag);
    }

    /**
     * Загрузить данные мозга жителя с диска (или выдать из кэша очереди, если они еще не записались)
     */
    public static CompoundTag load(Level level, UUID uuid) {
        if (saveQueue.containsKey(uuid)) {
            return saveQueue.get(uuid);
        }

        File file = getSaveFile(level, uuid);
        if (!file.exists()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            // Исправлено: в 1.21.1 NbtIo требует NbtAccounter для защиты кучи
            return NbtIo.readCompressed(fis, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            EconomyMod.LOGGER.error("ЭКОНОМИКА ОШИБКА: Не удалось прочитать мозг жителя {} с диска", uuid, e);
        }
        return null;
    }

    private static File getSaveFile(Level level, UUID uuid) {
        File folder = level.getServer().getWorldPath(LevelResource.ROOT).resolve("economymod_brains").toFile();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return new File(folder, uuid.toString() + ".dat");
    }

    /**
     * Фоновая нить записи данных ИИ на жесткий диск
     */
    private static void startAsyncSaverThread() {
        if (saverActive) return;
        saverActive = true;

        Thread thread = new Thread(() -> {
            while (saverActive) {
                try {
                    Thread.sleep(2000); // Сбрасываем кэш на диск каждые 2 секунды

                    if (saveQueue.isEmpty()) continue;

                    var iterator = saveQueue.entrySet().iterator();
                    while (iterator.hasNext()) {
                        var entry = iterator.next();
                        UUID uuid = entry.getKey();
                        CompoundTag tag = entry.getValue();

                        // Потокобезопасно забираем текущий серверный уровень
                        ServerLevelProvider.getCurrentLevel().ifPresent(level -> {
                            File file = getSaveFile(level, uuid);
                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                NbtIo.writeCompressed(tag, fos);
                            } catch (Exception e) {
                                EconomyMod.LOGGER.error("ЭКОНОМИКА ОШИБКА: Ошибка асинхронного сохранения мозга для {}", uuid, e);
                            }
                        });

                        iterator.remove(); // Удаляем из очереди после успешной записи
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    EconomyMod.LOGGER.error("ЭКОНОМИКА ОШИБКА: Сбой в работе фонового потока записи мозгов", e);
                }
            }
        }, "EconomyMod-Brain-Saver");

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Вспомогательный провайдер для потокобезопасного получения ServerLevel в фоновом потоке
     */
    public static class ServerLevelProvider {
        private static net.minecraft.server.MinecraftServer serverInstance = null;

        public static void setServer(net.minecraft.server.MinecraftServer server) {
            serverInstance = server;
        }

        public static java.util.Optional<Level> getCurrentLevel() {
            if (serverInstance != null) {
                return java.util.Optional.of(serverInstance.overworld());
            }
            return java.util.Optional.empty();
        }
    }
}