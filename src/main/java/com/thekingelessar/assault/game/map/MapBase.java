package com.thekingelessar.assault.game.map;

import com.thekingelessar.assault.game.team.TeamColor;
import com.thekingelessar.assault.util.Coordinate;

import java.util.List;

public class MapBase
{
    public TeamColor teamColor;
    public Coordinate defenderSpawn;
    public Coordinate attackerSpawn;
    public List<Coordinate> emeraldSpawns;
    
    public Coordinate objective;
    
    public MapBase(TeamColor teamColor, Coordinate defenderSpawn, Coordinate attackerSpawn, List<Coordinate> emeraldSpawns, Coordinate objective)
    {
        this.teamColor = teamColor;
        this.defenderSpawn = defenderSpawn;
        this.attackerSpawn = attackerSpawn;
        this.emeraldSpawns = emeraldSpawns;
        
        this.objective = objective;
    }
    
    // Emerald spawn should be located here, because you don't want
    // emeralds spawning in both bases at the same time.
}