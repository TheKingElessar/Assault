package com.thekingelessar.assault.game.player;

import com.thekingelessar.assault.Assault;
import com.thekingelessar.assault.game.GameInstance;
import com.thekingelessar.assault.game.GameStage;
import com.thekingelessar.assault.game.inventory.Currency;
import com.thekingelessar.assault.game.team.GameTeam;
import com.thekingelessar.assault.game.team.TeamColor;
import com.thekingelessar.assault.game.team.TeamStage;
import com.thekingelessar.assault.game.timertasks.TaskCountdownRespawn;
import com.thekingelessar.assault.util.Util;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;

public class GamePlayer
{
    public Player player;
    public GameInstance gameInstance;
    public GameTeam gameTeam;
    public PlayerBank playerBank;
    public FastBoard scoreboard;
    
    public TaskCountdownRespawn taskCountdownRespawn;
    
    public List<ItemStack> spawnItems = new ArrayList<>();
    public List<ItemStack> spawnArmor = new ArrayList<>();
    
    public GamePlayer(Player player, GameInstance gameInstance, GameTeam gameTeam)
    {
        this.player = player;
        this.gameInstance = gameInstance;
        this.playerBank = new PlayerBank(0);
        this.scoreboard = new FastBoard(player);
        this.gameTeam = gameTeam;
    }
    
    public void swapReset()
    {
        spawnItems = new ArrayList<>();
        spawnArmor = new ArrayList<>();
        
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        bootsMeta.setColor(gameInstance.getPlayerTeam(player).color.color);
        boots.setItemMeta(bootsMeta);
        spawnArmor.add(boots);
        
        ItemStack leggings = new ItemStack(Material.CHAINMAIL_LEGGINGS);
        spawnArmor.add(leggings);
        
        ItemStack chestplate = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
        spawnArmor.add(chestplate);
        
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta helmetMeta = (LeatherArmorMeta) helmet.getItemMeta();
        helmetMeta.setColor(gameInstance.getPlayerTeam(player).color.color);
        helmet.setItemMeta(helmetMeta);
        spawnArmor.add(helmet);
        
        spawnItems.add(new ItemStack(Material.WOOD_SWORD));
    }
    
    public void addSpawnItem(ItemStack spawnItem)
    {
        spawnItems.add(spawnItem);
    }
    
    public void spawn(PlayerMode playerMode)
    {
        GameTeam playerTeam = gameInstance.getPlayerTeam(player);
        PlayerMode mode = PlayerMode.setPlayerMode(player, playerMode, gameInstance);
        player.teleport(gameInstance.gameMap.getSpawn(playerTeam, null).toLocation(gameInstance.gameWorld));
        player.setHealth(player.getMaxHealth());
        
        if (!playerMode.equals(PlayerMode.BUILDING))
        {
            player.getInventory().clear();
        }
        
        for (ItemStack itemStack : spawnArmor)
        {
            player.getInventory().setArmorContents(spawnArmor.toArray(new ItemStack[0]));
        }
        
        for (ItemStack itemStack : spawnItems)
        {
            if (!player.getInventory().contains(itemStack))
            {
                player.getInventory().addItem(itemStack);
            }
        }
    }
    
    public void respawn(PlayerMode playerMode, boolean delay, DeathType deathType)
    {
        if (playerMode == null)
        {
            playerMode = this.gameInstance.getPlayerMode(this.player);
        }
        
        switch (deathType)
        {
            case SWORD:
            case BOW:
                List<Material> dropItems = new ArrayList<>();
                dropItems.add(Material.EMERALD);
                
                PlayerInventory inventory = this.player.getInventory();
                for (ItemStack itemStack : inventory.getContents())
                {
                    if (itemStack != null)
                    {
                        if (dropItems.contains(itemStack.getType()))
                        {
                            Location location = this.player.getLocation();
                            location.getWorld().dropItemNaturally(location, itemStack);
                        }
                    }
                }
                break;
            
            case VOID:
                if (gameInstance.lastDamagedBy.get(player) == null)
                {
                    this.addVoidFallFeed();
                    if (playerMode.equals(PlayerMode.BUILDING))
                    {
                        this.spawn(playerMode);
                        return;
                    }
                }
                else
                {
                    Player attacker = gameInstance.lastDamagedBy.get(player);
                    this.addVoidDeathFeed(attacker);
                    
                    gameInstance.getPlayerTeam(attacker).gamerPoints += 1;
                    
                    GamePlayer attackerPlayer = gameInstance.getGamePlayer(attacker);
                    GamePlayer victimPlayer = gameInstance.getGamePlayer(player);
                    
                    attackerPlayer.playerBank.coins += (int) (0.2 * (victimPlayer.playerBank.coins));
                    attacker.playSound(attacker.getLocation(), Sound.ORB_PICKUP, 0.8F, 1.0F);
                    
                    int emeraldCount = 0;
                    for (ItemStack itemStack : player.getInventory().getContents())
                    {
                        if (itemStack != null && itemStack.getType().equals(Material.EMERALD))
                        {
                            emeraldCount += itemStack.getAmount();
                        }
                    }
                    
                    if (emeraldCount > 0)
                    {
                        attacker.getInventory().addItem(new ItemStack(Material.EMERALD, emeraldCount));
                    }
                    
                    attackerPlayer.updateScoreboard();
                    
                    gameInstance.lastDamagedBy.put(player, null);
                }
                break;
            
            case DEATH:
                this.addDeathFeed();
                break;
        }
        
        PlayerMode.setPlayerMode(player, PlayerMode.SPECTATOR, gameInstance);
        
        player.teleport(gameInstance.gameMap.waitingSpawn.toLocation(gameInstance.gameWorld));
        
        GameTeam gameTeam = gameInstance.getPlayerTeam(player);
        GamePlayer gamePlayer = gameTeam.getGamePlayer(player);
        
        if (delay)
        {
            gamePlayer.taskCountdownRespawn = new TaskCountdownRespawn(60, 0, 20, gameInstance, player);
            gamePlayer.taskCountdownRespawn.runTaskTimer(Assault.INSTANCE, gamePlayer.taskCountdownRespawn.startDelay, gamePlayer.taskCountdownRespawn.tickDelay);
        }
        else
        {
            this.spawn(playerMode);
        }
    }
    
    public void killPlayer(Player victim, boolean arrow)
    {
        if (this.gameTeam.teamStage.equals(TeamStage.ATTACKING))
        {
            gameTeam.gamerPoints += 1;
        }
        
        GamePlayer victimPlayer = gameInstance.getPlayerTeam(victim).getGamePlayer(victim);
        
        if (!arrow)
        {
            victimPlayer.addSwordDeathFeed(this.player);
        }
        
        this.playerBank.coins += (int) (0.2 * (victimPlayer.playerBank.coins));
        
        this.player.playSound(this.player.getLocation(), Sound.ORB_PICKUP, 0.8F, 1.0F);
        
        this.updateScoreboard();
    }
    
    public void addSwordDeathFeed(Player killer)
    {
        for (Player player : this.gameInstance.getPlayers())
        {
            player.sendRawMessage(killer.getDisplayName() + ChatColor.RESET + " stabbed " + this.player.getDisplayName() + ChatColor.RESET + " to death");
        }
    }
    
    public void addBowDeathFeed(Player killer)
    {
        for (Player player : this.gameInstance.getPlayers())
        {
            player.sendRawMessage(killer.getDisplayName() + ChatColor.RESET + " shot " + this.player.getDisplayName() + ChatColor.RESET + " to death");
        }
    }
    
    public void addVoidFallFeed()
    {
        for (Player player : this.gameInstance.getPlayers())
        {
            player.sendRawMessage(this.player.getDisplayName() + ChatColor.RESET + " fell into the void");
        }
    }
    
    public void addVoidDeathFeed(Player killer)
    {
        for (Player player : this.gameInstance.getPlayers())
        {
            player.sendRawMessage(killer.getDisplayName() + ChatColor.RESET + " knocked " + this.player.getDisplayName() + ChatColor.RESET + " into the void");
        }
    }
    
    public void addDeathFeed()
    {
        for (Player player : this.gameInstance.getPlayers())
        {
            player.sendRawMessage(this.player.getDisplayName() + ChatColor.RESET + " died");
        }
    }
    
    public void updateScoreboard()
    {
        scoreboard.updateTitle(ChatColor.LIGHT_PURPLE.toString() + ChatColor.BOLD + "Assault");
        
        List<String> lines = new ArrayList<>();
        
        lines.add("");
        
        lines.add("Your team: " + gameInstance.getPlayerTeam(player).color.getFormattedName(false, false, ChatColor.BOLD) + ChatColor.RESET);
        
        lines.add("");
        
        if (gameInstance.gameStage.equals(GameStage.BUILDING))
        {
            lines.add(ChatColor.WHITE.toString() + ChatColor.BOLD + "Building time: " + ChatColor.RESET + Util.secondsToMinutes(gameInstance.buildingSecondsLeft, true));
        }
        else
        {
            lines.add(ChatColor.BLUE.toString() + ChatColor.BOLD + "BLUE" + ChatColor.RESET + ": " + Util.secondsToMinutes((int) gameInstance.teams.get(TeamColor.BLUE).displaySeconds, true));
            lines.add(ChatColor.RED.toString() + ChatColor.BOLD + "RED" + ChatColor.RESET + ": " + Util.secondsToMinutes((int) gameInstance.teams.get(TeamColor.RED).displaySeconds, true));
        }
        
        lines.add("");
        
        lines.add(ChatColor.GOLD.toString() + ChatColor.BOLD + "Coins" + ChatColor.RESET + ": " + playerBank.coins);
        lines.add(ChatColor.AQUA.toString() + ChatColor.BOLD + "Team Gamer Points" + ChatColor.RESET + ": " + gameInstance.getPlayerTeam(player).gamerPoints);
        
        lines.add("");
        
        scoreboard.updateLines(lines);
    }
    
    public boolean purchaseItem(int cost, Currency currency)
    {
        switch (currency)
        {
            case COINS:
                if (cost <= playerBank.coins)
                {
                    playerBank.coins -= cost;
                    
                    if (gameInstance.gameStage.equals(GameStage.BUILDING))
                    {
                        gameInstance.buildingCoinsRemaining.put(player, playerBank.coins);
                    }
                    
                    return true;
                }
                break;
            case EMERALDS:
                ItemStack[] inventoryContents = this.player.getInventory().getContents();
                int emeraldCount = 0;
                
                for (ItemStack itemStack : inventoryContents)
                {
                    if (itemStack == null)
                    {
                        continue;
                    }
                    
                    if (itemStack.getType().equals(Material.EMERALD))
                    {
                        emeraldCount += itemStack.getAmount();
                    }
                }
                
                int spentEmeralds = 0;
                if (emeraldCount >= cost)
                {
                    for (int i = 0; i < inventoryContents.length; i++)
                    {
                        ItemStack itemStack = inventoryContents[i];
                        if (itemStack == null)
                        {
                            continue;
                        }
                        
                        int remainingCost = cost - spentEmeralds;
                        if (itemStack.getType().equals(Material.EMERALD))
                        {
                            if (itemStack.getAmount() == remainingCost)
                            {
                                itemStack.setAmount(0);
                                player.getInventory().setItem(i, itemStack);
                                return true;
                            }
                            else if (itemStack.getAmount() > remainingCost)
                            {
                                itemStack.setAmount(itemStack.getAmount() - (remainingCost));
                                player.getInventory().setItem(i, itemStack);
                                return true;
                            }
                            else if (itemStack.getAmount() < remainingCost)
                            {
                                spentEmeralds += itemStack.getAmount();
                                itemStack.setAmount(0);
                                player.getInventory().setItem(i, itemStack);
                            }
                        }
                    }
                }
                
                break;
            case GAMER_POINTS:
                GameTeam gameTeam = gameInstance.getPlayerTeam(player);
                
                if (cost <= gameTeam.gamerPoints)
                {
                    return true;
                }
        }
        
        return false;
    }
}
