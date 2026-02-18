package com.PixelmonRaid;

import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;

public class RaidBossParticipant extends WildPixelmonParticipant {

    public RaidBossParticipant(PixelmonEntity... pixelmon) {

        super(false, pixelmon);
    }

}