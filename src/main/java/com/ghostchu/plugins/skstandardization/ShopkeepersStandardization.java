package com.ghostchu.plugins.skstandardization;

import com.nisovin.shopkeepers.api.events.ShopkeeperOpenUIEvent;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.TradingRecipe;
import com.nisovin.shopkeepers.api.ui.DefaultUITypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ShopkeepersStandardization extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        reloadConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        Metrics metrics = new Metrics(this,20812);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler(ignoreCancelled = true)
    public void onShopkeeperOpening(ShopkeeperOpenUIEvent event) {
        if (!DefaultUITypes.TRADING().equals(event.getUIType())) {
            return;
        }
        Shopkeeper shopkeeper = event.getShopkeeper();
        Player player = event.getPlayer();
        this.updateShopItems(shopkeeper, player);
    }

    private void updateShopItems(Shopkeeper shopkeeper, Player player) {
        List<ItemStack> shopItems = new ArrayList<>(); // 等待检查列表
        for (TradingRecipe recipe : shopkeeper.getTradingRecipes(player)) {
            shopItems.add(recipe.getResultItem().copy());
            shopItems.add(recipe.getItem1().copy());
            if (recipe.getItem2() != null) {
                shopItems.add(recipe.getItem2().copy());
            }
        }
        boolean anyUpdate = false;
        int i = 0;
        for (ItemStack storageContent : player.getInventory().getStorageContents()) {
            if (storageContent == null) continue;
            for (ItemStack shopItem : shopItems) {
                if (isStandardSimilar(storageContent, shopItem)) { // 标准检查下相同的话
                    if (!storageContent.isSimilar(shopItem)) { // 但非标准检查并不相同
                        // 同步玩家背包中的物品的 NBT 数据和商店的保持同步
                        anyUpdate = true;
                        storageContent.setItemMeta(shopItem.getItemMeta());
                        i++;
                    }
                }
            }
        }
        if (anyUpdate) {
            player.updateInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize(String.format(getConfig().getString("items-updated"), i)));
            getLogger().info("更新了玩家 "+player.getName()+" 的 "+i+" 个物品，以修复和 "+shopkeeper.getName()+" 的交易");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("itemstandardization")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            return false;
        }
        player.getInventory().setItemInMainHand(standardItemStack(player.getInventory().getItemInMainHand()));
        sender.sendMessage("名称和 Lores 标准化完成");
        return true;
    }

    private boolean isStandardSimilar(ItemStack stack1, ItemStack stack2) {
        stack1 = stack1.clone();
        stack2 = stack2.clone();
        stack1.setAmount(1);
        stack2.setAmount(1);
        return standardItemStack(stack1).isSimilar(standardItemStack(stack2));
    }

    private ItemStack standardItemStack(ItemStack original) {
        ItemStack stack = original.clone();
        if (!stack.hasItemMeta()) {
            return stack;
        }
        stack.setItemMeta(standardItemMeta(stack.getItemMeta()));
        return stack;
    }

    private ItemMeta standardItemMeta(ItemMeta original) {
        ItemMeta meta = original.clone();
        if (meta.hasDisplayName()) {
            meta.displayName(standardComponent(meta.displayName()));
        }
        if (meta.hasLore()) {
            List<Component> lores = meta.lore();
            List<Component> standardLores = lores.stream().map(this::standardComponent).toList();
            meta.lore(standardLores);
        }
        return meta;
    }

    public Component standardComponent(Component component) {
        return GsonComponentSerializer.gson().deserialize(GsonComponentSerializer.gson().serialize(component.compact()));
    }
}
