/*
 * Copyright (c) 2020 dutta64 <https://github.com/dutta64>
 * Copyright (c) 2019 Im2be <https://github.com/Im2be>
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

package com.theplug.kotori.cerberushelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.theplug.kotori.cerberushelper.CerberusHelperConfig;
import com.theplug.kotori.cerberushelper.CerberusHelperPlugin;
import com.theplug.kotori.cerberushelper.domain.Cerberus;
import com.theplug.kotori.cerberushelper.domain.Phase;
import com.theplug.kotori.cerberushelper.util.ImageManager;
import com.theplug.kotori.cerberushelper.util.InfoBoxComponent;
import net.runelite.api.Prayer;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.PanelComponent;

@Singleton
public final class UpcomingAttackOverlay extends Overlay
{
	private static final Color COLOR_DEFAULT = new Color(70, 61, 50, 225);
	private static final Color COLOR_GHOSTS = new Color(255, 255, 255, 225);
	private static final Color COLOR_TRIPLE_ATTACK = new Color(0, 15, 255, 225);
	private static final Color COLOR_LAVA = new Color(82, 0, 0, 225);
	private static final Color COLOR_NEXT_ATTACK_BORDER = Color.WHITE;

	private static final PanelComponent PANEL_COMPONENT = new PanelComponent();

	private static final int GAP_SIZE = 2;

	private final CerberusHelperPlugin plugin;
	private final CerberusHelperConfig config;

	@Inject
	public UpcomingAttackOverlay(final CerberusHelperPlugin plugin, final CerberusHelperConfig config)
	{
		this.plugin = plugin;
		this.config = config;

		// Clear background and border
		PANEL_COMPONENT.setBackgroundColor(null);
		PANEL_COMPONENT.setBorder(new Rectangle(0, 0, 0, 0));

		setPosition(OverlayPosition.BOTTOM_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(final Graphics2D graphics2D)
	{
		final Cerberus cerberus = plugin.getCerberus();

		if (!config.showUpcomingAttacks() || cerberus == null)
		{
			return null;
		}

		// Remove upcoming attack infobox children
		PANEL_COMPONENT.getChildren().clear();

		// Set size from config
		final int size = 40;
		final Dimension dimension = new Dimension(size, size);
		PANEL_COMPONENT.setPreferredSize(dimension);

		// Set orientation from config
		PANEL_COMPONENT.setOrientation(ComponentOrientation.VERTICAL);

		// Set gap between infobox children
		final Point gap = new Point(0, GAP_SIZE);
		PANEL_COMPONENT.setGap(gap);

		final int attacksShown = config.amountOfAttacksShown();

		for (int i = 0; i < attacksShown; ++i)
		{
			final int attack;

			attack = attacksShown - i;

			if (attack == 1)
			{
				renderOutlineBorder(graphics2D, size, gap, attacksShown);
			}

			// Get the image for the infobox
			final int cerberusHp = cerberus.getHp();
			final Phase phase = cerberus.getNextAttackPhase(attack, cerberusHp);

			Prayer prayer = plugin.getDefaultPrayer();

			if (config.killingEchoCerberus())
			{
				int attacksInRotation = cerberus.getNonGhostAttacks() % 24;
				if (attacksInRotation < 8)
				{
					prayer = Prayer.PROTECT_FROM_MAGIC;
				}
				else if (attacksInRotation < 16)
				{
					prayer = Prayer.PROTECT_FROM_MISSILES;
				}
				else
				{
					prayer = Prayer.PROTECT_FROM_MELEE;
				}
			}

			final BufferedImage image = ImageManager.getCerberusBufferedImage(phase, prayer);

			if (image == null)
			{
				continue;
			}

			// Create infobox
			final InfoBoxComponent infoBoxComponent = new InfoBoxComponent();
			infoBoxComponent.setFont(FontManager.getRunescapeSmallFont());
			infoBoxComponent.setTextColor(Color.GREEN);
			infoBoxComponent.setBackgroundColor(getColorFromPhase(phase));
			infoBoxComponent.setPreferredSize(dimension);
			infoBoxComponent.setImage(image);

			// Set text
			final Phase nextThresholdPhase = cerberus.getNextAttackPhase(attack, cerberusHp - 200);

			if (!nextThresholdPhase.equals(phase))
			{
				final String text = nextThresholdPhase.name().substring(0, 1);

				infoBoxComponent.setText(String.format("%s +%d", text, cerberusHp % 200));
			}

			// Set title
			infoBoxComponent.setTitle(String.valueOf(cerberus.getPhaseCount() + attack));

			// Add infobox to panel
			PANEL_COMPONENT.getChildren().add(infoBoxComponent);
		}

		return PANEL_COMPONENT.render(graphics2D);
	}

	private void renderOutlineBorder(final Graphics2D graphics2D, final int size, final Point gap, final int numberOfAttacks)
	{
		int x = -1;
		int y = -1;

		y += (int) ((size + gap.getY()) * (numberOfAttacks - 1));

		final Rectangle rectangle = new Rectangle();

		rectangle.setLocation(x, y);
		rectangle.setSize(size + 1, size + 1);

		graphics2D.setColor(COLOR_NEXT_ATTACK_BORDER);
		graphics2D.draw(rectangle);
	}

	public static Color getColorFromPhase(final Phase phase)
	{
		final Color color;

		switch (phase)
		{
			case TRIPLE:
				color = COLOR_TRIPLE_ATTACK;
				break;
			case LAVA:
				color = COLOR_LAVA;
				break;
			case GHOSTS:
				color = COLOR_GHOSTS;
				break;
			case AUTO:
			default:
				color = COLOR_DEFAULT;
				break;
		}

		return color;
	}
}
