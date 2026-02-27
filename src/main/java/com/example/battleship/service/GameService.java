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

    // Check if cell was already shot before incrementing turn number
    if (gs.computerBoard.wasShot(req.x, req.y)) {
      return new FireResponse(new FireResult(FireResult.Outcome.ALREADY_SHOT, false, false), List.of(), toState(gs));
    }

    gs.playerTurnNumber++;
    FireResult playerShot = gs.computerBoard.fire(req.x, req.y, gs.playerTurnNumber);
    if (playerShot.outcome() == FireResult.Outcome.INVALID) {
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
       gs.computerTurnNumber++;
        FireResult computerShot = gs.playerBoard.fire(cShot.x(), cShot.y(), gs.computerTurnNumber);
       updateComputerTargeting(gs, cShot, computerShot);
       computerShots.add(new FireResponse.ComputerShot(cShot.x(), cShot.y(), computerShot, gs.computerTurnNumber));

      if (computerShot.allShipsSunk()) {
        gs.winner = "COMPUTER";
        gs.phase = Phase.FINISHED;
        return new FireResponse(playerShot, computerShots, toState(gs));
      }

      if (computerShot.outcome() == FireResult.Outcome.MISS) {
        return new FireResponse(playerShot, computerShots, toState(gs));
      }

      // If it's a hit, continue shooting
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
      if (gs.playerBoard.inBounds(c.x(), c.y()) && !gs.playerBoard.wasShot(c.x(), c.y()) && !gs.computerInvalidCells.contains(c)) {
        return c;
      }
    }

    // Improved AI: Target cells in checkerboard pattern to maximize coverage
    for (int attempt = 0; attempt < 500; attempt++) {
      int x = rnd.nextInt(Board.SIZE);
      int y = rnd.nextInt(Board.SIZE);
      // Prefer cells that form a checkerboard pattern (reduces misses)
      if (!gs.playerBoard.wasShot(x, y) && (x + y) % 2 == 0 && !gs.computerInvalidCells.contains(new Coord(x, y))) {
        return new Coord(x, y);
      }
    }

    // Fallback to random if checkerboard pattern is full
    for (int attempt = 0; attempt < 500; attempt++) {
      int x = rnd.nextInt(Board.SIZE);
      int y = rnd.nextInt(Board.SIZE);
      if (!gs.playerBoard.wasShot(x, y) && !gs.computerInvalidCells.contains(new Coord(x, y))) {
        return new Coord(x, y);
      }
    }

    // Final fallback: scan the board
    for (int y = 0; y < Board.SIZE; y++) {
      for (int x = 0; x < Board.SIZE; x++) {
        if (!gs.playerBoard.wasShot(x, y) && !gs.computerInvalidCells.contains(new Coord(x, y))) {
          return new Coord(x, y);
        }
      }
    }
    return new Coord(0, 0); // should never happen
  }

  private void updateComputerTargeting(GameSession gs, Coord shot, FireResult res) {
    if (res.outcome() != FireResult.Outcome.HIT) return;
    if (res.shipSunk()) {
      // Find the sunk ship and mark all surrounding cells as invalid
      var sunkShipOpt = gs.playerBoard.shipAt(shot.x(), shot.y());
      if (sunkShipOpt.isPresent()) {
        var sunkShip = sunkShipOpt.get();
        markCellsAroundShipAsInvalid(gs, sunkShip);
      }
      gs.computerTargetQueue.clear();
      return;
    }

    Deque<Coord> q = gs.computerTargetQueue;
    
    // Determine ship direction from existing hits
    Orientation shipDirection = determineShipDirection(gs, shot);
    
    if (shipDirection == Orientation.H) {
      // Ship is horizontal, target all possible left and right cells
      // First, find all existing horizontal hits
      List<Coord> existingHorizontalHits = new ArrayList<>();
      existingHorizontalHits.add(shot);
      
      // Check left of current shot
      int x = shot.x() - 1;
      while (x >= 0 && gs.playerBoard.wasShot(x, shot.y()) && 
             isHitCell(gs.playerBoard, x, shot.y())) {
        existingHorizontalHits.add(new Coord(x, shot.y()));
        x--;
      }
      
      // Check right of current shot
      x = shot.x() + 1;
      while (x < Board.SIZE && gs.playerBoard.wasShot(x, shot.y()) && 
             isHitCell(gs.playerBoard, x, shot.y())) {
        existingHorizontalHits.add(new Coord(x, shot.y()));
        x++;
      }
      
      // Find the minimum and maximum x coordinates of horizontal hits
      int minX = existingHorizontalHits.stream().mapToInt(Coord::x).min().orElse(shot.x());
      int maxX = existingHorizontalHits.stream().mapToInt(Coord::x).max().orElse(shot.x());
      
      // Add cells to the left of minX and right of maxX
      if (minX > 0) {
        q.addLast(new Coord(minX - 1, shot.y()));
      }
      if (maxX < Board.SIZE - 1) {
        q.addLast(new Coord(maxX + 1, shot.y()));
      }
      
      // Mark vertical neighbors as invalid (no need to shoot perpendicular)
      for (Coord hit : existingHorizontalHits) {
        if (hit.y() > 0) {
          gs.computerInvalidCells.add(new Coord(hit.x(), hit.y() - 1));
        }
        if (hit.y() < Board.SIZE - 1) {
          gs.computerInvalidCells.add(new Coord(hit.x(), hit.y() + 1));
        }
      }
    } else if (shipDirection == Orientation.V) {
      // Ship is vertical, target all possible up and down cells
      // First, find all existing vertical hits
      List<Coord> existingVerticalHits = new ArrayList<>();
      existingVerticalHits.add(shot);
      
      // Check above current shot
      int y = shot.y() - 1;
      while (y >= 0 && gs.playerBoard.wasShot(y, shot.x()) && 
             isHitCell(gs.playerBoard, shot.x(), y)) {
        existingVerticalHits.add(new Coord(shot.x(), y));
        y--;
      }
      
      // Check below current shot
      y = shot.y() + 1;
      while (y < Board.SIZE && gs.playerBoard.wasShot(y, shot.x()) && 
             isHitCell(gs.playerBoard, shot.x(), y)) {
        existingVerticalHits.add(new Coord(shot.x(), y));
        y++;
      }
      
      // Find the minimum and maximum y coordinates of vertical hits
      int minY = existingVerticalHits.stream().mapToInt(Coord::y).min().orElse(shot.y());
      int maxY = existingVerticalHits.stream().mapToInt(Coord::y).max().orElse(shot.y());
      
      // Add cells above minY and below maxY
      if (minY > 0) {
        q.addLast(new Coord(shot.x(), minY - 1));
      }
      if (maxY < Board.SIZE - 1) {
        q.addLast(new Coord(shot.x(), maxY + 1));
      }
      
      // Mark horizontal neighbors as invalid (no need to shoot perpendicular)
      for (Coord hit : existingVerticalHits) {
        if (hit.x() > 0) {
          gs.computerInvalidCells.add(new Coord(hit.x() - 1, hit.y()));
        }
        if (hit.x() < Board.SIZE - 1) {
          gs.computerInvalidCells.add(new Coord(hit.x() + 1, hit.y()));
        }
      }
    } else {
      // No clear direction, target all four directions
      q.addLast(new Coord(shot.x() + 1, shot.y()));
      q.addLast(new Coord(shot.x() - 1, shot.y()));
      q.addLast(new Coord(shot.x(), shot.y() + 1));
      q.addLast(new Coord(shot.x(), shot.y() - 1));
    }
  }
  
  private Orientation determineShipDirection(GameSession gs, Coord currentHit) {
    // Check if there are any adjacent hits to determine ship direction
    // Check horizontal neighbors
    boolean hasLeftHit = gs.playerBoard.wasShot(currentHit.x() - 1, currentHit.y()) && 
                        isHitCell(gs.playerBoard, currentHit.x() - 1, currentHit.y());
    boolean hasRightHit = gs.playerBoard.wasShot(currentHit.x() + 1, currentHit.y()) && 
                         isHitCell(gs.playerBoard, currentHit.x() + 1, currentHit.y());
                         
    if (hasLeftHit || hasRightHit) {
      return Orientation.H;
    }
    
    // Check vertical neighbors
    boolean hasTopHit = gs.playerBoard.wasShot(currentHit.x(), currentHit.y() - 1) && 
                       isHitCell(gs.playerBoard, currentHit.x(), currentHit.y() - 1);
    boolean hasBottomHit = gs.playerBoard.wasShot(currentHit.x(), currentHit.y() + 1) && 
                          isHitCell(gs.playerBoard, currentHit.x(), currentHit.y() + 1);
                          
    if (hasTopHit || hasBottomHit) {
      return Orientation.V;
    }
    
    // No adjacent hits, direction unknown
    return null;
  }
  
  private boolean isHitCell(Board board, int x, int y) {
    if (!board.inBounds(x, y)) {
      return false;
    }
    
    var cellView = board.viewForOwner()[y][x];
    return cellView == Board.CellView.HIT || cellView == Board.CellView.SUNK;
  }
  
  private void markCellsAroundShipAsInvalid(GameSession gs, com.example.battleship.game.Ship ship) {
    for (var cell : ship.cells()) {
      // Mark all 8 surrounding cells as invalid
      for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
          if (dx == 0 && dy == 0) continue; // Skip the cell itself
          int nx = cell.x() + dx;
          int ny = cell.y() + dy;
          if (gs.playerBoard.inBounds(nx, ny)) {
            gs.computerInvalidCells.add(new Coord(nx, ny));
          }
        }
      }
    }
  }

  private GameStateResponse toState(GameSession gs) {
    String[][] player = toStrings(gs.playerBoard.viewForOwner());
    String[][] computer = toStrings(gs.phase == Phase.FINISHED ? gs.computerBoard.viewForOwner() : gs.computerBoard.viewForOpponent());
    String[][] computerFull = toStrings(gs.computerBoard.viewForOwner()); // Always include full computer board
    int[][] playerShotTurns = gs.playerBoard.shotTurnMatrix();
    int[][] computerShotTurns = gs.computerBoard.shotTurnMatrix();

    return new GameStateResponse(
        gs.phase,
        gs.winner,
        player,
        computer,
        computerFull,
        playerShotTurns,
        computerShotTurns,
        gs.playerTurnNumber,
        gs.computerTurnNumber,
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

