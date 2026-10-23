
package com.tunashud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TunasHUD extends JavaPlugin
        implements Listener, CommandExecutor, TabCompleter {

    private static final String[] DIRECTIONS = {
            "N", "NE", "E", "SE", "S", "SW", "W", "NW"
    };

    private static final int DOT_COUNT = 5;
    private static final int CELL_WIDTH = 24;
    private static final int TOTAL_CELLS = 32;
    private static final int MAX_PIXEL_WIDTH = 172;

    private static final char HOME_MARKER = '⌂';

    private final Map<UUID, BossBar> compassBars = new HashMap<>();
    private final Map<UUID, String> lastTitles = new HashMap<>();
    private final Map<UUID, Boolean> enabledSettings = new HashMap<>();

    private final Map<UUID, String> originalTabNames = new HashMap<>();
    private final Map<UUID, String> lastBiomeNames = new HashMap<>();
    private final Map<UUID, String> hudTabNames = new HashMap<>();

    private final Map<UUID, Component> originalTabHeaders = new HashMap<>();
    private final Map<UUID, Component> originalTabFooters = new HashMap<>();

    private long tickCounter = 0;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("hud")).setExecutor(this);
        Objects.requireNonNull(getCommand("hud")).setTabCompleter(this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tickCounter++;

            for (Player player : Bukkit.getOnlinePlayers()) {
                updateCompass(player);
            }

            if (tickCounter % 40 == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateTabBiome(player);
                }
            }

            if (tickCounter % 20 == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateTabClock(player);
                }
            }
        }, 0L, 1L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTabBiome(player);
            updateTabClock(player);
        }

        getLogger().info("TunasHUD " +
                getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        for (BossBar bar : compassBars.values()) {
            bar.removeAll();
        }

        compassBars.clear();
        lastTitles.clear();
        enabledSettings.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreTabName(player);
            restoreTabHeader(player);
        }

        originalTabNames.clear();
        lastBiomeNames.clear();
        hudTabNames.clear();
        originalTabHeaders.clear();
        originalTabFooters.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        updateTabBiome(player);
        updateTabClock(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        removeCompass(id);
        enabledSettings.remove(id);

        restoreTabName(player);
        restoreTabHeader(player);

        originalTabNames.remove(id);
        lastBiomeNames.remove(id);
        hudTabNames.remove(id);
        originalTabHeaders.remove(id);
        originalTabFooters.remove(id);
    }

    private void removeCompass(UUID id) {
        BossBar bar = compassBars.remove(id);

        if (bar != null) {
            bar.removeAll();
        }

        lastTitles.remove(id);
    }

    private boolean isEnabled(Player player) {
        return enabledSettings.getOrDefault(
                player.getUniqueId(), true
        );
    }

    private void updateCompass(Player player) {
        UUID id = player.getUniqueId();

        if (!isEnabled(player)) {
            removeCompass(id);
            return;
        }

        BossBar bar = compassBars.computeIfAbsent(id, ignored -> {
            BossBar newBar = Bukkit.createBossBar(
                    "TunasHUD",
                    BarColor.WHITE,
                    BarStyle.SOLID
            );

            newBar.addPlayer(player);
            newBar.setProgress(1.0);

            return newBar;
        });

        String newTitle = buildCompass(player);

        if (!newTitle.equals(lastTitles.get(id))) {
            bar.setTitle(newTitle);
            lastTitles.put(id, newTitle);
        }
    }

    private void updateTabBiome(Player player) {
        UUID id = player.getUniqueId();

        originalTabNames.putIfAbsent(
                id, player.getPlayerListName()
        );

        Biome biome = player.getLocation().getBlock().getBiome();
        NamespacedKey key = Registry.BIOME.getKey(biome);

        String biomeId = key != null
                ? key.getKey()
                : biome.toString();

        String biomeName = formatBiomeName(biomeId);

        if (biomeName.equals(lastBiomeNames.get(id))) {
            return;
        }

        String originalName = originalTabNames.get(id);

        String newName = originalName
                + " §8- §a"
                + biomeName;

        player.setPlayerListName(newName);

        lastBiomeNames.put(id, biomeName);
        hudTabNames.put(id, newName);
    }

    private void restoreTabName(Player player) {
        UUID id = player.getUniqueId();

        String original = originalTabNames.get(id);
        String applied = hudTabNames.get(id);

        if (original != null
                && applied != null
                && applied.equals(player.getPlayerListName())) {

            player.setPlayerListName(original);
        }
    }

    private void updateTabClock(Player player) {
        UUID id = player.getUniqueId();

        originalTabHeaders.putIfAbsent(
                id, player.playerListHeader()
        );

        originalTabFooters.putIfAbsent(
                id, player.playerListFooter()
        );

        long time = player.getWorld().getTime();

        long totalMinutes =
                ((time + 6000L) % 24000L) * 1440L / 24000L;

        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        String formattedTime = String.format(
                Locale.ROOT,
                "%02d:%02d",
                hours,
                minutes
        );

        boolean isDay = time < 12000L;

        String symbol = isDay ? "☀" : "☾";

        NamedTextColor symbolColor = isDay
                ? NamedTextColor.YELLOW
                : NamedTextColor.AQUA;

        Component header = Component.text("TunasHUD")
                .color(NamedTextColor.GOLD)
                .append(Component.newline())
                .append(Component.text(symbol + " ", symbolColor))
                .append(Component.text(
                        formattedTime,
                        NamedTextColor.WHITE
                ));

        Component originalFooter = originalTabFooters.get(id);

        player.sendPlayerListHeaderAndFooter(
                header,
                originalFooter != null
                        ? originalFooter
                        : Component.empty()
        );
    }

    private void restoreTabHeader(Player player) {
        UUID id = player.getUniqueId();

        Component originalHeader = originalTabHeaders.get(id);
        Component originalFooter = originalTabFooters.get(id);

        if (originalHeader != null || originalFooter != null) {
            player.sendPlayerListHeaderAndFooter(
                    originalHeader != null
                            ? originalHeader
                            : Component.empty(),
                    originalFooter != null
                            ? originalFooter
                            : Component.empty()
            );
        }
    }

    private String formatBiomeName(String biomeId) {
        String[] words = biomeId.replace('_', ' ').split(" ");

        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(Character.toUpperCase(word.charAt(0)));

            if (word.length() > 1) {
                result.append(
                        word.substring(1).toLowerCase(Locale.ROOT)
                );
            }
        }

        return result.toString();
    }

    // Minecraft yaw: south=0, west=90, north=180, east=270.
    private double normalizeHeading(double yaw) {
        return ((yaw + 180.0) % 360.0 + 360.0) % 360.0;
    }

    // Calculate the compass bearing toward the player's bed.
    // Returns null if there is no bed spawn or it is in another world.
    private Double getBedBearing(Player player) {
        Location bed = player.getBedSpawnLocation();

        if (bed == null) {
            return null;
        }

        Location current = player.getLocation();

        if (!current.getWorld().equals(bed.getWorld())) {
            return null;
        }

        double dx = bed.getX() - current.getX();
        double dz = bed.getZ() - current.getZ();

        // No useful horizontal direction when already at the bed.
        if (dx * dx + dz * dz < 0.01) {
            return null;
        }

        double yawToBed = Math.toDegrees(Math.atan2(-dx, dz));

        return normalizeHeading(yawToBed);
    }

    // Add the house by replacing an existing dot.
    // This never inserts or removes compass characters.
    private int findHomeDot(
            String visibleTape,
            int start,
            double heading,
            double bedBearing
    ) {
        double centerPosition =
                8.0 * CELL_WIDTH
                        + (heading / 45.0) * CELL_WIDTH;

        double targetPosition =
                8.0 * CELL_WIDTH
                        + (bedBearing / 45.0) * CELL_WIDTH;

        double revolutionWidth = DIRECTIONS.length * CELL_WIDTH;

        // Choose the copy of the compass bearing closest
        // to the current viewing direction.
        while (targetPosition - centerPosition > revolutionWidth / 2.0) {
            targetPosition -= revolutionWidth;
        }

        while (centerPosition - targetPosition > revolutionWidth / 2.0) {
            targetPosition += revolutionWidth;
        }

        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < visibleTape.length(); i++) {
            if (visibleTape.charAt(i) != '·') {
                continue;
            }

            double absolutePosition = start + i;
            double distance = Math.abs(
                    absolutePosition - targetPosition
            );

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }

        // The target must actually be within the displayed tape.
        // Allow a small offset to reach the nearest dot.
        if (nearestDistance > 4.0) {
            return -1;
        }

        return nearestIndex;
    }

    // Original compass geometry preserved, with a bed marker added.
    private String buildCompass(Player player) {
        float yaw = player.getLocation().getYaw();

        double heading = normalizeHeading(yaw);

        int activeIndex = (int) Math.floor(
                (heading + 22.5) / 45.0
        ) % DIRECTIONS.length;

        StringBuilder tape = new StringBuilder();

        for (int i = 0; i < TOTAL_CELLS; i++) {
            String label = DIRECTIONS[i % DIRECTIONS.length];
            tape.append(label);

            int availableSpace = CELL_WIDTH - label.length();

            char[] spacing = new char[availableSpace];
            Arrays.fill(spacing, ' ');

            for (int dot = 1; dot <= DOT_COUNT; dot++) {
                int position = (int) Math.round(
                        (double) dot * availableSpace
                                / (DOT_COUNT + 1)
                );

                if (position >= 0 && position < availableSpace) {
                    spacing[position] = '·';
                }
            }

            tape.append(spacing);
        }

        double position = (heading / 45.0) * CELL_WIDTH;
        int scrollOffset = (int) Math.round(position);

        int centerPosition = (8 * CELL_WIDTH) + scrollOffset;

        int start = centerPosition - 30;
        int end = centerPosition + 31;

        String visibleTape = tape.substring(start, end);

        while (estimateWidth(visibleTape) > MAX_PIXEL_WIDTH
                && visibleTape.length() > 1) {

            visibleTape = visibleTape.substring(
                    1, visibleTape.length() - 1
            );

            start++;
        }

        // New in v1.1.3: replace the closest visible dot
        // with a green marker pointing toward the bed.
        int homeDotIndex = -1;
        Double bedBearing = getBedBearing(player);

        if (bedBearing != null) {
            homeDotIndex = findHomeDot(
                    visibleTape,
                    start,
                    heading,
                    bedBearing
            );
        }

        String activeLabel = DIRECTIONS[activeIndex];
        int activeCell = 8 + activeIndex;

        if (activeIndex == 0 && heading > 180.0) {
            activeCell += 8;
        }

        int activePosition = activeCell * CELL_WIDTH - start;

        // Build color formatting without changing tape positions.
        StringBuilder result = new StringBuilder("§f");

        for (int i = 0; i < visibleTape.length(); i++) {

            if (i == homeDotIndex) {
                result.append("§a");
                result.append(HOME_MARKER);
                result.append("§f");
                continue;
            }

            if (i == activePosition
                    && activePosition >= 0
                    && activePosition + activeLabel.length()
                    <= visibleTape.length()) {

                result.append("§e");
                result.append(activeLabel);
                result.append("§f");

                i += activeLabel.length() - 1;
                continue;
            }

            result.append(visibleTape.charAt(i));
        }

        return result.toString();
    }

    private int estimateWidth(String text) {
        int width = 0;

        for (char c : text.toCharArray()) {
            width += switch (c) {
                case ' ' -> 4;
                case '·' -> 3;
                case 'I', 'i', 'l', '.', ',' -> 2;
                case 'N', 'E', 'S', 'W' -> 6;
                default -> 6;
            };
        }

        return width;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    "This command can only be used in-game."
            );
            return true;
        }

        if (args.length == 0
                || args[0].equalsIgnoreCase("help")) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                enabledSettings.put(player.getUniqueId(), true);
                updateCompass(player);
                player.sendMessage("§aTunasHUD compass enabled.");
            }

            case "off" -> {
                enabledSettings.put(player.getUniqueId(), false);
                removeCompass(player.getUniqueId());
                player.sendMessage("§eTunasHUD compass hidden.");
            }

            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6----- TunasHUD Commands -----");
        player.sendMessage("§e/hud on §7- Show compass");
        player.sendMessage("§e/hud off §7- Hide compass");
        player.sendMessage("§e/hud help §7- Show this help");
        player.sendMessage("§7Hold Tab to view player biomes and time.");
        player.sendMessage("§7Green house marker points toward your bed spawn.");
        player.sendMessage("§7Compass settings reset on disconnect.");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);

            return List.of("on", "off", "help")
                    .stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
