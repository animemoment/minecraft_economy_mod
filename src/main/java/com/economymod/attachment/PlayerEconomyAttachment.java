package com.economymod.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class PlayerEconomyAttachment {
    public static final Codec<PlayerEconomyAttachment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.fieldOf("balance").forGetter(a -> a.balance)
            ).apply(instance, PlayerEconomyAttachment::new)
    );

    private long balance;

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public PlayerEconomyAttachment() {
        this.balance = 100L;
    }

    public PlayerEconomyAttachment(long balance) {
        this.balance = balance;
    }

    public long getBalance() {
        return balance;
    }

    public boolean hasEnough(long amount) {
        return this.balance >= amount;
    }

    public void add(long amount) {
        this.balance += amount;
    }

    public boolean subtract(long amount) {
        if (!hasEnough(amount)) return false;
        this.balance -= amount;
        return true;
    }
}