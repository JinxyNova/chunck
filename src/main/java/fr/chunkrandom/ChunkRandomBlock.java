package fr.chunkrandom;

import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ChunkRandomBlock extends JavaPlugin implements Listener {

    /** Blocs "spéciaux" (inventaires, portes, lits...) : jamais remplacés, jamais choisis. */
    private static final String[] SPECIAL = {
            "DOOR", "BED", "SIGN", "BANNER", "HEAD", "SKULL", "COMMAND_BLOCK", "BARRIER",
            "STRUCTURE", "JIGSAW", "SPAWNER", "CHEST", "SHULKER_BOX", "FURNACE", "SMOKER",
            "HOPPER", "DISPENSER", "DROPPER", "CRAFTER", "LECTERN", "BREWING", "CHISELED_BOOKSHELF",
            "DECORATED_POT", "BEACON", "CONDUIT", "ENCHANTING", "PORTAL", "END_GATEWAY", "VAULT",
            "SCULK_SENSOR", "SCULK_SHRIEKER", "SCULK_CATALYST", "JUKEBOX", "BELL", "CAMPFIRE",
            "PISTON", "TEST_", "STATUE", "BARREL", "FLOWER_POT"
    };

    private NamespacedKey keyMaterial;
    private NamespacedKey keyDone;
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final Set<String> active = new HashSet<>();
    private final List<Material> pool = new ArrayList<>();
    private final Set<String> worlds = new HashSet<>();
    private int checksPerTick;
    private int changesPerTick;
    private boolean announce;
    private int freezeTicks;
    /** Chunks fraîchement convertis : les blocs à gravité n'y tombent pas pendant un court instant. */
    private final Map<String, Integer> frozen = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyMaterial = new NamespacedKey(this, "chunk_block");
        keyDone = new NamespacedKey(this, "chunk_done");

        worlds.addAll(getConfig().getStringList("worlds"));
        checksPerTick = Math.max(1000, getConfig().getInt("checks-per-tick", 30000));
        changesPerTick = Math.max(100, getConfig().getInt("changes-per-tick", 3000));
        announce = getConfig().getBoolean("announce", true);
        freezeTicks = Math.max(20, getConfig().getInt("freeze-ticks", 100));

        Set<String> blacklist = new HashSet<>();
        for (String s : getConfig().getStringList("blacklist")) {
            blacklist.add(s.toUpperCase(Locale.ROOT));
        }
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock() || m.isAir() || !m.isSolid()) continue;
            if (isSpecial(m) || blacklist.contains(m.name())) continue;
            pool.add(m);
        }
        if (pool.isEmpty()) {
            getLogger().severe("Aucun bloc disponible, plugin désactivé.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info(pool.size() + " blocs possibles.");

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        for (Player p : getServer().getOnlinePlayers()) {
            handle(p, p.getLocation().getChunk());
        }
    }

    @Override
    public void onDisable() {
        for (Job j : jobs) {
            j.chunk.removePluginChunkTicket(this);
        }
        jobs.clear();
        active.clear();
    }

    private static boolean isSpecial(Material m) {
        String n = m.name();
        if (n.endsWith("_SHELF")) return true;
        for (String s : SPECIAL) {
            if (n.contains(s)) return true;
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        handle(p, p.getLocation().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        var from = e.getFrom();
        var to = e.getTo();
        if (to == null) return;
        if (from.getWorld() == to.getWorld()
                && (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }
        handle(e.getPlayer(), to.getChunk());
    }

    /**
     * Empêche le sable, gravier, etc. de tomber pendant et juste après la conversion du chunk.
     * Ensuite ils restent en place jusqu'à ce qu'on casse / pose un bloc à côté (mise à jour normale du jeu).
     */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock) || !e.getTo().isAir()) return;
        if (active.isEmpty() && frozen.isEmpty()) return;

        Block b = e.getBlock();
        String id = b.getWorld().getUID() + ":" + Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4);
        if (active.contains(id)) {
            e.setCancelled(true);
            return;
        }
        Integer until = frozen.get(id);
        if (until != null && getServer().getCurrentTick() < until) {
            e.setCancelled(true);
        }
    }

    private void handle(Player player, Chunk chunk) {
        if (!worlds.isEmpty() && !worlds.contains(chunk.getWorld().getName())) return;

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (pdc.has(keyDone, PersistentDataType.BYTE)) return;

        String id = chunk.getWorld().getUID() + ":" + chunk.getChunkKey();
        if (!active.add(id)) return;

        // Reprend le bloc déjà choisi si la conversion avait été interrompue (arrêt serveur)
        String stored = pdc.get(keyMaterial, PersistentDataType.STRING);
        Material mat = stored != null ? Material.matchMaterial(stored) : null;
        if (mat == null) {
            mat = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            pdc.set(keyMaterial, PersistentDataType.STRING, mat.name());
        }

        chunk.addPluginChunkTicket(this);
        World w = chunk.getWorld();
        jobs.add(new Job(chunk, mat, id, w.getMaxHeight(), (w.getMaxHeight() - w.getMinHeight()) * 256));

        if (announce) {
            player.sendActionBar(Component.text("Chunk [" + chunk.getX() + ", " + chunk.getZ() + "] → "
                    + mat.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        }
    }

    /** Traite les chunks petit à petit, du haut vers le bas, pour rester fluide. */
    private void tick() {
        if (!frozen.isEmpty()) {
            int now = getServer().getCurrentTick();
            frozen.values().removeIf(until -> until <= now);
        }

        int checks = checksPerTick;
        int changes = changesPerTick;

        while (checks > 0 && changes > 0) {
            Job j = jobs.peek();
            if (j == null) return;

            while (j.idx < j.total && checks > 0 && changes > 0) {
                int i = j.idx++;
                checks--;
                int x = i & 15;
                int z = (i >> 4) & 15;
                int y = j.maxY - 1 - (i >> 8);

                Block b = j.chunk.getBlock(x, y, z);
                Material t = b.getType();
                if (t != j.mat && t.isSolid() && !isSpecial(t)) {
                    b.setType(j.mat, false);
                    changes--;
                }
            }

            if (j.idx >= j.total) {
                jobs.poll();
                j.chunk.getPersistentDataContainer().set(keyDone, PersistentDataType.BYTE, (byte) 1);
                j.chunk.removePluginChunkTicket(this);
                frozen.put(j.id, getServer().getCurrentTick() + freezeTicks);
                active.remove(j.id);
            }
        }
    }

    private static final class Job {
        final Chunk chunk;
        final Material mat;
        final String id;
        final int maxY;
        final int total;
        int idx = 0;

        Job(Chunk chunk, Material mat, String id, int maxY, int total) {
            this.chunk = chunk;
            this.mat = mat;
            this.id = id;
            this.maxY = maxY;
            this.total = total;
        }
    }
}
