package com.example.battleship.game;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Ship {
  private final int id;
  private final int length;
  private final List<Coord> cells;
  private final Set<Coord> hits = new HashSet<>();

  public Ship(int id, List<Coord> cells) {
    this.id = id;
    this.cells = List.copyOf(cells);
    this.length = this.cells.size();
  }

  public int id() {
    return id;
  }

  public int length() {
    return length;
  }

  public List<Coord> cells() {
    return Collections.unmodifiableList(cells);
  }

  public boolean isSunk() {
    return hits.size() >= length;
  }

  public void registerHit(Coord c) {
    hits.add(c);
  }
}

