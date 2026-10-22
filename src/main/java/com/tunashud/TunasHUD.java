
package com.tunashud;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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

    private final Map<UUID, BossBar> compassBars = new HashMap<>();
    private final Map<UUID, String> lastTitles = new HashMap<>();
    private final Map<UUID, Boolean> enabledSettings = new HashMap<>();

    // Remember Tab names so they can be restored.
    private final Map<UUID, String> originalTabNames = new HashMap<>();
    private final Map<UUID, String> lastBiomeNames = new HashMap<>();
    private final Map<UUID, String> hudTabNames = new HashMap<>();

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

            // Update Tab-list biomes every two seconds.
            if (tickCounter % 40 == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateTabBiome(player);
                }
            }

        }, 0L, 1L);

        // Support players already online during a plugin reload.
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTabBiome(player);
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
        }

        originalTabNames.clear();
        lastBiomeNames.clear();
        hudTabNames.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updateTabBiome(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        removeCompass(id);
        enabledSettings.remove(id);

        restoreTabName(player);

        originalTabNames.remove(id);
        lastBiomeNames.remove(id);
        hudTabNames.remove(id);
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

        String newTitle = buildCompass(player.getLocation().getYaw());

        if (!newTitle.equals(lastTitles.get(id))) {
            bar.setTitle(newTitle);
            lastTitles.put(id, newTitle);
        }
    }

    private void updateTabBiome(Player player) {
        UUID id = player.getUniqueId();

        // Save the player's existing Tab name before modifying it.
        originalTabNames.putIfAbsent(id, player.getPlayerListName());

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

        // Only restore if our plugin still owns the displayed name.
        if (original != null
                && applied != null
                && applied.equals(player.getPlayerListName())) {

            player.setPlayerListName(original);
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

    // Compass rendering preserved from v1.0.11.
    private String buildCompass(float yaw) {

        double heading = ((yaw + 180.0) % 360.0 + 360.0) % 360.0;

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
                        (double) dot * availableSpace / (DOT_COUNT + 1)
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

        String activeLabel = DIRECTIONS[activeIndex];
        int activeCell = 8 + activeIndex;

        if (activeIndex == 0 && heading > 180.0) {
            activeCell += 8;
        }

        int activePosition = activeCell * CELL_WIDTH - start;

        if (activePosition >= 0
                && activePosition + activeLabel.length()
                <= visibleTape.length()) {

            return "§f"
                    + visibleTape.substring(0, activePosition)
                    + "§e"
                    + activeLabel
                    + "§f"
                    + visibleTape.substring(
                    activePosition + activeLabel.length()
            );
        }

        return "§f" + visibleTape;
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
        player.sendMessage("§7Hold Tab to view player biomes.");
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
