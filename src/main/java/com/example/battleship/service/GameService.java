package com.example.battleship.service;

import com.example.battleship.game.Board;
import com.example.battleship.game.Coord;
import com.example.battleship.game.FireResult;
import com.example.battleship.game.FleetRules;
import com.example.battleship.game.Orientation;
import com.example.battleship.game.Phase;
import com.example.battleship.game.ai.RandomPlacer;
import com.example.battleship.service.dto.FireRequest;
import com.example.battleship.service.dto.FireResponse;
import com.example.battleship.service.dto.GameStateResponse;
import com.example.battleship.service.dto.PlaceShipRequest;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public final class GameService {
  private static final String SESSION_KEY = "BATTLESHIP_GAME";
  private final SecureRandom rnd = new SecureRandom();
  private final RandomPlacer placer = new RandomPlacer();

  public GameStateResponse newGame(HttpSession session) {
    GameSession gs = new GameSession();
    randomizeComputerFleet(gs);
    session.setAttribute(SESSION_KEY, gs);
    return toState(gs);
  }

  public GameStateResponse state(HttpSession session) {
    return toState(getOrCreate(session));
  }

  public GameStateResponse placePlayerShip(HttpSession session, PlaceShipRequest req) {
    GameSession gs = getOrCreate(session);
    if (gs.phase != Phase.PLACING) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not in PLACING phase.");
    }
    if (!FleetRules.isAllowedLength(req.length)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ship length.");
    }
    if (!gs.playerFleet.canPlaceLength(req.length)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No more ships of this length allowed.");
    }

    boolean ok = gs.playerBoard.placeShip(req.x, req.y, req.length, req.orientation);
    if (!ok) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot place ship there (touching/overlap/out of bounds).");
    }
    gs.playerFleet.onPlaced(req.length);
    return toState(gs);
  }

  public GameStateResponse autoPlaceRemainingForPlayer(HttpSession session) {
    GameSession gs = getOrCreate(session);
    if (gs.phase != Phase.PLACING) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not in PLACING phase.");
    }

    for (var e : FleetRules.requiredCountsByLength().entrySet()) {
      int length = e.getKey();
      while (gs.playerFleet.canPlaceLength(length)) {
        boolean placed = tryPlaceRandomOnExistingBoard(gs.playerBoard, length, 20000);
        if (!placed) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auto-place failed; try a different manual placement.");
        }
        gs.playerFleet.onPlaced(length);
      }
    }

    if (gs.playerFleet.isComplete()) {
      gs.phase = Phase.PLAY;
    }
    return toState(gs);
  }

  public GameStateResponse startPlay(HttpSession session) {
    GameSession gs = getOrCreate(session);
    if (gs.phase != Phase.PLACING) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not in PLACING phase.");
    }
    if (!gs.playerFleet.isComplete()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fleet not complete.");
    }
    gs.phase = Phase.PLAY;
    return toState(gs);
  }

  public FireResponse playerFires(HttpSession session, FireRequest req) {
    GameSession gs = getOrCreate(session);
    if (gs.phase != Phase.PLAY) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not in PLAY phase.");
    }
    if (gs.winner != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game finished.");
    }

    FireResult playerShot = gs.computerBoard.fire(req.x, req.y);
    if (playerShot.outcome() == FireResult.Outcome.INVALID || playerShot.outcome() == FireResult.Outcome.ALREADY_SHOT) {
      return new FireResponse(playerShot, List.of(), toState(gs));
    }

    if (playerShot.allShipsSunk()) {
      gs.winner = "PLAYER";
      gs.phase = Phase.FINISHED;
      return new FireResponse(playerShot, List.of(), toState(gs));
    }

    // Player hit - gets another turn, computer doesn't shoot
    if (playerShot.outcome() == FireResult.Outcome.HIT) {
      return new FireResponse(playerShot, List.of(), toState(gs));
    }

    // Player missed - computer gets to shoot, and continues shooting if it hits
    List<FireResponse.ComputerShot> computerShots = new ArrayList<>();
    while (true) {
      Coord cShot = pickComputerShot(gs);
      FireResult computerShot = gs.playerBoard.fire(cShot.x(), cShot.y());
      updateComputerTargeting(gs, cShot, computerShot);
      computerShots.add(new FireResponse.ComputerShot(cShot.x(), cShot.y(), computerShot));

      if (computerShot.allShipsSunk()) {
        gs.winner = "COMPUTER";
        gs.phase = Phase.FINISHED;
        return new FireResponse(playerShot, computerShots, toState(gs));
      }

      // Computer missed - end turn
      if (computerShot.outcome() == FireResult.Outcome.MISS) {
        return new FireResponse(playerShot, computerShots, toState(gs));
      }

      // Computer hit - continue shooting (loop to get another turn)
    }
  }

  // ---------------- internals ----------------

  private GameSession getOrCreate(HttpSession session) {
    Object o = session.getAttribute(SESSION_KEY);
    if (o instanceof GameSession gs) return gs;

    GameSession gs = new GameSession();
    randomizeComputerFleet(gs);
    session.setAttribute(SESSION_KEY, gs);
    return gs;
  }

  private void randomizeComputerFleet(GameSession gs) {
    for (int attempt = 0; attempt < 200; attempt++) {
      Board b = new Board(true);
      var tracker = new com.example.battleship.game.FleetTracker();
      boolean ok = true;

      for (var e : FleetRules.requiredCountsByLength().entrySet()) {
        int length = e.getKey();
        int count = e.getValue();
        for (int i = 0; i < count; i++) {
          if (!placer.tryPlaceOne(b, length, 8000)) {
            ok = false;
            break;
          }
          tracker.onPlaced(length);
        }
        if (!ok) break;
      }

      if (ok && tracker.isComplete()) {
        gs.computerBoard = b;
        gs.computerFleet = tracker;
        return;
      }
    }
    throw new IllegalStateException("Could not generate computer fleet.");
  }

  private boolean tryPlaceRandomOnExistingBoard(Board board, int length, int maxAttempts) {
    Orientation o = (rnd.nextBoolean() ? Orientation.H : Orientation.V);
    for (int i = 0; i < maxAttempts; i++) {
      int x = rnd.nextInt(Board.SIZE);
      int y = rnd.nextInt(Board.SIZE);
      if (board.placeShip(x, y, length, o)) return true;
      o = (rnd.nextBoolean() ? Orientation.H : Orientation.V);
    }
    return false;
  }

  private Coord pickComputerShot(GameSession gs) {
    Deque<Coord> q = gs.computerTargetQueue;
    while (!q.isEmpty()) {
      Coord c = q.removeFirst();
      if (gs.playerBoard.inBounds(c.x(), c.y()) && !gs.playerBoard.wasShot(c.x(), c.y())) {
        return c;
      }
    }

    // Improved AI: Target cells in checkerboard pattern to maximize coverage
    for (int attempt = 0; attempt < 500; attempt++) {
      int x = rnd.nextInt(Board.SIZE);
      int y = rnd.nextInt(Board.SIZE);
      // Prefer cells that form a checkerboard pattern (reduces misses)
      if (!gs.playerBoard.wasShot(x, y) && (x + y) % 2 == 0) return new Coord(x, y);
    }

    // Fallback to random if checkerboard pattern is full
    for (int attempt = 0; attempt < 500; attempt++) {
      int x = rnd.nextInt(Board.SIZE);
      int y = rnd.nextInt(Board.SIZE);
      if (!gs.playerBoard.wasShot(x, y)) return new Coord(x, y);
    }

    // Final fallback: scan the board
    for (int y = 0; y < Board.SIZE; y++) {
      for (int x = 0; x < Board.SIZE; x++) {
        if (!gs.playerBoard.wasShot(x, y)) return new Coord(x, y);
      }
    }
    return new Coord(0, 0); // should never happen
  }

  private void updateComputerTargeting(GameSession gs, Coord shot, FireResult res) {
    if (res.outcome() != FireResult.Outcome.HIT) return;
    if (res.shipSunk()) {
      gs.computerTargetQueue.clear();
      return;
    }

    Deque<Coord> q = gs.computerTargetQueue;
    // Add adjacent cells in a specific order to improve targeting
    q.addLast(new Coord(shot.x() + 1, shot.y()));
    q.addLast(new Coord(shot.x() - 1, shot.y()));
    q.addLast(new Coord(shot.x(), shot.y() + 1));
    q.addLast(new Coord(shot.x(), shot.y() - 1));
  }

  private GameStateResponse toState(GameSession gs) {
    String[][] player = toStrings(gs.playerBoard.viewForOwner());
    String[][] computer = toStrings(gs.phase == Phase.FINISHED ? gs.computerBoard.viewForOwner() : gs.computerBoard.viewForOpponent());
    String[][] computerFull = toStrings(gs.computerBoard.viewForOwner()); // Always include full computer board

    return new GameStateResponse(
        gs.phase,
        gs.winner,
        player,
        computer,
        computerFull,
        FleetRules.requiredCountsByLength(),
        gs.playerFleet.placedCounts(),
        gs.playerFleet.isComplete()
    );
  }

  private static String[][] toStrings(Board.CellView[][] view) {
    String[][] out = new String[Board.SIZE][Board.SIZE];
    for (int y = 0; y < Board.SIZE; y++) {
      for (int x = 0; x < Board.SIZE; x++) {
        out[y][x] = view[y][x].name();
      }
    }
    return out;
  }
}

