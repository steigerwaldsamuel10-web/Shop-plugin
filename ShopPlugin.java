package de.yourname.shopplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShopPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Economy economy;

    private File shopFile;
    private File pricesFile;
    private FileConfiguration shopConfig;
    private FileConfiguration pricesConfig;
    private Map<String, List<ItemStack>> shopSections;
    private Map<Material, Double> itemPrices;
    private Map<Player, Integer> worthPage;
    private Set<Player> playersInSellGUI;

    @Override
    public void onEnable() {
        // Vault/Economy einrichten
        if (!setupEconomy()) {
            getLogger().severe("Vault oder ein Economy-Plugin wurde nicht gefunden. Bitte installiere Vault + z.B. EssentialsX Economy.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.shopSections = new HashMap<>();
        this.itemPrices = new HashMap<>();
        this.worthPage = new HashMap<>();
        this.playersInSellGUI = new HashSet<>();

        // Plugin registrieren
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("shop") != null) getCommand("shop").setExecutor(this);
        if (getCommand("editshop") != null) getCommand("editshop").setExecutor(this);
        if (getCommand("worth") != null) getCommand("worth").setExecutor(this);
        if (getCommand("sell") != null) getCommand("sell").setExecutor(this);

        // Dateien erstellen/laden
        createFiles();
        loadShopData();
        loadPrices();

        getLogger().info("ShopPlugin mit Vault-Support gestartet!");
    }

    @Override
    public void onDisable() {
        saveShopData();
        savePrices();
        getLogger().info("ShopPlugin wurde gestoppt!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void createFiles() {
        // Shop Datei
        shopFile = new File(getDataFolder(), "shop.yml");
        if (!shopFile.exists()) {
            shopFile.getParentFile().mkdirs();
            try {
                shopFile.createNewFile();
                createDefaultShopConfig();
            } catch (IOException e) {
                getLogger().severe("Fehler beim Erstellen der shop.yml: " + e.getMessage());
            }
        }
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);

        // Preise Datei
        pricesFile = new File(getDataFolder(), "prices.yml");
        if (!pricesFile.exists()) {
            try {
                pricesFile.createNewFile();
                createDefaultPrices();
            } catch (IOException e) {
                getLogger().severe("Fehler beim Erstellen der prices.yml: " + e.getMessage());
            }
        }
        pricesConfig = YamlConfiguration.loadConfiguration(pricesFile);
    }

    private void createDefaultShopConfig() {
        try {
            // Standard PVP-Sektion erstellen
            shopConfig.set("sections.PVP.display_name", "§c§lPVP");
            shopConfig.set("sections.PVP.display_material", "TOTEM_OF_UNDYING");
            shopConfig.set("sections.PVP.lore", Arrays.asList(
                    "§7PVP Items und Ausrüstung",
                    "§7für den Kampf gegen andere Spieler",
                    "§8Klicke zum Öffnen"
            ));
            shopConfig.set("sections.PVP.items", new ArrayList<>());

            shopConfig.save(shopFile);
            getLogger().info("Standard Shop-Konfiguration erstellt!");
        } catch (IOException e) {
            getLogger().severe("Fehler beim Erstellen der Standard-Shop-Config: " + e.getMessage());
        }
    }

    private void createDefaultPrices() {
        // Beispiel-Preise
        pricesConfig.set("prices.DIAMOND", 100.0);
        pricesConfig.set("prices.EMERALD", 50.0);
        pricesConfig.set("prices.GOLD_INGOT", 25.0);
        pricesConfig.set("prices.IRON_INGOT", 10.0);
        pricesConfig.set("prices.COAL", 5.0);
        pricesConfig.set("prices.COBBLESTONE", 1.0);
        pricesConfig.set("prices.DIRT", 0.5);
        pricesConfig.set("prices.STONE", 2.0);
        pricesConfig.set("prices.OAK_LOG", 3.0);
        pricesConfig.set("prices.WHEAT", 2.0);
        pricesConfig.set("prices.COPPER_INGOT", 8.0);
        pricesConfig.set("prices.NETHERITE_INGOT", 500.0);
        pricesConfig.set("prices.ANCIENT_DEBRIS", 200.0);
        pricesConfig.set("prices.AMETHYST_SHARD", 15.0);
        pricesConfig.set("prices.RAW_IRON", 8.0);
        pricesConfig.set("prices.RAW_GOLD", 20.0);
        pricesConfig.set("prices.RAW_COPPER", 6.0);

        try {
            pricesConfig.save(pricesFile);
        } catch (IOException e) {
            getLogger().severe("Fehler beim Speichern der Standard-Preise: " + e.getMessage());
        }
    }

    private void loadShopData() {
        shopSections.clear();

        if (shopConfig.getConfigurationSection("sections") == null) {
            return;
        }

        for (String section : shopConfig.getConfigurationSection("sections").getKeys(false)) {
            List<ItemStack> items = new ArrayList<>();

            // Lade Items aus der Konfiguration
            if (shopConfig.isList("sections." + section + ".items")) {
                List<Map<?, ?>> itemList = shopConfig.getMapList("sections." + section + ".items");
                for (Map<?, ?> itemMap : itemList) {
                    try {
                        @SuppressWarnings("unchecked")
                        ItemStack item = ItemStack.deserialize((Map<String, Object>) itemMap);
                        items.add(item);
                    } catch (Exception e) {
                        getLogger().warning("Fehler beim Laden eines Items aus Sektion " + section + ": " + e.getMessage());
                    }
                }
            }

            shopSections.put(section, items);
        }
    }

    private void loadPrices() {
        itemPrices.clear();
        if (pricesConfig.getConfigurationSection("prices") != null) {
            for (String materialName : pricesConfig.getConfigurationSection("prices").getKeys(false)) {
                try {
                    Material material = Material.valueOf(materialName);
                    double price = pricesConfig.getDouble("prices." + materialName);
                    itemPrices.put(material, price);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Unbekanntes Material in prices.yml: " + materialName);
                }
            }
        }
    }

    private void saveShopData() {
        try {
            // Speichere alle Sektionen mit ihren Items
            for (Map.Entry<String, List<ItemStack>> entry : shopSections.entrySet()) {
                String sectionName = entry.getKey();
                List<ItemStack> items = entry.getValue();

                // Items serialisieren
                List<Map<String, Object>> itemList = new ArrayList<>();
                for (ItemStack item : items) {
                    itemList.add(item.serialize());
                }

                // Nur Items updaten, Display-Eigenschaften beibehalten
                shopConfig.set("sections." + sectionName + ".items", itemList);
            }

            shopConfig.save(shopFile);
        } catch (IOException e) {
            getLogger().severe("Fehler beim Speichern der Shop-Daten: " + e.getMessage());
        }
    }

    private void savePrices() {
        try {
            pricesConfig.save(pricesFile);
        } catch (IOException e) {
            getLogger().severe("Fehler beim Speichern der Preise: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("shop")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cDieser Befehl kann nur von Spielern ausgeführt werden!");
                return true;
            }
            openShopGUI((Player) sender);
            return true;
        }

        if (command.getName().equalsIgnoreCase("worth")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cDieser Befehl kann nur von Spielern ausgeführt werden!");
                return true;
            }
            openWorthGUI((Player) sender, 0);
            return true;
        }

        if (command.getName().equalsIgnoreCase("sell")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cDieser Befehl kann nur von Spielern ausgeführt werden!");
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                openSellGUI(player);
                return true;
            }

            if (args[0].equalsIgnoreCase("hand")) {
                sellHandItem(player);
            } else if (args[0].equalsIgnoreCase("all")) {
                sellAllItems(player);
            } else {
                try {
                    Material material = Material.valueOf(args[0].toUpperCase());
                    int amount = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                    sellSpecificItem(player, material, amount);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cUnbekanntes Material oder ungültige Anzahl!");
                }
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("editshop")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cDieser Befehl kann nur von Spielern ausgeführt werden!");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("shopplugin.edit")) {
                player.sendMessage("§cDu hast keine Berechtigung für diesen Befehl!");
                return true;
            }

            if (args.length == 0) {
                player.sendMessage("§cVerwendung: /editshop <addsection|addhanditem>");
                return true;
            }

            if (args[0].equalsIgnoreCase("addsection")) {
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /editshop addsection <Name>");
                    return true;
                }

                String sectionName = args[1];
                if (shopSections.containsKey(sectionName)) {
                    player.sendMessage("§cDiese Sektion existiert bereits!");
                    return true;
                }

                // Erstelle neue Sektion mit Standard-Werten
                shopSections.put(sectionName, new ArrayList<>());
                shopConfig.set("sections." + sectionName + ".display_name", "§a" + sectionName);
                shopConfig.set("sections." + sectionName + ".display_material", "CHEST");
                shopConfig.set("sections." + sectionName + ".lore", Arrays.asList(
                        "§7Klicke hier, um die Kategorie zu öffnen"
                ));
                shopConfig.set("sections." + sectionName + ".items", new ArrayList<>());

                saveShopData();
                player.sendMessage("§aSektion '" + sectionName + "' wurde erfolgreich erstellt!");
                player.sendMessage("§7Du kannst die Anzeige in der shop.yml bearbeiten!");
                return true;
            }

            if (args[0].equalsIgnoreCase("addhanditem")) {
                if (args.length < 2) {
                    player.sendMessage("§cVerwendung: /editshop addhanditem <Sektion>");
                    return true;
                }

                String sectionName = args[1];
                if (!shopSections.containsKey(sectionName)) {
                    player.sendMessage("§cDiese Sektion existiert nicht!");
                    return true;
                }

                ItemStack handItem = player.getInventory().getItemInMainHand();
                if (handItem == null || handItem.getType() == Material.AIR || handItem.getType().isAir()) {
                    player.sendMessage("§cDu musst ein Item in der Hand haben!");
                    return true;
                }

                shopSections.get(sectionName).add(handItem.clone());
                saveShopData();
                player.sendMessage("§aItem wurde zur Sektion '" + sectionName + "' hinzugefügt!");
                return true;
            }
        }

        return false;
    }

    private void openShopGUI(Player player) {
        if (shopSections.isEmpty()) {
            player.sendMessage("§cDer Shop ist leer! Ein Admin muss erst Sektionen hinzufügen.");
            return;
        }

        Inventory shopGUI = Bukkit.createInventory(null, 27, "§6§lShop - Kategorien");

        int slot = 0;
        for (String sectionName : shopSections.keySet()) {
            if (slot >= 27) break;

            // Lade Display-Eigenschaften aus der Konfiguration
            String displayName = shopConfig.getString("sections." + sectionName + ".display_name", "§a" + sectionName);
            String materialName = shopConfig.getString("sections." + sectionName + ".display_material", "CHEST");
            List<String> lore = shopConfig.getStringList("sections." + sectionName + ".lore");

            // Fallback für Standard-Lore
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "§7Klicke hier, um die Kategorie zu öffnen",
                        "§7Items: §e" + shopSections.get(sectionName).size()
                );
            } else {
                // Items-Anzahl zur Lore hinzufügen
                lore = new ArrayList<>(lore);
                lore.add("§7Items: §e" + shopSections.get(sectionName).size());
            }

            // Material parsen
            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.CHEST;
                getLogger().warning("Unbekanntes Material für Sektion " + sectionName + ": " + materialName);
            }

            ItemStack sectionItem = new ItemStack(material);
            ItemMeta meta = sectionItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                sectionItem.setItemMeta(meta);
            }

            shopGUI.setItem(slot, sectionItem);
            slot++;
        }

        player.openInventory(shopGUI);
    }

    private void openWorthGUI(Player player, int page) {
        List<Material> materials = new ArrayList<>(itemPrices.keySet());
        materials.sort(Comparator.comparing(Enum::name));

        int itemsPerPage = 21; // 3 Reihen à 7 Items (2 Slots für Navigation)
        int totalPages = (materials.size() + itemsPerPage - 1) / itemsPerPage;
        if (totalPages <= 0) totalPages = 1;

        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory worthGUI = Bukkit.createInventory(null, 27, "§6§lWorth - Seite " + (page + 1) + "/" + totalPages);

        // Items für diese Seite anzeigen
        for (int i = 0; i < itemsPerPage && (page * itemsPerPage + i) < materials.size(); i++) {
            Material material = materials.get(page * itemsPerPage + i);
            double price = itemPrices.get(material);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + material.name());
                meta.setLore(Arrays.asList(
                        "§7Verkaufspreis: §a" + String.format("%.2f", price) + " Coins",
                        "§7Linksklick zum Verkaufen (1x)",
                        "§7Rechtsklick zum Verkaufen (64x)"
                ));
                item.setItemMeta(meta);
            }

            worthGUI.setItem(i, item);
        }

        // Navigation
        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta meta = prevPage.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aVorherige Seite");
                prevPage.setItemMeta(meta);
            }
            worthGUI.setItem(25, prevPage);
        }

        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta meta = nextPage.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aNächste Seite");
                nextPage.setItemMeta(meta);
            }
            worthGUI.setItem(26, nextPage);
        }

        worthPage.put(player, page);
        player.openInventory(worthGUI);
    }

    private void openSellGUI(Player player) {
        Inventory sellGUI = Bukkit.createInventory(null, 45, "§c§lVerkaufen - Items hier hineinlegen");

        // Info-Item
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lVerkaufs-Info");
            meta.setLore(Arrays.asList(
                    "§7Lege die Items, die du verkaufen möchtest,",
                    "§7in dieses Inventar.",
                    "",
                    "§e§lAutomatischer Verkauf:",
                    "§7Beim Schließen der GUI werden alle",
                    "§7verkaufbaren Items automatisch verkauft.",
                    "",
                    "§7Nur Items mit festgelegten Preisen",
                    "§7können verkauft werden.",
                    "§7Nicht verkaufbare Items werden zurückgegeben."
            ));
            infoItem.setItemMeta(meta);
        }
        sellGUI.setItem(40, infoItem);

        // Verkaufen Button
        ItemStack sellButton = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta sellMeta = sellButton.getItemMeta();
        if (sellMeta != null) {
            sellMeta.setDisplayName("§a§lAlle Items verkaufen");
            sellMeta.setLore(Arrays.asList(
                    "§7Klicke hier, um alle Items",
                    "§7in diesem Inventar zu verkaufen.",
                    "§e§lHinweis: Items werden auch automatisch",
                    "§e§lverkauft, wenn du die GUI schließt!"
            ));
            sellButton.setItemMeta(sellMeta);
        }
        sellGUI.setItem(44, sellButton);

        // Zurück Button
        ItemStack backButton = new ItemStack(Material.RED_CONCRETE);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§lItems zurückgeben");
            backMeta.setLore(Arrays.asList(
                    "§7Items werden zurück in dein",
                    "§7Inventar gelegt ohne Verkauf."
            ));
            backButton.setItemMeta(backMeta);
        }
        sellGUI.setItem(36, backButton);

        playersInSellGUI.add(player);
        player.openInventory(sellGUI);
    }

    private void processSellGUI(Player player, Inventory sellInventory) {
        double totalEarned = 0;
        int totalItems = 0;
        List<ItemStack> unsellableItems = new ArrayList<>();

        // Durchlaufe alle Items im Sell-GUI (außer die Button-Slots)
        for (int i = 0; i < 36; i++) { // Erste 4 Reihen (36 Slots)
            ItemStack item = sellInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR && !item.getType().isAir()) {
                if (itemPrices.containsKey(item.getType())) {
                    double price = itemPrices.get(item.getType()) * item.getAmount();
                    totalEarned += price;
                    totalItems += item.getAmount();
                    // Item aus dem GUI entfernen (wird verkauft)
                    sellInventory.setItem(i, null);
                } else {
                    // Item kann nicht verkauft werden
                    unsellableItems.add(item);
                }
            }
        }

        // Nicht-verkaufbare Items zurück ins Inventar
        for (ItemStack item : unsellableItems) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                // Falls Inventar voll ist, Items droppen
                for (ItemStack droppedItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), droppedItem);
                }
            }
        }

        if (totalItems == 0) {
            if (unsellableItems.isEmpty()) {
                player.sendMessage("§eKeine Items zum Verkaufen gefunden.");
            } else {
                player.sendMessage("§cKeine verkaufbaren Items gefunden! Alle Items wurden zurückgegeben.");
            }
        } else {
            // Geld gutschreiben
            economy.depositPlayer(player, totalEarned);
            player.sendMessage("§a§l✓ Verkauft: §e" + totalItems + " Items §afür insgesamt §e" + String.format("%.2f", totalEarned) + " Coins§a!");
            if (!unsellableItems.isEmpty()) {
                player.sendMessage("§c" + unsellableItems.size() + " Items konnten nicht verkauft werden und wurden zurückgegeben.");
            }
        }
    }

    private void returnSellGUIItems(Player player, Inventory sellInventory) {
        List<ItemStack> itemsToReturn = new ArrayList<>();

        // Sammle alle Items aus dem Sell-GUI (außer Button-Slots)
        for (int i = 0; i < 36; i++) {
            ItemStack item = sellInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR && !item.getType().isAir()) {
                itemsToReturn.add(item.clone());
                sellInventory.setItem(i, null); // Item aus GUI entfernen
            }
        }

        // Gebe Items zurück ins Spieler-Inventar
        for (ItemStack item : itemsToReturn) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                // Falls Inventar voll ist, Items droppen
                for (ItemStack droppedItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), droppedItem);
                }
                if (itemsToReturn.size() > 0) {
                    player.sendMessage("§eEinige Items wurden gedroppt, da dein Inventar voll war!");
                }
            }
        }
    }

    private void sellHandItem(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§cDu musst ein Item in der Hand haben!");
            return;
        }

        Material material = handItem.getType();
        if (!itemPrices.containsKey(material)) {
            player.sendMessage("§cDieses Item kann nicht verkauft werden!");
            return;
        }

        int amount = handItem.getAmount();
        double totalPrice = itemPrices.get(material) * amount;

        player.getInventory().setItemInMainHand(null);

        economy.depositPlayer(player, totalPrice);
        player.sendMessage("§aVerkauft: §e" + amount + "x " + material.name() + " §afür §e" + String.format("%.2f", totalPrice) + " Coins§a!");
    }

    private void sellAllItems(Player player) {
        double totalEarned = 0;
        int totalItems = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && !item.getType().isAir() && itemPrices.containsKey(item.getType())) {
                double price = itemPrices.get(item.getType()) * item.getAmount();
                totalEarned += price;
                totalItems += item.getAmount();
                player.getInventory().remove(item);
            }
        }

        if (totalItems == 0) {
            player.sendMessage("§cKeine verkaufbaren Items im Inventar gefunden!");
        } else {
            economy.depositPlayer(player, totalEarned);
            player.sendMessage("§aVerkauft: §e" + totalItems + " Items §afür insgesamt §e" + String.format("%.2f", totalEarned) + " Coins§a!");
        }
    }

    private void sellSpecificItem(Player player, Material material, int amount) {
        if (!itemPrices.containsKey(material)) {
            player.sendMessage("§cDieses Item kann nicht verkauft werden!");
            return;
        }

        ItemStack itemToRemove = new ItemStack(material, amount);
        if (!player.getInventory().containsAtLeast(itemToRemove, amount)) {
            player.sendMessage("§cDu hast nicht genug von diesem Item!");
            return;
        }

        player.getInventory().removeItem(itemToRemove);
        double totalPrice = itemPrices.get(material) * amount;

        economy.depositPlayer(player, totalPrice);
        player.sendMessage("§aVerkauft: §e" + amount + "x " + material.name() + " §afür §e" + String.format("%.2f", totalPrice) + " Coins§a!");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.equals("§6§lShop - Kategorien")) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType().isAir()) return;

            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null || meta.getDisplayName() == null) return;

            // Finde die Sektion anhand des Display-Namens
            String targetSection = null;
            for (String sectionName : shopSections.keySet()) {
                String configDisplayName = shopConfig.getString("sections." + sectionName + ".display_name", "§a" + sectionName);
                if (configDisplayName.equals(meta.getDisplayName())) {
                    targetSection = sectionName;
                    break;
                }
            }

            if (targetSection != null) {
                openSectionGUI(player, targetSection);
            }
        }

        if (title.startsWith("§6§lWorth - Seite")) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType().isAir()) return;

            // Navigation
            if (clickedItem.getType() == Material.ARROW) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta != null && meta.getDisplayName() != null) {
                    int currentPage = worthPage.getOrDefault(player, 0);
                    if (meta.getDisplayName().contains("Vorherige")) {
                        openWorthGUI(player, currentPage - 1);
                    } else if (meta.getDisplayName().contains("Nächste")) {
                        openWorthGUI(player, currentPage + 1);
                    }
                }
                return;
            }

            // Item verkaufen
            Material material = clickedItem.getType();
            if (itemPrices.containsKey(material)) {
                int amount = event.isRightClick() ? 64 : 1;
                sellSpecificItem(player, material, amount);
            }
        }

        if (title.startsWith("§6§lShop - ") && !title.equals("§6§lShop - Kategorien")) {
            event.setCancelled(true);

            if (event.isRightClick()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getType().isAir()) return;

                player.sendMessage("§aKauf-System noch nicht implementiert!");
                player.sendMessage("§7Du würdest: §e" + clickedItem.getType().name() + " §7kaufen");
            }
        }

        if (title.equals("§c§lVerkaufen - Items hier hineinlegen")) {
            ItemStack clickedItem = event.getCurrentItem();

            // Button-Klicks verarbeiten
            if (clickedItem != null) {
                if (clickedItem.getType() == Material.GREEN_CONCRETE) {
                    event.setCancelled(true);
                    // Alle Items im Sell-GUI verkaufen
                    processSellGUI(player, event.getInventory());
                    playersInSellGUI.remove(player);
                    player.closeInventory();
                    return;
                }

                if (clickedItem.getType() == Material.RED_CONCRETE) {
                    event.setCancelled(true);
                    // Items zurückgeben und GUI schließen
                    returnSellGUIItems(player, event.getInventory());
                    playersInSellGUI.remove(player);
                    player.sendMessage("§cVerkauf abgebrochen. Alle Items wurden zurückgegeben.");
                    player.closeInventory();
                    return;
                }

                if (clickedItem.getType() == Material.BOOK) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Verhindere Interaktion mit den Button-Slots
            if (event.getSlot() >= 36) {
                event.setCancelled(true);
                return;
            }

            // Erlaube normale Item-Interaktion in den ersten 36 Slots
        }
    }

    private void openSectionGUI(Player player, String sectionName) {
        List<ItemStack> items = shopSections.get(sectionName);
        if (items == null || items.isEmpty()) {
            player.sendMessage("§cDiese Sektion ist leer!");
            return;
        }

        Inventory sectionGUI = Bukkit.createInventory(null, 27, "§6§lShop - " + sectionName);

        for (int i = 0; i < items.size() && i < 27; i++) {
            ItemStack item = items.get(i).clone();
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();
                lore.add("");
                lore.add("§7Rechtsklick zum Kaufen");

                if (itemPrices.containsKey(item.getType())) {
                    lore.add("§7Preis: §e" + String.format("%.2f", itemPrices.get(item.getType())) + " Coins");
                } else {
                    lore.add("§7Preis: §cNicht festgelegt");
                }

                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            sectionGUI.setItem(i, item);
        }

        player.openInventory(sectionGUI);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        // Wenn Sell-GUI geschlossen wird, Items automatisch verkaufen
        if (title.equals("§c§lVerkaufen - Items hier hineinlegen") && playersInSellGUI.contains(player)) {
            // Items automatisch verkaufen beim Schließen (nächster Tick)
            Bukkit.getScheduler().runTask(this, () -> {
                processSellGUI(player, event.getInventory());
                playersInSellGUI.remove(player);
            });
        }
    }
}