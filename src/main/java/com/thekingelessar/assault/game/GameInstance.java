package com.thekingelessar.assault.game;

import com.thekingelessar.assault.Assault;
import com.thekingelessar.assault.game.map.Map;
import com.thekingelessar.assault.game.player.GamePlayer;
import com.thekingelessar.assault.game.team.GameTeam;
import com.thekingelessar.assault.game.team.TeamColor;
import com.thekingelessar.assault.game.team.TeamStage;
import com.thekingelessar.assault.game.timertasks.TaskGameStartDelay;
import com.thekingelessar.assault.game.timertasks.TaskGiveCoins;
import com.thekingelessar.assault.game.world.WorldManager;
import com.thekingelessar.assault.util.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
    
    public HashMap<UUID, PlayerMode> playerModes = new HashMap<>();
    
    public TaskGameStartDelay taskGameStartDelay;
    
    public TaskGiveCoins taskGiveCoins;
    
    
    public GameInstance(String mapName, List<Player> players, List<Player> spectators)
    {
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
        
        Assault.INSTANCE.getLogger().info(Arrays.toString(this.gameWorld.getGameRules()));
        
        
        Assault.INSTANCE.getLogger().info("Opened new game world: " + gameWorld.getName());
    }
    
    public void sendPlayersToWorld()
    {
        for (Player player : players)
        {
            player.teleport(gameMap.waitingSpawn.toLocation(this.gameWorld));
            UUID playerUUID = player.getUniqueId();
            playerModes.put(playerUUID, PlayerMode.setPlayerMode(playerUUID, PlayerMode.LOBBY));
        }
        
        if (spectators != null)
        {
            for (Player player : spectators)
            {
                player.teleport(gameMap.waitingSpawn.toLocation(this.gameWorld));
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        
        taskGameStartDelay = new TaskGameStartDelay(200, 20, 20, this);
        taskGameStartDelay.runTaskTimer(Assault.INSTANCE, taskGameStartDelay.startDelay, taskGameStartDelay.tickDelay);
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
    
    public void startGame()
    {
        createTeams();
        
        this.gameStage = GameStage.BUILDING_BASE;
        
        for (java.util.Map.Entry<TeamColor, GameTeam> team : teams.entrySet())
        {
            
            for (Player player : team.getValue().getPlayers())
            {
                team.getValue().getGamePlayer(player).playerBank.coins += 100;
                
                try
                {
                    GameTeam gameTeam = getPlayerTeam(player);
                    player.teleport(gameTeam.mapBase.defenderSpawn.toLocation(this.gameWorld, 180f, 0f));
                    // todo: add facing rotation for spawns so you can customize them
                    
                    Title title = new Title(ChatColor.WHITE + "You are on the " + gameTeam.color.getFormattedName(false) + ChatColor.WHITE + " team!", "Begin building your defenses!");
                    title.clearTitle(player);
                    
                    title.send(player);
                    
                    PlayerMode.setPlayerMode(player.getUniqueId(), PlayerMode.PLAYER);
                    
                }
                catch (Exception exception)
                {
                    // Player not valid
                }
            }
            
            team.getValue().createBuildingShop();
        }
        
        this.updateScoreboards();
        gameMap.clearWaitingPlatform(gameWorld);
        
        this.restoreHealth();
    }
    
    public void startAttackMode()
    {
        taskGiveCoins = new TaskGiveCoins(0, 100, this, 8);
        taskGiveCoins.runTaskTimer(Assault.INSTANCE, taskGiveCoins.startDelay, taskGiveCoins.tickDelay);
        
        this.gameStage = GameStage.ROUNDS;
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
