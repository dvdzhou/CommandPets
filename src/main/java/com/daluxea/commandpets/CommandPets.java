package com.daluxea.commandpets;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class CommandPets extends JavaPlugin implements CommandExecutor, TabCompleter {

    private final double PROTECTION_RADIUS = 10.0;
    private final double MAX_DISTANCE_FROM_OWNER = 20.0;
    private final int MAX_PETS_PER_BLOCK = 3; 
    private final int MAX_SEARCH_RANGE = 10; 
    
    // Saved Data
    private Map<UUID, Set<UUID>> friendList = new HashMap<>();
    private Map<UUID, Location> homeLocations = new HashMap<>();
    private Map<UUID, Boolean> attackMode = new HashMap<>(); 
    private Map<UUID, Boolean> silenceMode = new HashMap<>(); 
    
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        loadData();
        
        if (getCommand("pets") != null) {
            getCommand("pets").setExecutor(this);
            getCommand("pets").setTabCompleter(this);
        }
        
        getLogger().info("CommandPets v1.0 enabled!");
        startGuardTask();
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("CommandPets disabled.");
    }

    // --- GUARD LOGIC ---
    private void startGuardTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    boolean guardActive = attackMode.getOrDefault(player.getUniqueId(), false);
                    checkPlayerWolves(player, guardActive);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void checkPlayerWolves(Player owner, boolean guardActive) {
        for (Entity entity : owner.getWorld().getEntities()) {
            if (entity instanceof Wolf) {
                Wolf wolf = (Wolf) entity;
                
                if (!wolf.isTamed() || wolf.getOwner() == null || !wolf.getOwner().equals(owner)) continue;
                if (wolf.isSitting()) continue;
                if (wolf.isLoveMode()) continue;
                if (!wolf.isAdult()) continue; // Babies don't fight

                // Safety Recall
                if (wolf.getLocation().distanceSquared(owner.getLocation()) > (MAX_DISTANCE_FROM_OWNER * MAX_DISTANCE_FROM_OWNER)) {
                    wolf.teleport(owner.getLocation());
                    wolf.setTarget(null);
                    wolf.setAngry(false);
                    continue; 
                }

                if (guardActive) {
                    scanForThreats(wolf, owner);
                }
            }
        }
    }

    private void scanForThreats(Wolf wolf, Player owner) {
        if (wolf.getTarget() != null && !wolf.getTarget().isDead()) return;

        List<Entity> nearbyEntities = owner.getNearbyEntities(PROTECTION_RADIUS, PROTECTION_RADIUS, PROTECTION_RADIUS);
        Collections.shuffle(nearbyEntities);

        for (Entity target : nearbyEntities) {
            if (target instanceof Monster) {
                if (target != wolf && target != owner) {
                    wolf.setTarget((LivingEntity) target);
                    return; 
                }
            }
            if (target instanceof Player) {
                Player targetPlayer = (Player) target;
                if (targetPlayer.equals(owner)) continue;
                if (isFriend(owner.getUniqueId(), targetPlayer.getUniqueId())) continue;

                if (isHoldingWeapon(targetPlayer)) {
                    wolf.setTarget(targetPlayer);
                    return;
                }
            }
        }
    }

    private boolean isHoldingWeapon(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return false;
        String typeName = item.getType().toString();
        return typeName.endsWith("_SWORD") || typeName.endsWith("_AXE") || 
               typeName.equals("TRIDENT") || typeName.equals("MACE");
    }

    // --- SMART MOVEMENT MANAGEMENT ---

    private void managePets(Player player, Location baseDest, String action, String typeFilter, String ageFilter) {
        List<Tameable> petsToMove = new ArrayList<>();
        String typeMsg = "pets";
        
        boolean sitDown = action.equals("sit") || action.equals("gohome");
        boolean teleport = action.equals("call") || action.equals("gohome");
        boolean wantSilence = silenceMode.getOrDefault(player.getUniqueId(), true);

        // 1. Filter Pets
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Tameable) {
                    Tameable pet = (Tameable) entity;
                    if (!pet.isTamed() || pet.getOwner() == null || !pet.getOwner().equals(player)) continue;

                    // Age Filter
                    if (pet instanceof Ageable) {
                        Ageable ageable = (Ageable) pet;
                        if (ageFilter.equals("adults") && !ageable.isAdult()) continue;
                        if (ageFilter.equals("babies") && ageable.isAdult()) continue;
                    }

                    // Type Filter
                    boolean isWolf = pet instanceof Wolf;
                    boolean isCat = pet instanceof Cat;
                    boolean match = false;

                    if (typeFilter.equals("all")) {
                        if (isWolf || isCat) match = true;
                    } else if (typeFilter.equals("dogs") && isWolf) {
                        match = true; typeMsg = "dogs";
                    } else if (typeFilter.equals("cats") && isCat) {
                        match = true; typeMsg = "cats";
                    }

                    if (match) petsToMove.add(pet);
                }
            }
        }

        int totalPets = petsToMove.size();
        if (totalPets == 0) {
            String ageMsg = ageFilter.equals("all") ? "" : (" (" + ageFilter + ")");
            player.sendMessage(ChatColor.YELLOW + "No " + typeMsg + ageMsg + " found nearby.");
            return;
        }

        // 2. Safe Spots Calculation (Flood Fill)
        Queue<Location> safeSpots = new LinkedList<>();
        if (teleport && baseDest != null) {
            int spotsNeeded = (int) Math.ceil((double) totalPets / MAX_PETS_PER_BLOCK);
            List<Location> foundSpots = findWalkableSpots(baseDest, spotsNeeded);
            safeSpots.addAll(foundSpots);
            
            if (foundSpots.isEmpty()) {
                player.sendMessage(ChatColor.RED + "WARNING: No safe space found! Pets will not teleport.");
                return;
            }
        }

        // 3. Execution
        int movedCount = 0;
        
        for (Tameable pet : petsToMove) {
            if (teleport && baseDest != null) {
                Location targetLoc = safeSpots.peek();
                if (targetLoc != null) {
                    Location finalLoc = targetLoc.clone().add(0.5, 0, 0.5);
                    finalLoc.setYaw((float) (Math.random() * 360));
                    pet.teleport(finalLoc);
                    movedCount++;
                    safeSpots.add(safeSpots.poll()); // Rotate spots
                } else {
                    continue; 
                }
            } else {
                movedCount++;
            }
            
            if (pet instanceof Sittable) {
                Sittable sittablePet = (Sittable) pet;
                if (sitDown) {
                    sittablePet.setSitting(true);
                    if (wantSilence) pet.setSilent(true);
                    if (pet instanceof Wolf) {
                        ((Wolf) pet).setTarget(null);
                        ((Wolf) pet).setAngry(false); 
                    }
                } else {
                    sittablePet.setSitting(false);
                    pet.setSilent(false);
                }
            }
            
            if ((!sitDown) && pet instanceof Wolf) {
                ((Wolf) pet).setTarget(null); 
                ((Wolf) pet).setAngry(false);
            }
        }

        String verb = teleport ? "Teleported" : (sitDown ? "Sat down" : "Stood up");
        String ageSuffix = ageFilter.equals("all") ? "" : (" (" + ageFilter + ")");
        player.sendMessage(ChatColor.GREEN + verb + " " + movedCount + " " + typeMsg + ageSuffix + ".");
        
        if (teleport && movedCount < totalPets) {
            player.sendMessage(ChatColor.RED + "(Some pets stayed behind because there was no safe space).");
        }
    }

    // --- ALGORITHM: FLOOD FILL (BFS) ---
    private List<Location> findWalkableSpots(Location start, int maxSpotsNeeded) {
        List<Location> validSpots = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<Location> queue = new LinkedList<>();
        
        Location startBlock = start.getBlock().getLocation();
        queue.add(startBlock);
        visited.add(locKey(startBlock));

        int[][] dirs = {{1,0}, {-1,0}, {0,1}, {0,-1}};

        while (!queue.isEmpty() && validSpots.size() < maxSpotsNeeded * 2) {
            Location current = queue.poll();
            
            if (isSafeSpot(current)) {
                for(int i=0; i<MAX_PETS_PER_BLOCK; i++) validSpots.add(current);
                if (validSpots.size() >= maxSpotsNeeded) break;
            }

            if (current.distanceSquared(startBlock) > MAX_SEARCH_RANGE * MAX_SEARCH_RANGE) continue;

            for (int[] d : dirs) {
                Location neighbor = current.clone().add(d[0], 0, d[1]);
                Location targetNeighbor = null;
                
                if (canStepTo(current, neighbor)) targetNeighbor = neighbor;
                else if (canStepTo(current, neighbor.clone().add(0, 1, 0))) targetNeighbor = neighbor.clone().add(0, 1, 0);
                else if (canStepTo(current, neighbor.clone().add(0, -1, 0))) targetNeighbor = neighbor.clone().add(0, -1, 0);

                if (targetNeighbor != null) {
                    String key = locKey(targetNeighbor);
                    if (!visited.contains(key)) {
                        visited.add(key);
                        queue.add(targetNeighbor);
                    }
                }
            }
        }
        return validSpots;
    }

    private String locKey(Location l) { return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ(); }

    private boolean isSafeSpot(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);
        return feet.isPassable() && head.isPassable() && ground.getType().isSolid();
    }

    private boolean canStepTo(Location from, Location to) {
        return isSafeSpot(to);
    }

    private void updateSilenceState(Player player) {
        boolean wantSilence = silenceMode.getOrDefault(player.getUniqueId(), true);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Tameable && entity instanceof Sittable) {
                    Tameable pet = (Tameable) entity;
                    Sittable sittable = (Sittable) entity;
                    if (pet.isTamed() && pet.getOwner() != null && pet.getOwner().equals(player)) {
                        if (sittable.isSitting()) {
                            pet.setSilent(wantSilence);
                        } else {
                            pet.setSilent(false);
                        }
                    }
                }
            }
        }
    }

    // --- DATA IO ---
    private boolean isFriend(UUID ownerId, UUID friendId) {
        return friendList.containsKey(ownerId) && friendList.get(ownerId).contains(friendId);
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { dataFile.getParentFile().mkdirs(); dataFile.createNewFile(); } 
            catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        friendList.clear(); homeLocations.clear(); attackMode.clear(); silenceMode.clear();

        if (dataConfig.contains("friends")) {
            for (String key : dataConfig.getConfigurationSection("friends").getKeys(false)) {
                try {
                    UUID ownerId = UUID.fromString(key);
                    List<String> sList = dataConfig.getStringList("friends." + key);
                    Set<UUID> fUuids = sList.stream().map(UUID::fromString).collect(Collectors.toSet());
                    friendList.put(ownerId, fUuids);
                } catch (Exception e) {}
            }
        }
        if (dataConfig.contains("homes")) {
            for (String key : dataConfig.getConfigurationSection("homes").getKeys(false)) {
                try { homeLocations.put(UUID.fromString(key), dataConfig.getLocation("homes." + key)); } catch (Exception e) {}
            }
        }
        if (dataConfig.contains("settings.attack")) {
            for (String key : dataConfig.getConfigurationSection("settings.attack").getKeys(false)) {
                attackMode.put(UUID.fromString(key), dataConfig.getBoolean("settings.attack." + key));
            }
        }
        if (dataConfig.contains("settings.silence")) {
            for (String key : dataConfig.getConfigurationSection("settings.silence").getKeys(false)) {
                silenceMode.put(UUID.fromString(key), dataConfig.getBoolean("settings.silence." + key));
            }
        }
    }

    private void saveData() {
        if (dataFile == null || dataConfig == null) return;
        for (Map.Entry<UUID, Set<UUID>> entry : friendList.entrySet()) {
            List<String> sList = entry.getValue().stream().map(UUID::toString).collect(Collectors.toList());
            dataConfig.set("friends." + entry.getKey().toString(), sList);
        }
        for (Map.Entry<UUID, Location> entry : homeLocations.entrySet()) {
            dataConfig.set("homes." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Boolean> entry : attackMode.entrySet()) {
            dataConfig.set("settings.attack." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Boolean> entry : silenceMode.entrySet()) {
            dataConfig.set("settings.silence." + entry.getKey().toString(), entry.getValue());
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // --- COMMANDS ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("CommandPets can only be used by players."); return true; }
        Player player = (Player) sender;
        UUID ownerId = player.getUniqueId();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(player); return true; }
        String mainArg = args[0].toLowerCase();

        if (mainArg.equals("sethome")) {
            homeLocations.put(ownerId, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Home sweet home! Bedding location set right here.");
            saveData();
            return true;
        }

        if (mainArg.equals("attack") || mainArg.equals("guard")) {
            if (args.length < 2) { 
                boolean current = attackMode.getOrDefault(ownerId, true);
                player.sendMessage(ChatColor.YELLOW + "Guard Mode is currently: " + (current ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                return true; 
            }
            boolean mode = args[1].equalsIgnoreCase("on");
            attackMode.put(ownerId, mode);
            player.sendMessage(ChatColor.YELLOW + "Guard Mode set to: " + (mode ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            saveData();
            return true;
        }

        if (mainArg.equals("silence") || mainArg.equals("quiet")) {
            if (args.length < 2) { 
                boolean current = silenceMode.getOrDefault(ownerId, true);
                player.sendMessage(ChatColor.YELLOW + "Silent Mode is currently: " + (current ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                return true; 
            }
            boolean mode = args[1].equalsIgnoreCase("on");
            silenceMode.put(ownerId, mode);
            player.sendMessage(ChatColor.YELLOW + "Silent Mode set to: " + (mode ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            updateSilenceState(player);
            saveData();
            return true;
        }

        if (mainArg.equals("friends")) {
            if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /pets friends < add | remove | list > [name]"); return true; }
            String sub = args[1].toLowerCase();
            
            if (sub.equals("list")) {
                if (!friendList.containsKey(ownerId) || friendList.get(ownerId).isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "Your friend list is empty.");
                } else {
                    player.sendMessage(ChatColor.GOLD + "Trusted Friends: " + ChatColor.GREEN + 
                        friendList.get(ownerId).stream().map(id -> Bukkit.getOfflinePlayer(id).getName()).collect(Collectors.joining(", ")));
                }
                return true;
            }
            if (args.length < 3) { player.sendMessage(ChatColor.RED + "Please specify the player name."); return true; }
            String targetName = args[2];
            org.bukkit.OfflinePlayer target = Bukkit.getPlayer(targetName);
            if (target == null) target = Bukkit.getOfflinePlayer(targetName);
            if (target.getUniqueId() == null) { player.sendMessage(ChatColor.RED + "Player never seen before."); return true; }
            
            if (sub.equals("add")) {
                if (target.getUniqueId().equals(ownerId)) { player.sendMessage(ChatColor.RED + "You cannot add yourself to the friend list."); return true; }
                friendList.computeIfAbsent(ownerId, k -> new HashSet<>()).add(target.getUniqueId());
                player.sendMessage(ChatColor.GREEN + targetName + " has been added to your trusted friends.");
                saveData();
            } else if (sub.equals("remove")) {
                if (friendList.containsKey(ownerId) && friendList.get(ownerId).remove(target.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + targetName + " has been removed from friends.");
                    saveData();
                } else {
                    player.sendMessage(ChatColor.RED + targetName + " was not in your list.");
                }
            }
            return true;
        }
        
        if (mainArg.equals("count") || mainArg.equals("stats")) {
            int dogs = 0; int cats = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Tameable) {
                        Tameable pet = (Tameable) entity;
                        if (pet.isTamed() && pet.getOwner() != null && pet.getOwner().equals(player)) {
                            if (entity instanceof Wolf) dogs++;
                            else if (entity instanceof Cat) cats++;
                        }
                    }
                }
            }
            player.sendMessage(ChatColor.GOLD + "--- Pet Census (Loaded) ---");
            player.sendMessage(ChatColor.GRAY + "Dogs: " + ChatColor.GREEN + dogs);
            player.sendMessage(ChatColor.GRAY + "Cats: " + ChatColor.GREEN + cats);
            player.sendMessage(ChatColor.YELLOW + "Total: " + ChatColor.WHITE + (dogs + cats));
            return true;
        }

        // 6. MOVEMENT (CLEANED LOGIC)
        if (mainArg.equals("call") || mainArg.equals("gohome") || mainArg.equals("sit") || mainArg.equals("stand")) {
            
            String type = "all";       // Default: Tutti i tipi
            String age = "all";        // Default generico: Tutti le età
            
            // MA: Per i comandi di SPOSTAMENTO, default a ADULTI per sicurezza
            if (mainArg.equals("call") || mainArg.equals("gohome")) {
                age = "adults";
            }

            // Keyword Parsing Semplificato
            for (int i = 1; i < args.length; i++) {
                String arg = args[i].toLowerCase();
                
                if (arg.equals("dogs") || arg.equals("cats")) {
                    type = arg;
                } else if (arg.equals("babies") || arg.equals("adults")) {
                    age = arg;
                } else if (arg.equals("all")) {
                    // "all" funge da jolly per l'età, sovrascrivendo "adults"
                    // Non serve toccare il tipo, che è già "all" di default.
                    age = "all"; 
                }
            }
            
            Location dest = player.getLocation();
            if (mainArg.equals("gohome")) {
                if (!homeLocations.containsKey(ownerId)) { 
                    player.sendMessage(ChatColor.RED + "No home set! Please use /pets sethome first."); 
                    return true; 
                }
                dest = homeLocations.get(ownerId);
            }
            
            managePets(player, dest, mainArg, type, age);
            return true;
        }

        sendHelp(player);
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.GOLD + "--- CommandPets Help ---");
        p.sendMessage(ChatColor.YELLOW + "/pets < call | sit | stand | gohome > [ dogs | cats ] [ adults | babies | all ]");
        p.sendMessage(ChatColor.YELLOW + "/pets count" + ChatColor.WHITE + " - Show pet statistics.");
        p.sendMessage(ChatColor.YELLOW + "/pets attack [ on | off ]");
        p.sendMessage(ChatColor.YELLOW + "/pets silence [ on | off ]");
        p.sendMessage(ChatColor.YELLOW + "/pets friends < add | remove | list >");
        p.sendMessage(ChatColor.YELLOW + "/pets sethome");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        List<String> result = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> cmds = Arrays.asList("call", "gohome", "sit", "stand", "friends", "attack", "silence", "count", "sethome", "help");
            for (String s : cmds) if (s.startsWith(args[0].toLowerCase())) result.add(s);
        } else if (args.length >= 2) {
            String prev = args[0].toLowerCase();
            if (prev.equals("call") || prev.equals("gohome") || prev.equals("sit") || prev.equals("stand")) {
                List<String> suggestions = Arrays.asList("dogs", "cats", "all", "babies", "adults");
                for (String s : suggestions) if (s.startsWith(args[args.length-1].toLowerCase())) result.add(s);
            } else if (prev.equals("friends") && args.length == 2) {
                List<String> sub = Arrays.asList("add", "remove", "list");
                for (String s : sub) if (s.startsWith(args[1].toLowerCase())) result.add(s);
            } else if (prev.equals("attack") || prev.equals("silence")) {
                List<String> sub = Arrays.asList("on", "off");
                for (String s : sub) if (s.startsWith(args[1].toLowerCase())) result.add(s);
            }
        }
        return result;
    }
}