package com.thekingelessar.assault.game.timertasks;

import com.thekingelessar.assault.game.GameInstance;
import com.thekingelessar.assault.util.Title;
import org.bukkit.scheduler.BukkitRunnable;

public class TaskAttackTimer extends BukkitRunnable
{
    public int startTicks;
    
    public int startDelay;
    
    public int tickDelay;
    
    public GameInstance gameInstance;
    
    private Title title = new Title();
    
    public TaskAttackTimer(int startTicks, int startDelay, int tickDelay, GameInstance gameInstance)
    {
        this.startTicks = startTicks;
        
        this.startDelay = startDelay;
        this.tickDelay = tickDelay;
        this.gameInstance = gameInstance;
    }
    
    @Override
    public void run()
    {
        advanceTimer();
    }
    
    public void advanceTimer()
    {
        double attackingTime = gameInstance.getAttackingTeam().displaySeconds;
        attackingTime = attackingTime + 1.;
        
        gameInstance.teams.get(gameInstance.attackingTeam).displaySeconds = attackingTime;
        
        gameInstance.updateScoreboards();
    }
    
    public void stopTimer() {
        this.gameInstance.taskAttackTimer = null;
        
        long nanosecondsTaken = this.gameInstance.getAttackingTeam().startAttackingTime - System.nanoTime();
        this.gameInstance.getAttackingTeam().finalAttackingTime = nanosecondsTaken / 1000000000.;
        this.gameInstance.getAttackingTeam().displaySeconds = (Math.round(gameInstance.getAttackingTeam().finalAttackingTime * 100d) / 100d);
    
        this.cancel();
    }
    
}