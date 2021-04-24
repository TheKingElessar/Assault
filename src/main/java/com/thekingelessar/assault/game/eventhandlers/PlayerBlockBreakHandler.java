package com.thekingelessar.assault.game.eventhandlers;

import com.thekingelessar.assault.game.GameInstance;
import com.thekingelessar.assault.game.player.PlayerMode;
import com.thekingelessar.assault.util.Coordinate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class PlayerBlockBreakHandler implements Listener
{
    @EventHandler
    public void onBlockBreak(BlockBreakEvent blockBreakEvent)
    {
        Player player = blockBreakEvent.getPlayer();
        
        PlayerMode playerMode = PlayerMode.getPlayerMode(player);
        
        if (playerMode != null)
        {
            if (!playerMode.canBreakBlocks)
            {
                blockBreakEvent.setCancelled(true);
            }
        }
        
        GameInstance gameInstance = GameInstance.getPlayerGameInstance(player);
        
        if (gameInstance != null)
        {
            Coordinate placedCoord = new Coordinate(blockBreakEvent.getBlock().getLocation());
            
            if (gameInstance.placedBlocks.contains(placedCoord))
            {
                gameInstance.placedBlocks.remove(placedCoord);
            }
            else if (!gameInstance.gameMap.breakableBlocks.contains(blockBreakEvent.getBlock().getType()))
            {
                blockBreakEvent.setCancelled(true);
            }
            
//            for (Material block : gameInstance.gameMap.breakableBlocks)
//            {
//                if (block.equals(blockBreakEvent.getBlock().getType()))
//                {
//                    blockBreakEvent.setCancelled(false);
//                }
//            }
            
        }
    }
}