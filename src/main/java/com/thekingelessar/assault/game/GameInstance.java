package com.thekingelessar.assault.game;

import com.thekingelessar.assault.Assault;
import com.thekingelessar.assault.game.map.Map;
import com.thekingelessar.assault.game.player.GamePlayer;
import com.thekingelessar.assault.game.player.PlayerMode;
import com.thekingelessar.assault.game.team.GameTeam;
import com.thekingelessar.assault.game.team.TeamColor;
import com.thekingelessar.assault.game.team.TeamStage;
import com.thekingelessar.assault.game.timertasks.*;
import com.thekingelessar.assault.game.world.WorldManager;
import com.thekingelessar.assault.util.Coordinate;
import com.thekingelessar.assault.util.Title;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.*;

public class GameInstance
{
    public UUID gameUUID = UUID.randomUUID();
    public String gameID;
    
    public GameStage gameStage;
    
    public Map gameMap;
    public World gameWorld;
    
    public HashMap<TeamColor, GameTeam> teams = new HashMap<>();
    public Scoreboard teamScoreboard;
    
    private final List<Player> players;
    private final List<Player> spectators;
    
    public HashMap<Player, PlayerMode> playerModes = new HashMap<>();
    
    public TaskTickTimer taskTickTimer;
    public TaskCountdownGameStart taskCountdownGameStart;
    public TaskCountdownBuilding taskCountdownBuilding;
    public TaskAttackTimer taskAttackTimer;
    public TaskCountdownSwapAttackers taskCountdownSwapAttackers;
    public TaskCountdownGameEnd taskCountdownGameEnd;
    
    public TeamColor attackingTeam;
    
    public int buildingSecondsLeft = 180;
    public int teamsGone = 0;
    
    public TaskGiveCoins taskGiveCoins;
    
    public List<Coordinate> placedBlocks = new ArrayList<>();
    
    public List<Item> guidingObjectives = new ArrayList<>();
    
    public GameInstance(String mapName, List<Player> players, List<Player> spectators)
    {
        if (!mapName.startsWith("map_"))
        {
            mapName = "map_" + mapName;
        }
        
        this.gameMap = Assault.maps.get(mapName);
        this.gameID = mapName + "_" + gameUUID.toString();
        
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        teamScoreboard = manager.getNewScoreboard();
        
        this.players = players;
        this.spectators = spectators;
    }
    
    public void startWorld()
    {
        this.gameWorld = WorldManager.createWorldFromMap(this.gameMap.mapName, false, this.gameID);
        
        this.gameWorld.setGameRuleValue("DO_DAYLIGHT_CYCLE", "false");
        this.gameWorld.setGameRuleValue("DO_MOB_SPAWNING", "false");
        
        Assault.INSTANCE.getLogger().info("Opened new game world: " + gameWorld.getName());
    }
    
    public void sendPlayersToWorld()
    {
        for (Player player : players)
        {
            player.teleport(gameMap.waitingSpawn.toLocation(this.gameWorld));
            PlayerMode.setPlayerMode(player, PlayerMode.LOBBY, this);
        }
        
        if (spectators != null)
        {
            for (Player player : spectators)
            {
                player.teleport(gameMap.waitingSpawn.toLocation(this.gameWorld));
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        
        taskCountdownGameStart = new TaskCountdownGameStart(200, 20, 20, this);
        taskCountdownGameStart.runTaskTimer(Assault.INSTANCE, taskCountdownGameStart.startDelay, taskCountdownGameStart.tickDelay);
    }
    
    public void createTeams()
    {
        GameTeam redTeam = new GameTeam(TeamColor.RED, this);
        redTeam.setTeamMapBase();
        redTeam.teamStage = TeamStage.DEFENDING;
        
        GameTeam blueTeam = new GameTeam(TeamColor.BLUE, this);
        blueTeam.setTeamMapBase();
        blueTeam.teamStage = TeamStage.DEFENDING;
        
        teams.put(TeamColor.RED, redTeam);
        teams.put(TeamColor.BLUE, blueTeam);
        
        Collections.shuffle(this.players);
        
        int numberOfTeams = 2;
        int firstMember = 0;
        List<List<Player>> teamLists = new ArrayList<>();
        for (int i = 0; i < numberOfTeams; i++)
        {
            List<Player> sublist = players.subList(firstMember, (players.size() / numberOfTeams) * (i + 1));
            firstMember = (players.size() / numberOfTeams) * (i + 1);
            teamLists.add(sublist);
        }
        
        if (this.players.size() == 1)
        {
            teamLists.add(players);
        }
        
        for (java.util.Map.Entry<TeamColor, GameTeam> entry : teams.entrySet())
        {
            entry.getValue().addMembers(teamLists.remove(0));
        }
    }
    
    public GameTeam getPlayerTeam(Player player)
    {
        for (java.util.Map.Entry<TeamColor, GameTeam> team : teams.entrySet())
        {
            
            for (Player teamPlayer : team.getValue().getPlayers())
            {
                if (teamPlayer.equals(player))
                {
                    return team.getValue();
                }
            }
            
        }
        
        return null;
    }
    
    public void startBuildMode()
    {
        createTeams();
        
        this.gameStage = GameStage.BUILDING_BASE;
        
        for (GameTeam team : teams.values())
        {
            
            Location objectiveLocation = team.mapBase.objective.toLocation(this.gameWorld);
            objectiveLocation.add(0, 0.5, 0);
            ItemStack objectiveItem = new ItemStack(Material.NETHER_STAR);
            
            this.guidingObjectives.add(this.gameWorld.dropItem(objectiveLocation, objectiveItem));
            
            for (Player player : team.getPlayers())
            {
                GamePlayer gamePlayer = team.getGamePlayer(player);
                gamePlayer.playerBank.coins += 100;
                
                try
                {
                    GameTeam gameTeam = getPlayerTeam(player);
                    gamePlayer.respawn(PlayerMode.BUILDING);
                    
                    Title title = new Title(ChatColor.WHITE + "You are on the " + gameTeam.color.getFormattedName(false, false, ChatColor.BOLD) + ChatColor.WHITE + " team!", "Begin building your defenses!");
                    title.clearTitle(player);
                    
                    title.send(player);
                    
                    PlayerMode.setPlayerMode(player, PlayerMode.BUILDING, this);
                    
                }
                catch (Exception exception)
                {
                    // Player not valid
                }
            }
            
            team.createBuildingShop();
        }
        
        this.updateScoreboards();
        gameMap.clearWaitingPlatform(gameWorld);
        
        this.restoreHealth();
        
        taskCountdownBuilding = new TaskCountdownBuilding(3600, 20, 20, this);
        taskCountdownBuilding.runTaskTimer(Assault.INSTANCE, taskCountdownBuilding.startDelay, taskCountdownBuilding.tickDelay);
    }
    
    public void startAttackMode()
    {
        for (Item item : this.guidingObjectives)
        {
            item.remove();
        }
        
        taskTickTimer = new TaskTickTimer(0, 1, this);
        taskTickTimer.runTaskTimer(Assault.INSTANCE, taskTickTimer.startDelay, taskTickTimer.tickDelay);
        
        this.gameStage = GameStage.ATTACK;
        
        List<TeamColor> randomList = new ArrayList<>(teams.keySet());
        Collections.shuffle(randomList);
        
        this.attackingTeam = randomList.get(0);
        this.teams.get(this.attackingTeam).teamStage = TeamStage.ATTACKING;
        this.teams.get(randomList.get(1)).teamStage = TeamStage.DEFENDING;
        
        for (GameTeam gameTeam : teams.values())
        {
            gameTeam.displaySeconds = 0;
        }
        
        startRound();
    }
    
    public void swapAttackingTeams()
    {
        teamsGone++;
        
        for (GameTeam gameTeam : teams.values())
        {
            gameTeam.buffList.clear();
        }
        
        TeamColor oldDefenders = this.getDefendingTeam().color;
        this.teams.get(this.attackingTeam).teamStage = TeamStage.DEFENDING;
        this.attackingTeam = oldDefenders;
        this.getDefendingTeam().teamStage = TeamStage.ATTACKING;
        
        startRound();
    }
    
    public void startRound()
    {
        taskGiveCoins = new TaskGiveCoins(0, 100, this, 8);
        taskGiveCoins.runTaskTimer(Assault.INSTANCE, taskGiveCoins.startDelay, taskGiveCoins.tickDelay);
        
        if (taskAttackTimer == null)
        {
            System.out.println("task attack is NULL");
        }
        
        taskAttackTimer = new TaskAttackTimer(0, 20, 20, this);
        taskAttackTimer.runTaskTimer(Assault.INSTANCE, taskAttackTimer.startDelay, taskAttackTimer.tickDelay);
        
        for (GameTeam gameTeam : teams.values())
        {
            switch (gameTeam.teamStage)
            {
                case DEFENDING:
                    for (Player player : gameTeam.getPlayers())
                    {
                        Title title = new Title(gameTeam.color.getFormattedName(true, true, ChatColor.BOLD) + ChatColor.RESET + " is DEFENDING!", "Defend the " + ChatColor.LIGHT_PURPLE + "nether star" + ChatColor.RESET + " with your life!");
                        title.clearTitle(player);
                        title.send(player);
                    }
                    break;
                case ATTACKING:
                    for (Player player : gameTeam.getPlayers())
                    {
                        Title title = new Title(gameTeam.color.getFormattedName(true, true, ChatColor.BOLD) + ChatColor.RESET + " is ATTACKING!", "Get to the " + ChatColor.LIGHT_PURPLE + "nether star" + ChatColor.RESET + " as fast as you can!");
                        title.clearTitle(player);
                        title.send(player);
                    }
                    break;
            }
            
            gameTeam.createAttackShop();
        }
        
        this.restoreHealth();
        
        this.getAttackingTeam().startAttackingTime = System.nanoTime();
        
        for (GameTeam gameTeam : teams.values())
        {
            for (Player player : gameTeam.getPlayers())
            {
                GamePlayer gamePlayer = gameTeam.getGamePlayer(player);
                gamePlayer.playerBank.coins = 0;
                gamePlayer.playerBank.gamerPoints = 0;
                
                player.getInventory().clear();
                gamePlayer.swapReset();
                
                PlayerMode mode = PlayerMode.setPlayerMode(player, PlayerMode.ATTACKING, this);
                gameTeam.getGamePlayer(player).respawn(mode);
            }
        }
        
        Location objectiveLocation = this.getDefendingTeam().mapBase.objective.toLocation(this.gameWorld);
        objectiveLocation.add(0, 0.5, 0);
        ItemStack objectiveItem = new ItemStack(Material.NETHER_STAR);
        
        this.gameWorld.dropItem(objectiveLocation, objectiveItem);
    }
    
    public void endGame()
    {
        WorldManager.closeWorld(this.gameWorld);
        List<GameInstance> gameInstances = Assault.gameInstances;
        for (int i = 0; i < gameInstances.size(); i++)
        {
            GameInstance gameInstance = gameInstances.get(i);
            if (gameInstance.equals(this))
            {
                GameInstance removed = Assault.gameInstances.remove(i);
            }
        }
    }
    
    public List<Player> getPlayers()
    {
        List<Player> players = new ArrayList<>();
        for (java.util.Map.Entry<TeamColor, GameTeam> entry : this.teams.entrySet())
        {
            players.addAll(entry.getValue().getPlayers());
        }
        
        return players;
    }
    
    public void restoreHealth()
    {
        for (Player player : this.getPlayers())
        {
            player.setHealth(player.getMaxHealth());
        }
    }
    
    public void doBuffs()
    {
        for (GameTeam gameTeam : this.teams.values())
        {
            gameTeam.doBuffs();
        }
    }
    
    public void updateScoreboards()
    {
        for (GameTeam gameTeam : this.teams.values())
        {
            for (GamePlayer player : gameTeam.members)
            {
                player.updateScoreboard();
            }
        }
    }
    
    public GameTeam getWinningTeam()
    {
        double lowestTime = Integer.MAX_VALUE;
        GameTeam lowestTeam = null;
        
        for (GameTeam gameTeam : this.teams.values())
        {
            if (gameTeam.finalAttackingTime < lowestTime)
            {
                lowestTeam = gameTeam;
                lowestTime = gameTeam.finalAttackingTime;
            }
        }
        
        return null;
    }
    
    public GameTeam getAttackingTeam()
    {
        return teams.get(this.attackingTeam);
    }
    
    public GameTeam getDefendingTeam()
    {
        for (GameTeam gameTeam : this.teams.values())
        {
            if (!gameTeam.color.equals(this.attackingTeam))
            {
                return gameTeam;
            }
        }
        return null;
    }
    
    public static GameInstance getPlayerGameInstance(Player player)
    {
        for (GameInstance gameInstance : Assault.gameInstances)
        {
            if (gameInstance.getPlayerTeam(player) != null)
            {
                return gameInstance;
            }
        }
        return null;
    }
    
}
