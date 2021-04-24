package com.thekingelessar.assault.game.eventhandlers;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;

public class ItemDespawnHandler implements Listener
{
    @EventHandler
    public void onItemDespawn(ItemDespawnEvent itemDespawnEvent)
    {
        Item item = itemDespawnEvent.getEntity();
        ItemStack itemStack = item.getItemStack();
        
        if (itemStack.getType().equals(Material.NETHER_STAR))
        {
            itemDespawnEvent.setCancelled(true);
        }
    }
}