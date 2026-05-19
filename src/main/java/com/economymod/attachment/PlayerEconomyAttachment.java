package com.economymod.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class PlayerEconomyAttachment {
    public static final Codec<PlayerEconomyAttachment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.DOUBLE.fieldOf("balance").forGetter(a -> a.balance)
            ).apply(instance, PlayerEconomyAttachment::new)
    );

    private double balance;

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public PlayerEconomyAttachment() {
        this.balance = 100.0;
    }

    public PlayerEconomyAttachment(double balance) {
        this.balance = balance;
    }

    public double getBalance() {
        return balance;
    }

    public boolean hasEnough(double amount) {
        return this.balance >= amount;
    }

    public void add(double amount) {
        this.balance += amount;
    }

    public boolean subtract(double amount) {
        if (!hasEnough(amount)) return false;
        this.balance -= amount;
        return true;
    }
}