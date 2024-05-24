/*
 * Copyright (c) 2019, Ganom <https://github.com/Ganom>
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

package com.theplug.kotori.fightcaves;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.theplug.kotori.kotoriutils.overlay.OverlayUtility;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Prayer;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

@Singleton
public class FightCaveOverlay extends Overlay
{
	private final FightCavePlugin plugin;
	private final FightCaveConfig config;
	private final Client client;
	private final SpriteManager spriteManager;

	@Inject
	FightCaveOverlay(final Client client, final FightCavePlugin plugin, final FightCaveConfig config, final SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_HIGHEST);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		for (FightCaveContainer npc : plugin.getFightCaveContainer())
		{
			if (npc.getNpc() == null)
			{
				continue;
			}

			final int ticksLeft = npc.getTicksUntilAttack();
			final FightCaveContainer.AttackStyle attackStyle = npc.getAttackStyle();

			if (ticksLeft <= 0)
			{
				continue;
			}

			final String ticksLeftStr = String.valueOf(ticksLeft);
			final int font = config.fontStyle().getFont();
			final boolean shadows = config.shadows();
			Color color = (ticksLeft <= 1 ? Color.WHITE : attackStyle.getColor());
			final Point canvasPoint = npc.getNpc().getCanvasTextLocation(graphics, Integer.toString(ticksLeft), 0);

			if (npc.getNpcName().equals("TzTok-Jad"))
			{
				color = (ticksLeft <= 1 || ticksLeft == 8 ? attackStyle.getColor() : Color.WHITE);

				BufferedImage pray = getPrayerImage(npc.getAttackStyle());

				if (pray == null)
				{
					continue;
				}

				renderImageLocation(graphics, npc.getNpc().getCanvasImageLocation(ImageUtil.resizeImage(pray, 36, 36), 0), pray, 12, 30);
			}

			OverlayUtility.renderTextLocation(graphics, ticksLeftStr, config.textSize(), font, color, canvasPoint, shadows, 0);
		}

		if (config.tickTimersWidget())
		{

			if (!plugin.getMageTicks().isEmpty())
			{
				widgetHandler(graphics,
					Prayer.PROTECT_FROM_MAGIC,
					plugin.getMageTicks().get(0) == 1 ? Color.WHITE : Color.CYAN,
					Integer.toString(plugin.getMageTicks().get(0)),
					config.shadows()
				);
			}
			if (!plugin.getRangedTicks().isEmpty())
			{
				widgetHandler(graphics,
					Prayer.PROTECT_FROM_MISSILES,
					plugin.getRangedTicks().get(0) == 1 ? Color.WHITE : Color.GREEN,
					Integer.toString(plugin.getRangedTicks().get(0)),
					config.shadows()
				);
			}
			if (!plugin.getMeleeTicks().isEmpty())
			{
				widgetHandler(graphics,
					Prayer.PROTECT_FROM_MELEE,
					plugin.getMeleeTicks().get(0) == 1 ? Color.WHITE : Color.RED,
					Integer.toString(plugin.getMeleeTicks().get(0)),
					config.shadows()
				);
			}
		}
		return null;
	}

	private void widgetHandler(Graphics2D graphics, Prayer prayer, Color color, String ticks, boolean shadows)
	{
		if (prayer != null)
		{
			Rectangle bounds = OverlayUtility.renderPrayerOverlay(graphics, client, prayer, color);

			if (bounds != null)
			{
				OverlayUtility.renderTextLocation(graphics, ticks, 16, config.fontStyle().getFont(), color, centerPoint(bounds), shadows, 0);
			}
		}
	}

	private BufferedImage getPrayerImage(FightCaveContainer.AttackStyle attackStyle)
	{
		switch (attackStyle)
		{
			case MAGE:
				return spriteManager.getSprite(SpriteID.PRAYER_PROTECT_FROM_MAGIC, 0);
			case MELEE:
				return spriteManager.getSprite(SpriteID.PRAYER_PROTECT_FROM_MELEE, 0);
			case RANGE:
				return spriteManager.getSprite(SpriteID.PRAYER_PROTECT_FROM_MISSILES, 0);
		}
		return null;
	}

	private void renderImageLocation(Graphics2D graphics, Point imgLoc, BufferedImage image, int xOffset, int yOffset)
	{
		int x = imgLoc.getX() + xOffset;
		int y = imgLoc.getY() - yOffset;

		graphics.drawImage(image, x, y, null);
	}

	private Point centerPoint(Rectangle rect)
	{
		int x = (int) (rect.getX() + rect.getWidth() / 2);
		int y = (int) (rect.getY() + rect.getHeight() / 2);
		return new Point(x, y);
	}
}
