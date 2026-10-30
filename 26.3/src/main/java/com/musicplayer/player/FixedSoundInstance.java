package com.musicplayer.player;

import com.musicplayer.DebugLog;
import com.musicplayer.mixin.WeighedSoundEventsAccessor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.List;

public class FixedSoundInstance extends SimpleSoundInstance {
    private final Identifier resolvedLoc;
    private Sound variantSound;

    public FixedSoundInstance(Identifier eventId, Identifier resolvedLoc) {
        super(eventId, SoundSource.MUSIC, 1.0f, 1.0f, RandomSource.create(),
              false, 0, SoundInstance.Attenuation.NONE, 0.0d, 0.0d, 0.0d, true);
        this.resolvedLoc = resolvedLoc;
        DebugLog.info("FixedSoundInstance created: eventId=%s resolvedLoc=%s", eventId, resolvedLoc);
    }

    @Override
    public WeighedSoundEvents getOrResolve(SoundManager sm) {
        try {
            DebugLog.info("FixedSoundInstance.getOrResolve() for variant=%s", resolvedLoc);
            WeighedSoundEvents wse = super.getOrResolve(sm);

            if (wse != null) {
                if (this.sound != null && resolvedLoc.equals(this.sound.getLocation())) {
                    variantSound = this.sound;
                    DebugLog.info("resolve: super already picked our variant: %s", this.sound.getLocation());
                    return wse;
                }
                Sound found = findSoundInWse(wse);
                if (found != null) {
                    variantSound = found;
                    DebugLog.info("resolve: forced variant to %s", found.getLocation());
                } else {
                    variantSound = this.sound;
                    DebugLog.info("resolve: variant %s not found, keeping: %s",
                        resolvedLoc, this.sound != null ? this.sound.getLocation() : "null");
                }
            }
            return wse;
        } catch (Exception e) {
            DebugLog.error("FixedSoundInstance.getOrResolve() failed: %s", e.getMessage());
            return super.getOrResolve(sm);
        }
    }

    @Override
    public Sound getSound() {
        if (variantSound != null) return variantSound;
        return super.getSound();
    }

    private Sound findSoundInWse(WeighedSoundEvents wse) {
        try {
            List<Weighted<Sound>> list = ((WeighedSoundEventsAccessor) wse).musicplayer$getList();
            if (list == null) return null;
            return findInList(list);
        } catch (Exception e) {
            DebugLog.error("findSoundInWse: %s", e.getMessage());
            return null;
        }
    }

    private Sound findInList(List<Weighted<Sound>> list) {
        for (Weighted<Sound> entry : list) {
            try {
                if (entry instanceof WeighedSoundEvents nestedWse) {
                    List<Weighted<Sound>> nested = ((WeighedSoundEventsAccessor) nestedWse).musicplayer$getList();
                    if (nested != null) {
                        Sound s = findInList(nested);
                        if (s != null) return s;
                    }
                } else {
                    Sound s = entry.getSound(RandomSource.create());
                    if (s != null && resolvedLoc.equals(s.getLocation())) {
                        return s;
                    }
                }
            } catch (Exception e) {
                // skip bad entry
            }
        }
        return null;
    }
}
