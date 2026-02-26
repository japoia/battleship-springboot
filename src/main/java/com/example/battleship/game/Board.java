package com.example.battleship.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Board {
  public static final int SIZE = 10;
  private final boolean isComputerBoard; // Flag to indicate if this is computer's board

  private final int[][] shipAt = new int[SIZE][SIZE]; // -1 none, otherwise shipId
  private final int[][] shotTurn = new int[SIZE][SIZE]; // -1 if not shot, otherwise turn number
  private final Map<Integer, Ship> ships = new HashMap<>();
  private int nextShipId = 1;

  // Default constructor for player board
  public Board() {
    this(false);
  }

  // Constructor with flag for computer board
  public Board(boolean isComputerBoard) {
    this.isComputerBoard = isComputerBoard;
    for (int y = 0; y < SIZE; y++) {
      Arrays.fill(shipAt[y], -1);
      Arrays.fill(shotTurn[y], -1);
    }
  }

  public boolean wasShot(int x, int y) {
    if (!inBounds(x, y)) return false;
    return shotTurn[y][x] != -1;
  }

  public int shotTurn(int x, int y) {
    if (!inBounds(x, y)) return -1;
    return shotTurn[y][x];
  }

  public Optional<Ship> shipAt(int x, int y) {
    if (!inBounds(x, y)) return Optional.empty();
    int id = shipAt[y][x];
    if (id < 0) return Optional.empty();
    return Optional.ofNullable(ships.get(id));
  }

  public int shipCount() {
    return ships.size();
  }

  public List<Ship> ships() {
    return List.copyOf(ships.values());
  }

  public boolean inBounds(int x, int y) {
    return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
  }

  public List<Coord> footprintFor(int x, int y, int length, Orientation o) {
    if (length <= 0) return List.of();
    List<Coord> cells = new ArrayList<>(length);
    for (int i = 0; i < length; i++) {
      int cx = (o == Orientation.H) ? x + i : x;
      int cy = (o == Orientation.V) ? y + i : y;
      cells.add(new Coord(cx, cy));
    }
    return cells;
  }

  public boolean canPlaceShip(int x, int y, int length, Orientation o) {
    List<Coord> cells = footprintFor(x, y, length, o);
    if (cells.isEmpty()) return false;

    for (Coord c : cells) {
      if (!inBounds(c.x(), c.y())) return false;
      if (shipAt[c.y()][c.x()] != -1) return false;

      // no touching: check 8-neighborhood for existing ships
      for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
          int nx = c.x() + dx;
          int ny = c.y() + dy;
          if (!inBounds(nx, ny)) continue;
          if (shipAt[ny][nx] != -1) return false;
        }
      }
    }

    return true;
  }

  public boolean placeShip(int x, int y, int length, Orientation o) {
    if (!canPlaceShip(x, y, length, o)) return false;
    List<Coord> cells = footprintFor(x, y, length, o);
    int id = nextShipId++;

    for (Coord c : cells) {
      shipAt[c.y()][c.x()] = id;
    }

    ships.put(id, new Ship(id, cells));
    return true;
  }

  public FireResult fire(int x, int y, int turnNumber) {
    if (!inBounds(x, y)) {
      return new FireResult(FireResult.Outcome.INVALID, false, false);
    }
    if (shotTurn[y][x] != -1) {
      return new FireResult(FireResult.Outcome.ALREADY_SHOT, false, false);
    }

    shotTurn[y][x] = turnNumber;
    int id = shipAt[y][x];
    if (id == -1) {
      return new FireResult(FireResult.Outcome.MISS, false, false);
    }

    // Cheating logic: If hit and this is computer board, try to move the ship to a new position and return MISS
    if (isComputerBoard) {
      Ship hitShip = ships.get(id);
      int shipLength = hitShip.cells().size();
      
      // Remove the hit ship from the board
      removeShip(id);
      
      // Try to place the ship in a new random position
      boolean shipMoved = tryPlaceShipRandomly(shipLength);
      
      if (shipMoved) {
        // Ship successfully moved, return MISS
        return new FireResult(FireResult.Outcome.MISS, false, false);
      } else {
        // Failed to move ship, place it back and proceed with normal hit logic
        placeShipBack(hitShip);
        hitShip.registerHit(new Coord(x, y));
        boolean sunk = hitShip.isSunk();
        boolean allSunk = sunk && ships.values().stream().allMatch(Ship::isSunk);
        return new FireResult(FireResult.Outcome.HIT, sunk, allSunk);
      }
    } else {
      // Player board: normal hit logic
      Ship hitShip = ships.get(id);
      hitShip.registerHit(new Coord(x, y));
      boolean sunk = hitShip.isSunk();
      boolean allSunk = sunk && ships.values().stream().allMatch(Ship::isSunk);
      return new FireResult(FireResult.Outcome.HIT, sunk, allSunk);
    }
  }

  // Helper method to remove a ship from the board
  private void removeShip(int shipId) {
    Ship ship = ships.get(shipId);
    if (ship != null) {
      for (Coord cell : ship.cells()) {
        shipAt[cell.y()][cell.x()] = -1;
      }
      ships.remove(shipId);
    }
  }

  // Helper method to try placing a ship in the first available position that hasn't been shot
  private boolean tryPlaceShipRandomly(int length) {
    // Check all possible positions and orientations systematically
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        // Try horizontal orientation first
        if (canPlaceShip(x, y, length, Orientation.H) && !containsShotCells(x, y, length, Orientation.H)) {
          placeShip(x, y, length, Orientation.H);
          return true;
        }
        // Then try vertical orientation
        if (canPlaceShip(x, y, length, Orientation.V) && !containsShotCells(x, y, length, Orientation.V)) {
          placeShip(x, y, length, Orientation.V);
          return true;
        }
      }
    }
    return false;
  }

   // Helper method to check if a ship's footprint contains any shot cells
  private boolean containsShotCells(int x, int y, int length, Orientation orientation) {
    List<Coord> cells = footprintFor(x, y, length, orientation);
    for (Coord cell : cells) {
      if (shotTurn[cell.y()][cell.x()] != -1) {
        return true;
      }
    }
    return false;
  }

  // Helper method to place a ship back to its original position
  private void placeShipBack(Ship ship) {
    int id = ship.id();
    // Ensure we use the original ship's id (not a new one)
    if (!ships.containsKey(id)) {
      ships.put(id, ship);
      for (Coord cell : ship.cells()) {
        shipAt[cell.y()][cell.x()] = id;
        if (id >= nextShipId) {
          nextShipId = id + 1;
        }
      }
    }
  }

  public CellView[][] viewForOwner() {
    CellView[][] view = new CellView[SIZE][SIZE];
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        boolean shot = shotTurn[y][x] != -1;
        int id = shipAt[y][x];
        if (id == -1) {
          view[y][x] = shot ? CellView.MISS : CellView.WATER;
        } else {
          Ship s = ships.get(id);
          if (shot) {
            view[y][x] = s.isSunk() ? CellView.SUNK : CellView.HIT;
          } else {
            view[y][x] = CellView.SHIP;
          }
        }
      }
    }
    return view;
  }

  public CellView[][] viewForOpponent() {
    CellView[][] view = new CellView[SIZE][SIZE];
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        if (shotTurn[y][x] == -1) {
          view[y][x] = CellView.UNKNOWN;
          continue;
        }
        int id = shipAt[y][x];
        if (id == -1) {
          view[y][x] = CellView.MISS;
        } else {
          Ship s = ships.get(id);
          view[y][x] = s.isSunk() ? CellView.SUNK : CellView.HIT;
        }
      }
    }
    return view;
  }

  public int[][] shotTurnMatrix() {
    int[][] copy = new int[SIZE][SIZE];
    for (int y = 0; y < SIZE; y++) {
      System.arraycopy(shotTurn[y], 0, copy[y], 0, SIZE);
    }
    return copy;
  }

  public enum CellView {
    UNKNOWN,
    WATER,
    SHIP,
    MISS,
    HIT,
    SUNK
  }
}

