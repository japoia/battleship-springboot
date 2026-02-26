package com.example.battleship.service;

import com.example.battleship.game.Board;
import com.example.battleship.game.Coord;
import com.example.battleship.game.FleetTracker;
import com.example.battleship.game.Phase;
import java.util.ArrayDeque;
import java.util.Deque;

public final class GameSession {
  public Phase phase = Phase.PLACING;
  public String winner = null; // "PLAYER" or "COMPUTER"
  public int playerTurnNumber = 0; // Track player's turn number
  public int computerTurnNumber = 0; // Track computer's turn number

  public Board playerBoard = new Board();
  public Board computerBoard = new Board();

  public FleetTracker playerFleet = new FleetTracker();
  public FleetTracker computerFleet = new FleetTracker();

  // Simple target-queue AI (neighbors after a hit).
  public Deque<Coord> computerTargetQueue = new ArrayDeque<>();
}

