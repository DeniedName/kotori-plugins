package com.theplug.kotori.sirehelper;

import com.theplug.kotori.kotoriutils.overlay.OverlayUtility;
import com.theplug.kotori.sirehelper.entity.AbyssalSire;
import com.theplug.kotori.sirehelper.entity.MiasmaPools;
import com.theplug.kotori.sirehelper.entity.RespiratorySystem;
import com.theplug.kotori.sirehelper.entity.Spawn;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.Map;
import java.util.Set;

@Singleton
public class SireHelperSceneOverlay extends Overlay
{
    public static final Set<WorldPoint> RESP_SAFESPOTS = Set.of(
            new WorldPoint(2973, 4780, 0), new WorldPoint(2967, 4770, 0),
            new WorldPoint(2983, 4844, 0), new WorldPoint(2977, 4834, 0),
            new WorldPoint(3113, 4780, 0), new WorldPoint(3107, 4770, 0),
            new WorldPoint(3108, 4844, 0), new WorldPoint(3102, 4834, 0)
    );

    public static final Set<WorldPoint> WHERE_TO_STAND_TILES = Set.of(
            //Phase 2
            new WorldPoint(2969, 4780, 0), new WorldPoint(2971, 4780, 0),
            new WorldPoint(2979, 4844, 0), new WorldPoint(2981, 4844, 0),
            new WorldPoint(3109, 4780, 0), new WorldPoint(3111, 4780, 0),
            new WorldPoint(3104, 4844, 0), new WorldPoint(3106, 4844, 0),
            //Phase 3
            new WorldPoint(2969, 4772, 0), new WorldPoint(2971, 4772, 0),
            new WorldPoint(2979, 4836, 0), new WorldPoint(2981, 4836, 0),
            new WorldPoint(3109, 4772, 0), new WorldPoint(3111, 4772, 0),
            new WorldPoint(3104, 4836, 0), new WorldPoint(3106, 4836, 0),
            //Phase 4
            new WorldPoint(2969, 4770, 0), new WorldPoint(2971, 4770, 0),
            new WorldPoint(2979, 4834, 0), new WorldPoint(2981, 4834, 0),
            new WorldPoint(3109, 4770, 0), new WorldPoint(3111, 4770, 0),
            new WorldPoint(3104, 4834, 0), new WorldPoint(3106, 4834, 0)
    );



    private final Client client;
    private final SireHelperPlugin plugin;
    private final SireHelperConfig config;

    @Inject
    private SireHelperSceneOverlay(final Client client, final SireHelperPlugin plugin, final SireHelperConfig config)
    {
        super(plugin);
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isAtSire())
        {
            return null;
        }

        renderGroundMarkers(graphics);
        renderRespiratoryInfo(graphics);
        renderAbyssalSireInfo(graphics);
        renderSpawnInfo(graphics);
        renderMiasmaPools(graphics);

        return null;
    }

    private void renderMiasmaPools(Graphics2D graphics2D)
    {
        if (!config.highlightMiasmaPools() && !config.showMiasmaTimer())
        {
            return;
        }

        Map<LocalPoint, MiasmaPools> miasmaPoolsMap = plugin.getMiasmaPoolsMap();

        for (Map.Entry<LocalPoint, MiasmaPools> miasmaEntry : miasmaPoolsMap.entrySet())
        {
            LocalPoint localPoint = miasmaEntry.getKey();

            WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);
            if (worldPoint.getPlane() != client.getTopLevelWorldView().getPlane())
            {
                continue;
            }

            final String countdown = String.valueOf(miasmaEntry.getValue().getTicksUntilDespawn());

            if (config.highlightMiasmaPools())
            {
                OverlayUtility.drawPolygon(graphics2D, client, localPoint, 3, config.miasmaBorderColor(), config.miasmaFillColor(), 2);
            }
            if (config.showMiasmaTimer())
            {
                OverlayUtility.renderTextLocation(graphics2D, client, localPoint, countdown, config.miasmaTimerSize(),
                        Font.BOLD, config.miasmaTimerColor(), true, 0);
            }
        }
    }

    private void renderRespiratoryInfo(Graphics2D graphics2D)
    {
        AbyssalSire sire = plugin.getAbyssalSire();

        if ((!config.showStunTimerOnRespirators() && !config.showTotalDamageDealtToRespirators()) || sire == null || plugin.getRespiratorsMap().isEmpty())
        {
            return;
        }

        for (Map.Entry<WorldPoint, RespiratorySystem> respEntry : plugin.getRespiratorsMap().entrySet())
        {
            LocalPoint localPoint = LocalPoint.fromWorld(client.getTopLevelWorldView(), respEntry.getKey());
            String infoText = "";

            if (config.showStunTimerOnRespirators())
            {
                infoText += sire.getStunTimer();
            }

            if (config.showTotalDamageDealtToRespirators())
            {
                RespiratorySystem system = respEntry.getValue();
                if (infoText.isEmpty())
                {
                    infoText += system.getDamageDealt();
                }
                else
                {
                    infoText += " : " + system.getDamageDealt();
                }
            }

            if (!infoText.isEmpty())
            {
                OverlayUtility.renderTextLocation(graphics2D, client, localPoint, infoText, config.respiratorTextSize(),
                        Font.BOLD, config.respiratorTextColor(), true, 0);
            }
        }
    }

    private void renderGroundMarkers(Graphics2D graphics2D)
    {
        if (config.showWhereToStandTiles())
        {
            renderTileSet(graphics2D, WHERE_TO_STAND_TILES, config.whereToStandBorderColor(), config.whereToStandFillColor());
        }

        if (config.showRespiratorySafeSpots())
        {
            renderTileSet(graphics2D, RESP_SAFESPOTS, config.safeSpotBorderColor(), config.safeSpotFillColor());
        }
    }

    private void renderAbyssalSireInfo(Graphics2D graphics2D)
    {
        final AbyssalSire sire = plugin.getAbyssalSire();

        if (!config.showHpUntilPhaseChange() || sire == null)
        {
            return;
        }

        final int hpUntilPhaseChange = sire.getHpUntilPhaseChange();

        if (hpUntilPhaseChange == 0)
        {
            return;
        }

        final String hpText = String.valueOf(hpUntilPhaseChange);

        LocalPoint localPoint = sire.getNpc().getLocalLocation();

        OverlayUtility.renderTextLocation(graphics2D, client, localPoint, hpText, config.phaseChangeTextSize(),
                Font.BOLD, config.phaseChangeTextColor(), true, 0);
    }

    private void renderSpawnInfo(Graphics2D graphics2D)
    {
        if (!config.showSpawnEvolutionTimer() || plugin.getSpawnSet().isEmpty())
        {
            return;
        }

        for (Spawn spawn : plugin.getSpawnSet())
        {
            final int timer = spawn.getEvolutionTimer();

            if (timer == 0)
            {
                return;
            }

            final String timerText = String.valueOf(timer);
            LocalPoint localPoint = spawn.getNpc().getLocalLocation();

            OverlayUtility.renderTextLocation(graphics2D, client, localPoint, timerText, config.evolutionTextSize(),
                    Font.BOLD, config.evolutionTextColor(), true, 0);
        }
    }

    private void renderTileSet(Graphics2D graphics2D, Set<WorldPoint> pointSet, Color borderColor, Color fillColor)
    {
        for (WorldPoint worldPoint : pointSet)
        {
            if (worldPoint.getPlane() != client.getTopLevelWorldView().getPlane() && !WorldPoint.isInScene(client.getTopLevelWorldView(), worldPoint.getX(), worldPoint.getY()))
            {
                continue;
            }

            OverlayUtility.drawTiles(graphics2D, client, worldPoint, borderColor, fillColor, 2);
        }
    }
}