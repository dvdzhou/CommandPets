package com.daluxea.commandpets;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
    
    // Dati salvati
    private Map<UUID, Set<UUID>> friendList = new HashMap<>();
    private Map<UUID, Location> homeLocations = new HashMap<>();
    private Map<UUID, Boolean> attackMode = new HashMap<>(); 
    
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        loadData();
        
        if (getCommand("pets") != null) {
            getCommand("pets").setExecutor(this);
            getCommand("pets").setTabCompleter(this);
        }
        
        getLogger().info("CommandPets v1.0 abilitato!");
        startGuardTask();
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("CommandPets disabilitato!");
    }

    // --- LOGICA PROTEZIONE (Guard Mode) ---
    private void startGuardTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Controlla se il player ha la modalità attacco attiva (default: true)
                    if (attackMode.getOrDefault(player.getUniqueId(), true)) {
                        checkPlayerWolves(player);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void checkPlayerWolves(Player owner) {
        for (Entity entity : owner.getWorld().getEntities()) {
            if (entity instanceof Wolf) {
                Wolf wolf = (Wolf) entity;
                // Attacca solo se: addomesticato, del player, NON seduto
                if (wolf.isTamed() && wolf.getOwner() != null && wolf.getOwner().equals(owner) && !wolf.isSitting()) {
                    scanForThreats(wolf, owner);
                }
            }
        }
    }

    private void scanForThreats(Wolf wolf, Player owner) {
        if (wolf.getTarget() != null && !wolf.getTarget().isDead()) return;

        List<Entity> nearbyEntities = owner.getNearbyEntities(PROTECTION_RADIUS, PROTECTION_RADIUS, PROTECTION_RADIUS);

        for (Entity target : nearbyEntities) {
            // 1. Mostri
            if (target instanceof Monster) {
                if (target != wolf && target != owner) {
                    wolf.setTarget((LivingEntity) target);
                    return;
                }
            }
            // 2. Player Ostili (Armati)
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
        // Supporto per tutte le armi principali
        return typeName.endsWith("_SWORD") || typeName.endsWith("_AXE") || 
               typeName.equals("TRIDENT") || typeName.equals("MACE");
    }

    // --- GESTIONE MOVIMENTI E STATO (Call, Sit, Home) ---

    private void managePets(Player player, Location dest, String action, String typeFilter) {
        int count = 0;
        String typeMsg = "animali";
        boolean sitDown = action.equals("sit") || action.equals("gohome");
        boolean teleport = action.equals("call") || action.equals("gohome");

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Tameable) {
                    Tameable pet = (Tameable) entity;
                    
                    // Verifica proprietà
                    if (!pet.isTamed() || pet.getOwner() == null || !pet.getOwner().equals(player)) continue;

                    boolean isWolf = pet instanceof Wolf;
                    boolean isCat = pet instanceof Cat;
                    boolean shouldAct = false;

                    // Filtri Specie
                    if (typeFilter.equals("all")) {
                        if (isWolf || isCat) shouldAct = true;
                    } else if (typeFilter.equals("dogs") && isWolf) {
                        shouldAct = true;
                        typeMsg = "cani";
                    } else if (typeFilter.equals("cats") && isCat) {
                        shouldAct = true;
                        typeMsg = "gatti";
                    }

                    if (shouldAct) {
                        // Teletrasporto
                        if (teleport && dest != null) pet.teleport(dest);
                        
                        // Cambio stato (Seduto/In Piedi)
                        if (entity instanceof Sittable) {
                            Sittable sittablePet = (Sittable) entity;
                            if (action.equals("sit") || action.equals("gohome")) {
                                sittablePet.setSitting(true);
                            } else if (action.equals("call") || action.equals("stand")) {
                                sittablePet.setSitting(false);
                            }
                        }

                        // Reset Target per i Lupi se richiamati (per farli smettere di combattere)
                        if ((action.equals("call") || action.equals("stand")) && isWolf) {
                            ((Wolf) pet).setTarget(null); 
                        }
                        
                        count++;
                    }
                }
            }
        }

        // Feedback al giocatore
        if (count > 0) {
            String verb = teleport ? "Teletrasportati" : (sitDown ? "Seduti" : "Alzati");
            player.sendMessage(ChatColor.GREEN + verb + " " + count + " " + typeMsg + ".");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Nessun " + typeMsg + " trovato nei paraggi.");
        }
    }

    // --- GESTIONE DATI (IO) ---

    private boolean isFriend(UUID ownerId, UUID friendId) {
        return friendList.containsKey(ownerId) && friendList.get(ownerId).contains(friendId);
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        friendList.clear();
        homeLocations.clear();
        attackMode.clear();

        // Caricamento Liste
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
                try {
                    homeLocations.put(UUID.fromString(key), dataConfig.getLocation("homes." + key));
                } catch (Exception e) {}
            }
        }
        if (dataConfig.contains("attack_mode")) {
            for (String key : dataConfig.getConfigurationSection("attack_mode").getKeys(false)) {
                try {
                    attackMode.put(UUID.fromString(key), dataConfig.getBoolean("attack_mode." + key));
                } catch (Exception e) {}
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
            dataConfig.set("attack_mode." + entry.getKey().toString(), entry.getValue());
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // --- COMANDI E TAB COMPLETE ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Solo i player possono usare CommandPets.");
            return true;
        }
        Player player = (Player) sender;
        UUID ownerId = player.getUniqueId();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        String mainArg = args[0].toLowerCase();

        // 1. SETHOME
        if (mainArg.equals("sethome")) {
            homeLocations.put(ownerId, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Cuccia impostata in questa posizione!");
            saveData();
            return true;
        }

        // 2. ATTACK ON/OFF
        if (mainArg.equals("attack")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usa: /pets attack <on|off>");
                return true;
            }
            boolean mode = args[1].equalsIgnoreCase("on");
            attackMode.put(ownerId, mode);
            player.sendMessage(ChatColor.YELLOW + "Modalità guardia: " + (mode ? ChatColor.GREEN + "ATTIVA" : ChatColor.RED + "DISATTIVA"));
            saveData();
            return true;
        }

        // 3. FRIENDS
        if (mainArg.equals("friends")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usa: /pets friends <add|remove|list> [nome]");
                return true;
            }
            String sub = args[1].toLowerCase();
            
            if (sub.equals("list")) {
                if (!friendList.containsKey(ownerId) || friendList.get(ownerId).isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "La tua lista amici è vuota.");
                } else {
                    player.sendMessage(ChatColor.GOLD + "Amici fidati: " + ChatColor.GREEN + 
                        friendList.get(ownerId).stream().map(id -> Bukkit.getOfflinePlayer(id).getName()).collect(Collectors.joining(", ")));
                }
                return true;
            }

            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Specifica il nome del giocatore.");
                return true;
            }
            
            String targetName = args[2];
            org.bukkit.OfflinePlayer target = Bukkit.getPlayer(targetName);
            if (target == null) target = Bukkit.getOfflinePlayer(targetName);
            if (target.getUniqueId() == null) { player.sendMessage(ChatColor.RED + "Giocatore mai visto."); return true; }
            
            if (sub.equals("add")) {
                if (target.getUniqueId().equals(ownerId)) { player.sendMessage(ChatColor.RED + "Non puoi aggiungere te stesso."); return true; }
                friendList.computeIfAbsent(ownerId, k -> new HashSet<>()).add(target.getUniqueId());
                player.sendMessage(ChatColor.GREEN + targetName + " aggiunto agli amici.");
                saveData();
            } else if (sub.equals("remove")) {
                if (friendList.containsKey(ownerId) && friendList.get(ownerId).remove(target.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + targetName + " rimosso dagli amici.");
                    saveData();
                } else {
                    player.sendMessage(ChatColor.RED + targetName + " non era in lista.");
                }
            }
            return true;
        }

        // 4. MOVIMENTO (Call, Gohome, Sit, Stand)
        if (mainArg.equals("call") || mainArg.equals("gohome") || mainArg.equals("sit") || mainArg.equals("stand")) {
            String type = "all";
            if (args.length > 1) {
                if (args[1].equalsIgnoreCase("dogs")) type = "dogs";
                else if (args[1].equalsIgnoreCase("cats")) type = "cats";
            }
            
            Location dest = player.getLocation(); // Default
            if (mainArg.equals("gohome")) {
                if (!homeLocations.containsKey(ownerId)) {
                    player.sendMessage(ChatColor.RED + "Nessuna cuccia impostata! Usa prima /pets sethome");
                    return true;
                }
                dest = homeLocations.get(ownerId);
            }
            
            managePets(player, dest, mainArg, type);
            return true;
        }

        sendHelp(player);
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.GOLD + "--- CommandPets Help ---");
        p.sendMessage(ChatColor.YELLOW + "/pets call [dogs|cats]" + ChatColor.WHITE + " - Chiama a raccolta.");
        p.sendMessage(ChatColor.YELLOW + "/pets gohome [dogs|cats]" + ChatColor.WHITE + " - Spedisce alla cuccia.");
        p.sendMessage(ChatColor.YELLOW + "/pets sit [dogs|cats]" + ChatColor.WHITE + " - Fa sedere gli animali vicini.");
        p.sendMessage(ChatColor.YELLOW + "/pets stand [dogs|cats]" + ChatColor.WHITE + " - Fa alzare gli animali vicini.");
        p.sendMessage(ChatColor.YELLOW + "/pets attack <on|off>" + ChatColor.WHITE + " - I cani attaccano chi ha armi?");
        p.sendMessage(ChatColor.YELLOW + "/pets sethome" + ChatColor.WHITE + " - Imposta qui la cuccia.");
        p.sendMessage(ChatColor.YELLOW + "/pets friends <add|remove|list>" + ChatColor.WHITE + " - Gestisci amici.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        List<String> result = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> cmds = Arrays.asList("call", "gohome", "sit", "stand", "friends", "attack", "sethome", "help");
            for (String s : cmds) if (s.startsWith(args[0].toLowerCase())) result.add(s);
        } else if (args.length == 2) {
            String prev = args[0].toLowerCase();
            if (prev.equals("call") || prev.equals("gohome") || prev.equals("sit") || prev.equals("stand")) {
                List<String> types = Arrays.asList("dogs", "cats", "all");
                for (String s : types) if (s.startsWith(args[1].toLowerCase())) result.add(s);
            } else if (prev.equals("friends")) {
                List<String> sub = Arrays.asList("add", "remove", "list");
                for (String s : sub) if (s.startsWith(args[1].toLowerCase())) result.add(s);
            } else if (prev.equals("attack")) {
                List<String> sub = Arrays.asList("on", "off");
                for (String s : sub) if (s.startsWith(args[1].toLowerCase())) result.add(s);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("friends")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("add")) {
                for (Player p : Bukkit.getOnlinePlayers()) result.add(p.getName());
            } else if (sub.equals("remove")) {
                UUID oid = ((Player)sender).getUniqueId();
                if (friendList.containsKey(oid)) {
                    for(UUID uid : friendList.get(oid)) {
                        String n = Bukkit.getOfflinePlayer(uid).getName();
                        if (n!=null) result.add(n);
                    }
                }
            }
        }
        return result;
    }
}