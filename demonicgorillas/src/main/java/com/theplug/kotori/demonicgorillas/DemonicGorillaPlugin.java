/*
 * Copyright (c) 2022, Kotori <https://github.com/OreoCupcakes/>
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.theplug.kotori.demonicgorillas;

import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

import com.theplug.kotori.kotoriutils.methods.MiscUtilities;
import com.theplug.kotori.kotoriutils.methods.NPCInteractions;
import com.theplug.kotori.kotoriutils.rlapi.GraphicIDPlus;
import com.theplug.kotori.kotoriutils.rlapi.WorldAreaExtended;
import com.theplug.kotori.kotoriutils.KotoriUtils;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDependency(KotoriUtils.class)
@PluginDescriptor(
	name = "<html><font color=#6b8af6>[P]</font> Demonic Gorillas</html>",
	enabledByDefault = false,
	description = "Count demonic gorilla attacks and display their next possible attack styles",
	tags = {"combat", "overlay", "pve", "pvm", "demonics", "gorilla", "ported", "kotori"}
)
public class DemonicGorillaPlugin extends Plugin
{
	private static final Set<Integer> DEMONIC_PROJECTILES = ImmutableSet.of(GraphicIDPlus.DEMONIC_GORILLA_RANGED, GraphicIDPlus.DEMONIC_GORILLA_MAGIC, GraphicIDPlus.DEMONIC_GORILLA_BOULDER);

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DemonicGorillaOverlay overlay;

	@Inject
	private ClientThread clientThread;

	@Getter(AccessLevel.PACKAGE)
	private Map<NPC, DemonicGorilla> gorillas;

	private List<WorldPoint> recentBoulders;

	private List<PendingGorillaAttack> pendingAttacks;

	private Map<Player, MemorizedPlayer> memorizedPlayers;
	private ArrayList<Projectile> gorillaProjectiles;
	private static final Set<Integer> REGION_IDS = Set.of(8280, 8536);
	private boolean atGorillas;

	private static final int DEMONIC_GORILLA_AOE_ATTACK = 7228;

	@Override
	protected void startUp()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !atDemonicGorillas())
		{
			return;
		}
		
		init();
	}

	@Override
	protected void shutDown()
	{
		atGorillas = false;
		
		overlayManager.remove(overlay);
		gorillas = null;
		recentBoulders = null;
		pendingAttacks = null;
		memorizedPlayers = null;
		gorillaProjectiles = null;
	}
	
	private void init()
	{
		atGorillas = true;
		
		overlayManager.add(overlay);
		gorillas = new HashMap<>();
		recentBoulders = new ArrayList<>();
		pendingAttacks = new ArrayList<>();
		gorillaProjectiles = new ArrayList<>();
		memorizedPlayers = new HashMap<>();
		clientThread.invoke(this::reset); // Updates the list of gorillas and players
	}

	private void clear()
	{
		recentBoulders.clear();
		pendingAttacks.clear();
		memorizedPlayers.clear();
		gorillas.clear();
	}

	private void reset()
	{
		recentBoulders.clear();
		pendingAttacks.clear();
		resetGorillas();
		resetPlayers();
	}

	private void resetGorillas()
	{
		gorillas.clear();
		for (NPC npc : NPCInteractions.getNpcs())
		{
			if (isNpcGorilla(npc.getId()))
			{
				gorillas.put(npc, new DemonicGorilla(npc, client));
			}
		}
	}

	private void resetPlayers()
	{
		memorizedPlayers.clear();
		for (Player player : NPCInteractions.getPlayers())
		{
			memorizedPlayers.put(player, new MemorizedPlayer(player));
		}
	}

	private static boolean isNpcGorilla(int npcId)
	{
		return npcId == NpcID.MM2_DEMON_GORILLA_1_MELEE ||
			npcId == NpcID.MM2_DEMON_GORILLA_1_RANGED ||
			npcId == NpcID.MM2_DEMON_GORILLA_1_MAGIC ||
			npcId == NpcID.MM2_DEMON_GORILLA_2_MELEE ||
			npcId == NpcID.MM2_DEMON_GORILLA_2_RANGED ||
			npcId == NpcID.MM2_DEMON_GORILLA_2_MAGIC;
	}

	private void checkGorillaAttackStyleSwitch(DemonicGorilla gorilla, final DemonicGorilla.AttackStyle... protectedStyles)
	{
		if (gorilla.getAttacksUntilSwitch() <= 0 ||
			gorilla.getNextPosibleAttackStyles().isEmpty())
		{
			gorilla.setNextPosibleAttackStyles(Arrays
				.stream(DemonicGorilla.ALL_REGULAR_ATTACK_STYLES)
				.filter(x -> Arrays.stream(protectedStyles).noneMatch(y -> x == y))
				.collect(Collectors.toList()));
			gorilla.setAttacksUntilSwitch(DemonicGorilla.ATTACKS_PER_SWITCH);
			gorilla.setChangedAttackStyleThisTick(true);
		}
	}

	private DemonicGorilla.AttackStyle getProtectedStyle(Player player)
	{
		HeadIcon headIcon = player.getOverheadIcon();
		if (headIcon == null)
		{
			return null;
		}
		switch (headIcon)
		{
			case MELEE:
				return DemonicGorilla.AttackStyle.MELEE;
			case RANGED:
				return DemonicGorilla.AttackStyle.RANGED;
			case MAGIC:
				return DemonicGorilla.AttackStyle.MAGIC;
			default:
				return null;
		}
	}

	private void onGorillaAttack(DemonicGorilla gorilla, final DemonicGorilla.AttackStyle attackStyle)
	{
		gorilla.setInitiatedCombat(true);

		Player target = (Player) gorilla.getNpc().getInteracting();

		DemonicGorilla.AttackStyle protectedStyle = null;
		if (target != null)
		{
			protectedStyle = getProtectedStyle(target);
		}
		boolean correctPrayer =
			target == null || // If player is out of memory, assume prayer was correct
				(attackStyle != null &&
					attackStyle.equals(protectedStyle));

		if (attackStyle == DemonicGorilla.AttackStyle.BOULDER)
		{
			// The gorilla can't throw boulders when it's meleeing
			gorilla.setNextPosibleAttackStyles(gorilla
				.getNextPosibleAttackStyles()
				.stream()
				.filter(x -> x != DemonicGorilla.AttackStyle.MELEE)
				.collect(Collectors.toList()));
		}
		else
		{
			if (correctPrayer)
			{
				gorilla.setAttacksUntilSwitch(gorilla.getAttacksUntilSwitch() - 1);
			}
			else
			{
				// We're not sure if the attack will hit a 0 or not,
				// so we don't know if we should decrease the counter or not,
				// so we keep track of the attack here until the damage splat
				// has appeared on the player.

				int damagesOnTick = client.getTickCount();
				if (attackStyle == DemonicGorilla.AttackStyle.MAGIC)
				{
					MemorizedPlayer mp = memorizedPlayers.get(target);
					WorldArea lastPlayerArea = mp.getLastWorldArea();
					if (lastPlayerArea != null)
					{
						int dist = gorilla.getNpc().getWorldArea().distanceTo(lastPlayerArea);
						damagesOnTick += (dist + DemonicGorilla.PROJECTILE_MAGIC_DELAY) /
							DemonicGorilla.PROJECTILE_MAGIC_SPEED;
					}
				}
				else if (attackStyle == DemonicGorilla.AttackStyle.RANGED)
				{
					MemorizedPlayer mp = memorizedPlayers.get(target);
					WorldArea lastPlayerArea = mp.getLastWorldArea();
					if (lastPlayerArea != null)
					{
						int dist = gorilla.getNpc().getWorldArea().distanceTo(lastPlayerArea);
						damagesOnTick += (dist + DemonicGorilla.PROJECTILE_RANGED_DELAY) /
							DemonicGorilla.PROJECTILE_RANGED_SPEED;
					}
				}
				pendingAttacks.add(new PendingGorillaAttack(gorilla, attackStyle, target, damagesOnTick));
			}

			gorilla.setNextPosibleAttackStyles(gorilla
				.getNextPosibleAttackStyles()
				.stream()
				.filter(x -> x == attackStyle)
				.collect(Collectors.toList()));

			if (gorilla.getNextPosibleAttackStyles().isEmpty())
			{
				// Sometimes the gorilla can switch attack style before it's supposed to
				// if someone was fighting it earlier and then left, so we just
				// reset the counter in that case.

				gorilla.setNextPosibleAttackStyles(Arrays
					.stream(DemonicGorilla.ALL_REGULAR_ATTACK_STYLES)
					.filter(x -> x == attackStyle)
					.collect(Collectors.toList()));
				gorilla.setAttacksUntilSwitch(DemonicGorilla.ATTACKS_PER_SWITCH -
					(correctPrayer ? 1 : 0));
			}
		}

		checkGorillaAttackStyleSwitch(gorilla, protectedStyle);

		int tickCounter = client.getTickCount();
		gorilla.setNextAttackTick(tickCounter + DemonicGorilla.ATTACK_RATE);
	}

	private void checkGorillaAttacks()
	{
		int tickCounter = client.getTickCount();
		for (DemonicGorilla gorilla : gorillas.values())
		{
			Player interacting = (Player) gorilla.getNpc().getInteracting();
			MemorizedPlayer mp = memorizedPlayers.get(interacting);

			if (gorilla.getLastTickInteracting() != null && interacting == null)
			{
				gorilla.setInitiatedCombat(false);
			}
			else if (mp != null && mp.getLastWorldArea() != null &&
				!gorilla.isInitiatedCombat() &&
				tickCounter < gorilla.getNextAttackTick() &&
				gorilla.getNpc().getWorldArea().isInMeleeDistance(mp.getLastWorldArea()))
			{
				gorilla.setInitiatedCombat(true);
				gorilla.setNextAttackTick(tickCounter + 1);
			}

			int animationId = gorilla.getNpc().getAnimation();

			if (gorilla.isTakenDamageRecently() &&
				tickCounter >= gorilla.getNextAttackTick() + 4)
			{
				// The gorilla was flinched, so its next attack gets delayed
				gorilla.setNextAttackTick(tickCounter + DemonicGorilla.ATTACK_RATE / 2);
				gorilla.setInitiatedCombat(true);

				if (mp != null && mp.getLastWorldArea() != null &&
					!gorilla.getNpc().getWorldArea().isInMeleeDistance(mp.getLastWorldArea()) &&
					!gorilla.getNpc().getWorldArea().intersectsWith(mp.getLastWorldArea()))
				{
					// Gorillas stop meleeing when they get flinched
					// and the target isn't in melee distance
					gorilla.setNextPosibleAttackStyles(gorilla
						.getNextPosibleAttackStyles()
						.stream()
						.filter(x -> x != DemonicGorilla.AttackStyle.MELEE)
						.collect(Collectors.toList()));
					if (interacting != null)
					{
						checkGorillaAttackStyleSwitch(gorilla, DemonicGorilla.AttackStyle.MELEE,
							getProtectedStyle(interacting));
					}
				}
			}
			else if (animationId != gorilla.getLastTickAnimation())
			{
				if (animationId == AnimationID.DEMONIC_GORILLA_PUNCH)
				{
					onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.MELEE);
				}
				else if (animationId == AnimationID.DEMONIC_GORILLA_MAGIC)
				{
					onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.MAGIC);
				}
				else if (animationId == AnimationID.DEMONIC_GORILLA_RANGE)
				{
					onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.RANGED);
				}
				else if (animationId == DEMONIC_GORILLA_AOE_ATTACK && interacting != null &&
					gorilla.getNextPosibleAttackStyles().stream().anyMatch(x -> x == DemonicGorilla.AttackStyle.MAGIC || x == DemonicGorilla.AttackStyle.RANGED))
				{
					// Note that AoE animation is the same as prayer switch animation
					// so we need to check if the prayer was switched or not.
					// It also does this animation when it spawns, so
					// we need the interacting != null check.

					if (gorilla.getOverheadIcon() == gorilla.getLastTickOverheadIcon())
					{
						// Confirmed, the gorilla used the AoE attack
						onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.BOULDER);
					}
					else
					{
						if (tickCounter >= gorilla.getNextAttackTick())
						{
							gorilla.setChangedPrayerThisTick(true);

							// This part is more complicated because the gorilla may have
							// used an attack, but the prayer switch animation takes
							// priority over normal attack animations.

							int projectileId = gorilla.getRecentProjectileId();
							if (projectileId == GraphicIDPlus.DEMONIC_GORILLA_MAGIC)
							{
								onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.MAGIC);
							}
							else if (projectileId == GraphicIDPlus.DEMONIC_GORILLA_RANGED)
							{
								onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.RANGED);
							}
							else if (mp != null)
							{
								WorldArea lastPlayerArea = mp.getLastWorldArea();
								if (lastPlayerArea != null && recentBoulders.stream()
									.anyMatch(x -> x.distanceTo(lastPlayerArea) == 0))
								{
									// A boulder started falling on the gorillas target,
									// so we assume it was the gorilla who shot it
									onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.BOULDER);
								}
								else if (!mp.getRecentHitsplats().isEmpty())
								{
									// It wasn't any of the three other attacks,
									// but the player took damage, so we assume
									// it's a melee attack
									onGorillaAttack(gorilla, DemonicGorilla.AttackStyle.MELEE);
								}
							}
						}

						// The next attack tick is always delayed if the
						// gorilla switched prayer
						gorilla.setNextAttackTick(tickCounter + DemonicGorilla.ATTACK_RATE);
						gorilla.setChangedPrayerThisTick(true);
					}
				}
			}

			if (gorilla.getDisabledMeleeMovementForTicks() > 0)
			{
				gorilla.setDisabledMeleeMovementForTicks(gorilla.getDisabledMeleeMovementForTicks() - 1);
			}
			else if (gorilla.isInitiatedCombat() &&
				gorilla.getNpc().getInteracting() != null &&
				!gorilla.isChangedAttackStyleThisTick() &&
				gorilla.getNextPosibleAttackStyles().size() >= 2 &&
				gorilla.getNextPosibleAttackStyles().stream()
					.anyMatch(x -> x == DemonicGorilla.AttackStyle.MELEE))
			{
				// If melee is a possibility, we can check if the gorilla
				// is or isn't moving toward the player to determine if
				// it is actually attempting to melee or not.
				// We only run this check if the gorilla is in combat
				// because otherwise it attempts to travel to melee
				// distance before attacking its target.

				if (mp != null && mp.getLastWorldArea() != null && gorilla.getLastWorldArea() != null)
				{
					WorldArea predictedNewArea = WorldAreaExtended.calculateNextTravellingPoint(
						client, gorilla.getLastWorldArea(), mp.getLastWorldArea(), true, x ->
						{
							// Gorillas can't normally walk through other gorillas
							// or other players
							final WorldArea area1 = new WorldArea(x, 1, 1);
							return gorillas.values().stream().noneMatch(y ->
							{
								if (y == gorilla)
								{
									return false;
								}
								final WorldArea area2 =
									y.getNpc().getIndex() < gorilla.getNpc().getIndex() ?
										y.getNpc().getWorldArea() : y.getLastWorldArea();
								return area2 != null && area1.intersectsWith(area2);
							}) && memorizedPlayers.values().stream().noneMatch(y ->
							{
								final WorldArea area2 = y.getLastWorldArea();
								return area2 != null && area1.intersectsWith(area2);
							});

							// There is a special case where if a player walked through
							// a gorilla, or a player walked through another player,
							// the tiles that were walked through becomes
							// walkable, but I didn't feel like it's necessary to handle
							// that special case as it should rarely happen.
						});
					if (predictedNewArea != null)
					{
						int distance = gorilla.getNpc().getWorldArea().distanceTo(mp.getLastWorldArea());
						WorldPoint predictedMovement = predictedNewArea.toWorldPoint();
						if (distance <= DemonicGorilla.MAX_ATTACK_RANGE && mp.getLastWorldArea().hasLineOfSightTo(client.getTopLevelWorldView(), gorilla.getLastWorldArea()))
						{
							if (predictedMovement.distanceTo(gorilla.getLastWorldArea().toWorldPoint()) != 0)
							{
								if (predictedMovement.distanceTo(gorilla.getNpc().getWorldLocation()) == 0)
								{
									gorilla.setNextPosibleAttackStyles(gorilla
										.getNextPosibleAttackStyles()
										.stream()
										.filter(x -> x == DemonicGorilla.AttackStyle.MELEE)
										.collect(Collectors.toList()));
								}
								else
								{
									gorilla.setNextPosibleAttackStyles(gorilla
										.getNextPosibleAttackStyles()
										.stream()
										.filter(x -> x != DemonicGorilla.AttackStyle.MELEE)
										.collect(Collectors.toList()));
								}
							}
							else if (tickCounter >= gorilla.getNextAttackTick() &&
								gorilla.getRecentProjectileId() == -1 &&
								recentBoulders.stream().noneMatch(x -> x.distanceTo(mp.getLastWorldArea()) == 0))
							{
								gorilla.setNextPosibleAttackStyles(gorilla
									.getNextPosibleAttackStyles()
									.stream()
									.filter(x -> x == DemonicGorilla.AttackStyle.MELEE)
									.collect(Collectors.toList()));
							}
						}
					}
				}
			}

			if (gorilla.isTakenDamageRecently())
			{
				gorilla.setInitiatedCombat(true);
			}

			if (gorilla.getOverheadIcon() != gorilla.getLastTickOverheadIcon())
			{
				if (gorilla.isChangedAttackStyleLastTick() ||
					gorilla.isChangedAttackStyleThisTick())
				{
					// Apparently if it changes attack style and changes
					// prayer on the same tick or 1 tick apart, it won't
					// be able to move for the next 2 ticks if it attempts
					// to melee
					gorilla.setDisabledMeleeMovementForTicks(2);
				}
				else
				{
					// If it didn't change attack style lately,
					// it's only for the next 1 tick
					gorilla.setDisabledMeleeMovementForTicks(1);
				}
			}
			gorilla.setLastTickAnimation(gorilla.getNpc().getAnimation());
			gorilla.setLastWorldArea(gorilla.getNpc().getWorldArea());
			gorilla.setLastTickInteracting(gorilla.getNpc().getInteracting());
			gorilla.setTakenDamageRecently(false);
			gorilla.setChangedPrayerThisTick(false);
			gorilla.setChangedAttackStyleLastTick(gorilla.isChangedAttackStyleThisTick());
			gorilla.setChangedAttackStyleThisTick(false);
			gorilla.setLastTickOverheadIcon(gorilla.getOverheadIcon());
			gorilla.setRecentProjectileId(-1);
		}
	}

	@Subscribe
	private void onProjectileMoved(ProjectileMoved event)
	{
		if (!atGorillas)
		{
			return;
		}
		
		final Projectile projectile = event.getProjectile();
		final int projectileId = projectile.getId();

		if (!DEMONIC_PROJECTILES.contains(projectileId))
		{
			return;
		}

		if (gorillaProjectiles.contains(projectile))
		{
			return;
		}
		gorillaProjectiles.add(projectile);

		final WorldPoint loc = projectile.getSourcePoint();

		if (projectileId == GraphicIDPlus.DEMONIC_GORILLA_BOULDER)
		{
			recentBoulders.add(loc);
		}
		else
		{
			for (DemonicGorilla gorilla : gorillas.values())
			{
				if (gorilla.getNpc().getWorldLocation().distanceTo(loc) == 0)
				{
					gorilla.setRecentProjectileId(projectile.getId());
				}
			}
		}
	}

	private void checkPendingAttacks()
	{
		Iterator<PendingGorillaAttack> it = pendingAttacks.iterator();
		int tickCounter = client.getTickCount();
		while (it.hasNext())
		{
			PendingGorillaAttack attack = it.next();
			if (tickCounter >= attack.getFinishesOnTick())
			{
				boolean shouldDecreaseCounter = false;
				DemonicGorilla gorilla = attack.getAttacker();
				MemorizedPlayer target = memorizedPlayers.get(attack.getTarget());
				if (target == null)
				{
					// Player went out of memory, so assume the hit was a 0
					shouldDecreaseCounter = true;
				}
				else if (target.getRecentHitsplats().isEmpty())
				{
					// No hitsplats was applied. This may happen in some cases
					// where the player was out of memory while the
					// projectile was travelling. So we assume the hit was a 0.
					shouldDecreaseCounter = true;
				}
				else if (target.getRecentHitsplats().stream()
					.anyMatch(x -> x.getHitsplatType() == HitsplatID.BLOCK_ME))
				{
					// A blue hitsplat appeared, so we assume the gorilla hit a 0
					shouldDecreaseCounter = true;
				}

				if (shouldDecreaseCounter)
				{
					gorilla.setAttacksUntilSwitch(gorilla.getAttacksUntilSwitch() - 1);
					checkGorillaAttackStyleSwitch(gorilla);
				}

				it.remove();
			}
		}
	}

	private void updatePlayers()
	{
		for (MemorizedPlayer mp : memorizedPlayers.values())
		{
			mp.setLastWorldArea(mp.getPlayer().getWorldArea());
			mp.getRecentHitsplats().clear();
		}
	}

	@Subscribe
	private void onHitsplatApplied(HitsplatApplied event)
	{
		if (!atGorillas || gorillas.isEmpty())
		{
			return;
		}

		if (event.getActor() instanceof Player)
		{
			Player player = (Player) event.getActor();
			MemorizedPlayer mp = memorizedPlayers.get(player);
			if (mp != null)
			{
				mp.getRecentHitsplats().add(event.getHitsplat());
			}
		}
		else if (event.getActor() instanceof NPC)
		{
			DemonicGorilla gorilla = gorillas.get((NPC) event.getActor());
			int hitsplatType = event.getHitsplat().getHitsplatType();
			if (gorilla != null && (hitsplatType == HitsplatID.BLOCK_ME ||
				hitsplatType == HitsplatID.DAMAGE_ME))
			{
				gorilla.setTakenDamageRecently(true);
			}
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		final GameState gs = event.getGameState();
		
		switch(gs)
		{
			case LOGGED_IN:
				if (atDemonicGorillas())
				{
					if (!atGorillas)
					{
						init();
					}
				}
				else
				{
					if (atGorillas)
					{
						shutDown();
					}
				}
				break;
			case HOPPING:
			case LOGGING_IN:
			case CONNECTION_LOST:
			case LOGIN_SCREEN:
				if (atGorillas)
				{
					shutDown();
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	private void onPlayerSpawned(PlayerSpawned event)
	{
		if (!atGorillas || gorillas.isEmpty())
		{
			return;
		}

		Player player = event.getPlayer();
		memorizedPlayers.put(player, new MemorizedPlayer(player));
	}

	@Subscribe
	private void onPlayerDespawned(PlayerDespawned event)
	{
		if (!atGorillas || gorillas.isEmpty())
		{
			return;
		}

		memorizedPlayers.remove(event.getPlayer());
	}

	@Subscribe
	private void onNpcSpawned(NpcSpawned event)
	{
		if (!atGorillas)
		{
			return;
		}
		NPC npc = event.getNpc();
		if (isNpcGorilla(npc.getId()))
		{
			if (gorillas.isEmpty())
			{
				// Players are not kept track of when there are no gorillas in
				// memory, so we need to add the players that were already in memory.
				resetPlayers();
			}

			gorillas.put(npc, new DemonicGorilla(npc, client));
		}
	}

	@Subscribe
	private void onNpcDespawned(NpcDespawned event)
	{
		if (!atGorillas)
		{
			return;
		}
		if (gorillas.remove(event.getNpc()) != null && gorillas.isEmpty())
		{
			clear();
		}
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		if (!atGorillas)
		{
			return;
		}
		checkGorillaAttacks();
		checkPendingAttacks();
		updatePlayers();
		recentBoulders.clear();
		clearProjectileArray();
	}

	private void clearProjectileArray()
	{
		gorillaProjectiles.removeIf(p -> p.getRemainingCycles() <= 0);
	}
	
	private boolean atDemonicGorillas()
	{
		return REGION_IDS.contains(MiscUtilities.getPlayerRegionID());
	}
}
