package com.example.battleship.service;

import com.example.battleship.game.Board;
import com.example.battleship.game.Coord;
import com.example.battleship.game.FleetTracker;
import com.example.battleship.game.Phase;
import com.example.battleship.game.ai.ComputerStrategy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class GameSession {
  public Phase phase = Phase.PLACING;
  public String winner = null; // "PLAYER" or "COMPUTER"
  public int playerTurnNumber = 0; // Track player's turn number
  public int computerTurnNumber = 0; // Track computer's turn number

  public Board playerBoard = new Board();
  public Board computerBoard = new Board();

  public FleetTracker playerFleet = new FleetTracker();
  public FleetTracker computerFleet = new FleetTracker();

  // Enhanced AI strategy
  public Deque<Coord> computerTargetQueue = new ArrayDeque<>();
  public Set<Coord> computerInvalidCells = new HashSet<>();
  public ComputerStrategy computerStrategy;

  /**
   * Initialize computer strategy.
   */
  public void initializeStrategy() {
    this.computerStrategy = new ComputerStrategy(playerBoard, computerTargetQueue, computerInvalidCells);
  }
}

