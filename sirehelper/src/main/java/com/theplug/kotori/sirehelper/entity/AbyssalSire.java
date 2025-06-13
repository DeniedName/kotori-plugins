package com.theplug.kotori.sirehelper.entity;

import com.theplug.kotori.kotoriutils.methods.NPCInteractions;
import lombok.*;
import net.runelite.api.NPC;
import net.runelite.api.gameval.NpcID;

@Getter
public class AbyssalSire
{
    private static final int TOTAL_HP = 400;
    private static final int PHASE_1_STUN_HP = 325;
    private static final int PHASE_3_HP = 200;
    private static final int PHASE_4_HP = 140;
    private static final int EXPLOSION_ANIMATION_ID = 7098;
    private static final int STUN_TIME_TICKS = 50;

    @NonNull
    @Setter
    private NPC npc;

    private int hp;
    private int phase;
    @Setter
    private boolean stunned;
    private int stunTimer;

    public AbyssalSire(@NonNull final NPC npc)
    {
        this.npc = npc;

        this.hp = TOTAL_HP;
        this.phase = 1;
        this.stunned = false;
        this.stunTimer = STUN_TIME_TICKS;
    }

    public void updateHp()
    {
        final int calculatedHp = NPCInteractions.calculateNpcHp(npc.getHealthRatio(), npc.getHealthScale(), TOTAL_HP);

        if (calculatedHp != -1)
        {
            hp = calculatedHp;
        }
        else if (npc.isDead())
        {
            hp = 0;
        }
    }

    public int getHpPercentage()
    {
        return (getHp() * 100) / TOTAL_HP;
    }

    public void updatePhase()
    {
        int npcId = npc.getId();

        switch (npcId)
        {
            case NpcID.ABYSSALSIRE_SIRE_STASIS_SLEEPING:
            case NpcID.ABYSSALSIRE_SIRE_STASIS_AWAKE:
            case NpcID.ABYSSALSIRE_SIRE_STASIS_STUNNED:
                phase = 1;
                break;
            case NpcID.ABYSSALSIRE_SIRE_PUPPET:
            case NpcID.ABYSSALSIRE_SIRE_WANDERING:
                if (hp < PHASE_3_HP)
                {
                    phase = 3;
                }
                else
                {
                    phase = 2;
                }
                break;
            case NpcID.ABYSSALSIRE_SIRE_PANICKING:
                if (npc.getAnimation() == EXPLOSION_ANIMATION_ID)
                {
                    phase = 4;
                }
                else if (hp < PHASE_4_HP)
                {
                    phase = 4;
                }
                else
                {
                    phase = 3;
                }
                break;
            case NpcID.ABYSSALSIRE_SIRE_APOCALYPSE:
                phase = 4;
                break;
        }
    }

    public int getHpUntilPhaseChange()
    {
        int hpUntil = 0;

        switch (phase)
        {
            case 1:
                hpUntil = Math.max(0, hp - PHASE_1_STUN_HP);
                break;
            case 2:
                hpUntil = Math.max(0, hp - PHASE_3_HP + 1);
                break;
            case 3:
                hpUntil = Math.max(0, hp - PHASE_4_HP + 1);
                break;
            case 4:
                hpUntil = Math.max(0, hp);
                break;
        }

        return hpUntil;
    }

    public void resetStunTimer()
    {
        stunTimer = STUN_TIME_TICKS;
    }

    public void decrementStunTimer()
    {
        stunTimer--;
    }
}
